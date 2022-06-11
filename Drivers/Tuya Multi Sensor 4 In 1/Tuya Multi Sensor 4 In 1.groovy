/**
 *  Tuya Multi Sensor 4 In 1 driver for Hubitat
 *
 *  https://community.hubitat.com/t/alpha-tuya-zigbee-multi-sensor-4-in-1/92441
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
 * ver. 1.0.1 2022-04-18 kkossev  - IAS cluster multiple TS0202, TS0210 and RH3040 Motion Sensors fingerprints; ignore repeated motion inactive events
 * ver. 1.0.2 2022-04-21 kkossev  - setMotion command; state.HashStringPars; advancedOptions: ledEnable (4in1); all DP info logs for 3in1!; _TZ3000_msl6wxk9 and other TS0202 devices inClusters correction
 * ver. 1.0.3 2022-05-05 kkossev  - '_TZE200_ztc6ggyl' 'Tuya ZigBee Breath Presence Sensor' tests; Illuminance unit changed to 'lx'
 * ver. 1.0.4 2022-05-06 kkossev  - DeleteAllStatesAndJobs; added isHumanPresenceSensorAIR(); isHumanPresenceSensorScene(); isHumanPresenceSensorFall(); convertTemperatureIfNeeded
 * ver. 1.0.5 2022-06-11 kkossev  - (dev. branch) _TZE200_3towulqd +battery; 'Reset Motion to Inactive' made explicit option; sensitivity and keepTime configuration for IAS sensors (TS0202);
 *                                W.I.P. - capability "PowerSource"
 *
*/

def version() { "1.0.5" }
def timeStamp() {"2022/06/11 9:23 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.zigbee.clusters.iaszone.ZoneStatus

metadata {
    definition (name: "Tuya Multi Sensor 4 In 1", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "Battery"
        capability "MotionSensor"
        capability "TemperatureMeasurement"        
        capability "RelativeHumidityMeasurement"
        capability "IlluminanceMeasurement"
        capability "TamperAlert"
        capability "PowerSource"    //powerSource - ENUM ["battery", "dc", "mains", "unknown"]
        capability "Refresh"

		attribute "distance", "number"        // Tuya Radar
        
        command "configure", [[name: "Configure the sensor after switching drivers"]]
        command "initialize", [[name: "Initialize the sensor after switching drivers.  \n\r   ***** Will load device default values! *****" ]]
        command "setMotion", [[name: "setMotion", type: "ENUM", constraints: ["active", "inactive"], description: "Force motion active/inactive (for tests)"]]
        command "refresh",   [[name: "May work for some DC/mains powered sensors only"]] 
        command "deleteAllStatesAndJobs",   [[name: "Delete all states and jobs before switching to another driver"]] 
        /*
        command "test", [
            [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
            [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
            [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
        ]
        */
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"TS0202", manufacturer:"_TZ3210_zmy9hjay", deviceJoinName: "Tuya Multi Sensor 4 In 1"          //
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"5j6ifxj", manufacturer:"_TYST11_i5j6ifxj", deviceJoinName: "Tuya Multi Sensor 4 In 1"       
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00", outClusters:"0019,000A", model:"hfcudw5", manufacturer:"_TYST11_7hfcudw5", deviceJoinName: "Tuya Multi Sensor 4 In 1"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_7hfcudw5", deviceJoinName: "Tuya NAS-PD07 Multi Sensor 3 In 1" // KK // https://szneo.com/en/products/show.php?id=239 // https://www.banggood.com/Tuya-Smart-Linkage-ZB-Motion-Sensor-Human-Infrared-Detector-Mobile-Phone-Remote-Monitoring-PIR-Sensor-p-1858413.html?cur_warehouse=CN 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_mrf6vtua", deviceJoinName: "Tuya Multi Sensor 3 In 1"          // not tested

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_auin8mzr", deviceJoinName: "Tuya Multi Sensor 2 In 1"          // https://zigbee.blakadder.com/Tuya_LY-TAD-K616S-ZB.html // Model LY-TAD-K616S-ZB
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_3towulqd", deviceJoinName: "Tuya 2 in 1 Zigbee Mini PIR Motion Detector + Bright Lux"          // https://www.aliexpress.com/item/1005004095233195.html
        
        // Human presence sensor AIR - o_sensitivity, v_sensitivity, led_status, vacancy_delay, light_on_luminance_prefer, light_off_luminance_prefer, mode, luminance_level, reference_luminance, vacant_confirm_time
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_auin8mzr", deviceJoinName: "Human presence sensor AIR"        // Tuya LY-TAD-K616S-ZB
        
        // Human presence sensor 'MIR-HE200-TY' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene ('default', 'area', 'toilet', 'bedroom', 'parlour', 'office', 'hotel')
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_vrfecyku", deviceJoinName: "Tuya Human presence sensor MIR-HE200-TY"
        
        // Human presence sensor 'MIR-HE200-TY_fall' - illuminance, presence, occupancy, motion_speed, motion_direction, radar_sensitivity, radar_scene, tumble_switch, fall_sensitivity, tumble_alarm_time, fall_down_status, static_dwell_alarm
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lu01t0zl", deviceJoinName: "Tuya Human presence sensor with fall function"
        
        // Smart Human presence sensor - illuminance, presence, target_distance; radar_sensitivity; minimum_range; maximum_range; detection_delay; fading_time; CLI; self_test (checking, check_success, check_failure, others, comm_fault, radar_fault)
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ztc6ggyl", deviceJoinName: "Tuya ZigBee Breath Presence Sensor ZY-M100"   // KK
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_ikvncluo", deviceJoinName: "Tuya ZigBee Breath Presence Sensor"   
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lyetpprm", deviceJoinName: "Tuya ZigBee Breath Presence Sensor"   
       
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500", outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_dl7cejts", deviceJoinName: "Tuya TS0202 Motion Sensor"  // KK model: 'ZM-RT201'// 5 seconds (!) reset period for testing
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_mmtwjmaq", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_otvn3lne", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_jytabjkb", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_ef5xlc9q", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_vwqnz1sn", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_2b8f6cio", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZE200_bq5c8xfe", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_qjqgmqxr", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_kmh5qpmb", deviceJoinName: "Tuya TS0202 Motion Sensor"    // 3in1 ?
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_zwvaj5wy", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_bsvqrxru", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_tv3wxhcz", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TYZB01_hqbdru35", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_tiwq83wk", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_ykwcwxmz", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"WHD02",  manufacturer:"_TZ3000_hktqahrq", deviceJoinName: "Tuya TS0202 Motion Sensor"

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_mcxw5ehu", deviceJoinName: "Tuya TS0202 ZM-35H-Q Motion Sensor"    // TODO: PIR sensor sensitivity and PIR keep time in seconds
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0003,0000", outClusters:"1000,0006,0019,000A", model:"TS0202", manufacturer:"_TZ3000_msl6wxk9", deviceJoinName: "Tuya TS0202 ZM-35H-Q Motion Sensor"    // TODO: fz.ZM35HQ_attr        
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500", outClusters:"1000,0006,0019,000A", model:"TS0210", manufacturer:"_TYZB01_3zv6oleo", deviceJoinName: "Tuya TS0210 Motion/Vibration Sensor"

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500,0B05", outClusters:"0019",           model:"TY0202", manufacturer:"_TZ1800_fcdjzz3s", deviceJoinName: "Lidl TY0202 Motion Sensor"
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                                    model:"RH3040", manufacturer:"TUYATEC-53o41joc", deviceJoinName: "TUYATEC RH3040 Motion Sensor"       // 60 seconds reset period        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                                    model:"RH3040", manufacturer:"TUYATEC-b5g40alm", deviceJoinName: "TUYATEC RH3040 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                                    model:"RH3040", manufacturer:"TUYATEC-deetibst", deviceJoinName: "TUYATEC RH3040 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                                    model:"RH3040", manufacturer:"TUYATEC-bd5faf9p", deviceJoinName: "Nedis/Samotech RH3040 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                                    model:"RH3040", manufacturer:"TUYATEC-zn9wyqtr", deviceJoinName: "Samotech RH3040 Motion Sensor"        // vendor: 'Samotech', model: 'SM301Z'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                                    model:"RH3040", manufacturer:"TUYATEC-b3ov3nor", deviceJoinName: "Zemismart RH3040 Motion Sensor"       // vendor: 'Nedis', model: 'ZBSM10WT'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                                    model:"RH3040", manufacturer:"TUYATEC-2gn2zf9e", deviceJoinName: "TUYATEC RH3040 Motion Sensor"
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0003",                model:"ms01",   manufacturer:"eWeLink"         // for testL 60 seconds re-triggering period!
    }
    preferences {
        if (advancedOptions == true || advancedOptions == false) { // Groovy ... :) 
            input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
            input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display sensor states in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
            if (isRadar() == false) {
                input (name: "motionReset", type: "bool", title: "Reset Motion to Inactive", description: "<i>Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b></i>", defaultValue: false)
                if (motionReset.value == true) {
    		        input ("motionResetTimer", "number", title: "After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds", description: "", range: "0..7200", defaultValue: 60)
                }
            }
            if (false) {
                input ("temperatureOffset", "number", title: "Temperature offset", description: "Select how many degrees to adjust the temperature.", range: "-100..100", defaultValue: 0.0)
                input ("humidityOffset", "number", title: "Humidity offset", description: "Enter a percentage to adjust the humidity.", range: "-50..50",  defaultValue: 0.0)
                input ("luxOffset", "number", title: "Illuminance coefficient", description: "Enter a coefficient to multiply the illuminance.", range: "0.1..2.0",  defaultValue: 1.0)
            }
        }
        input (name: "advancedOptions", type: "bool", title: "Advanced Options", description: "<i>May not work for all device types!</i>", defaultValue: false)
        if (advancedOptions == true) {
            if (is4in1()) {
                input (name: "ledEnable", type: "bool", title: "Enable LED", description: "<i>enable LED blinking when motion is detected (4in1 only)</i>", defaultValue: true)
            }
            if (is2in1() || isConfigurable() ) {
                input (name: "sensitivity", type: "enum", title: "Sensitivity", description:"Select PIR sensor sennsitivity", defaultValue: 0, options:  ["low":"low", "medium":"medium", "high":"high"])
            }
            if (is2in1()) {
                input (name: "keepTime", type: "enum", title: "Keep Time", description:"Select PIR sensor keep time (s)", defaultValue: 0, options:  ['10':'10', '30':'30', '60':'60', '120':'120'])
            }
            if (isConfigurable()) {
                input (name: "keepTime", type: "enum", title: "Keep Time", description:"Select PIR sensor keep time (s)", defaultValue: 0, options:  ['30':'30', '60':'60', '120':'120'])
            }
            if (isRadar()) {
                input (name: "ignoreDistance", type: "bool", title: "Ignore distance reports", description: "If not used, ignore the distance reports received every 1 second!", defaultValue: true)
		        input ("sensitivity", "number", title: "Radar sensitivity (1..9)", description: "", range: "0..9", defaultValue: 7)   
		        input ("detectionDelay", "number", title: "Detection delay, seconds", description: "", range: "1..120", defaultValue: 15)   
		        input ("fadingTime", "number", title: "Fading time, seconds", description: "", range: "1..300", defaultValue: 60)   
		        input ("minimumDistance", "number", title: "Minimum detection distance, meters", description: "", range: "0.1..5.0", defaultValue: 1.0)   
		        input ("maximumDistance", "number", title: "Maximum detection distance, meters", description: "", range: "1.0..7.0", defaultValue: 6.0)   
                // Minimum detection distance, meters
            }
        }
    }
}

@Field static final Integer numberOfconfigParams = 10
@Field static final Integer temperatureOffsetParamIndex = 0
@Field static final Integer humidityOffsetParamIndex = 1
@Field static final Integer luxOffsetParamIndex = 2
@Field static final Integer ledEnableParamIndex = 3
@Field static final Integer sensitivityParamIndex = 4
@Field static final Integer detectionDelayParamIndex = 5
@Field static final Integer fadingTimeParamIndex = 6
@Field static final Integer minimumDistanceParamIndex = 7
@Field static final Integer maximumDistanceParamIndex = 8
@Field static final Integer keepTimeParamIndex = 9


def is4in1() { return device.getDataValue('manufacturer') in ['_TZ3210_zmy9hjay', '_TYST11_i5j6ifxj', '_TYST11_7hfcudw5'] }
def is3in1() { return device.getDataValue('manufacturer') in ['_TZE200_7hfcudw5', '_TZE200_mrf6vtua'] }
def is2in1() { return device.getDataValue('manufacturer') in ['_TZE200_auin8mzr', '_TZE200_3towulqd'] }
def isIAS()  { return ((device.getDataValue('model') in ['TS0202']) || ('0500' in device.getDataValue('inClusters'))) }
def isTS0601() { return (device.getDataValue('model') in ['TS0601']) }
def isConfigurable() { return device.getDataValue('manufacturer') in ['_TZ3000_mcxw5ehu', '_TZ3000_msl6wxk9'] }   // TS0202 models
def isRadar() { return device.getDataValue('manufacturer') in ['_TZE200_ztc6ggyl', '_TZE200_lu01t0zl', '_TZE200_vrfecyku', '_TZE200_auin8mzr'] }
def isHumanPresenceSensorAIR()     { return device.getDataValue('manufacturer') in ['_TZE200_auin8mzr'] } 
def isHumanPresenceSensorScene()   { return device.getDataValue('manufacturer') in ['_TZE200_vrfecyku'] } 
def isHumanPresenceSensorFall()    { return device.getDataValue('manufacturer') in ['_TZE200_lu01t0zl'] } 


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
    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {	
        if (settings?.logEnable) log.debug "${device.displayName} Zone status: $description"
        parseIasMessage(description)    // TS0202 Motion sensor
    }
    else if (description?.startsWith('enroll request')) {
         /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) log.info "${device.displayName} Sending IAS enroll response..."
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        if (settings?.logEnable) log.debug "${device.displayName} enroll response: ${cmds}"
        sendZigbeeCommands( cmds )  
    }    
    else if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
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
            if (settings?.txtEnable) log.info "${device.displayName} device announcement"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0001") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya check-in (application version is ${descMap?.value})"
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
            if (descMap?.attrId == "0000") {
                if (settings?.logEnable) log.debug "${device.displayName} Zone State repot ignored value= ${Integer.parseInt(descMap?.value, 16)}"
            }
            else if (descMap?.attrId == "0002") {
                if (settings?.logEnable) log.debug "${device.displayName} Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
                handleMotion(Integer.parseInt(descMap?.value, 16))
            } else if (descMap?.attrId == "000B") {
                if (settings?.logEnable) log.debug "${device.displayName} IAS Zone ID: ${descMap.value}" 
            }
            else if (descMap?.attrId == "0013") {    // [raw:7CC50105000813002002, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:0013, encoding:20, command:0A, value:02, clusterInt:1280, attrInt:19]
                def value = Integer.parseInt(descMap?.value, 16)
                def str = getSensitivityString(value)
                if (settings?.txtEnable) log.info "${device.displayName} Current Zone Sensitivity Level = ${str} (${value})"
                device.updateSetting("sensitivity", [value:str, type:"enum"])                
            }
            else if (descMap?.attrId == "F001") {    // [raw:7CC50105000801F02000, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:F001, encoding:20, command:0A, value:00, clusterInt:1280, attrInt:61441]
                def value = Integer.parseInt(descMap?.value, 16)
                def str = getKeepTimeString(value)
                if (settings?.txtEnable) log.info "${device.displayName} Current Zone Keep-Time =  ${str} (${value})"
                //log.trace "str = ${str}"
                device.updateSetting("keepTime", [value:str, type:"enum"])                
            }
            else {
                if (settings?.logEnable) log.warn "${device.displayName} Zone status: NOT PROCESSED ${descMap}" 
            }
        } 
        else if (descMap?.clusterId == "0500" && descMap?.command == "04") {    //write attribute response
            if (settings?.logEnable) log.debug "${device.displayName} IAS enroll write attribute response is ${descMap?.data[0] == "00" ? "success" : "failure"}"
        } 
        else {
            if (settings?.logEnable) log.debug "${device.displayName} <b> NOT PARSED </b> : descMap = ${descMap}"
        }
    } // if 'catchall:' or 'read attr -'
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
            case 0x01 : // motion for 2-in-1 TS0601 (_TZE200_3towulqd) and presence state? for radars
                if (settings?.logEnable) log.debug "${device.displayName} motion event 0x01 fncmd = ${fncmd}"
                handleMotion(motionActive=fncmd)
                break
            case 0x02 :
                if (isRadar()) {    // including HumanPresenceSensorScene and isHumanPresenceSensorFall
                    if (settings?.logEnable) log.info "${device.displayName} Radar sensitivity is ${fncmd}"
                    device.updateSetting("sensitivity", [value:fncmd as int , type:"number"])
                }
                else {
                    if (settings?.logEnable) log.warn "${device.displayName} non-radar event ${dp} fncmd = ${fncmd}"
                }
                break
            case 0x03 :
                if (isRadar()) {
                    if (settings?.logEnable) log.info "${device.displayName} Radar Minimum detection distance is ${fncmd/100} m"    //
                    device.updateSetting("minimumDistance", [value:fncmd/100, type:"number"])
                }
                else {        // also battery level STATE for TS0202 ? 
                    if (settings?.logEnable) log.warn "${device.displayName} non-radar event ${dp} fncmd = ${fncmd}"
                }
                break
            case 0x04 :    // Battery level for _TZE200_3towulqd
                if (isRadar()) {
                    if (settings?.logEnable) log.info "${device.displayName} Radar Maximum detection distance is ${fncmd/100} m"
                    device.updateSetting("maximumDistance", [value:fncmd/100 , type:"number"])
                }
                else {        // also battery level for TS0202 
                    if (settings?.logEnable) log.trace "${device.displayName} Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )                    
                }
                break
            
            // case 0x05 : tamper alarm for TS0202 ?
            
            case 0x06 :
                if (isRadar()) {
                    if (settings?.logEnable) log.info "${device.displayName} Radar self checking status is ${fncmd}"
                }
                else {
                    if (settings?.logEnable) log.warn "${device.displayName} non-radar event ${dp} fncmd = ${fncmd}"
                }
                break
            case 0x09 :
                if (isRadar()) {
                    if (settings?.ignoreDistance == false) {
                        if (settings?.txtEnable) log.info "${device.displayName} Radar target distance is ${fncmd/100} m"
                        sendEvent(name : "distance", value : fncmd/100, unit : "m")
                    }
                }
                else {
                    // sensitivity for TS0202
                    def str = getSensitivityString(fncmd)
                    if (settings?.txtEnable) log.info "${device.displayName} sensitivity is ${str} (${fncmd})"
                    device.updateSetting("sensitivity", [value:str, type:"enum"])                
                }
                break
            case 0x0A : // (10) keep time for TS0202
                def str = getKeepTimeString(fncmd)
                if (settings?.txtEnable) log.info "${device.displayName} Keep Time is ${str} (${fncmd})"
                device.updateSetting("keepTime", [value:str, type:"enum"])                
                break
            case 0x0C : // (12)
                illuminanceEventLux( fncmd )    // illuminance for TS0601 2-in-1
                break
            //            
            //
            case 0x65 :    // (101)
                if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msVSensitivity is ${fncmd}s"
                    }
                    else {
                        if (settings?.logEnable) log.info "${device.displayName} Radar detection delay is ${fncmd}s"    //detectionDelay
                        device.updateSetting("detectionDelay", [value:fncmd as int , type:"number"])
                    }
                }
                else {     //  Tuya 3 in 1 (101) -> motion (ocupancy) + TUYATEC
                    if (settings?.logEnable) log.trace "{device.displayName} motion event 0x65 fncmd = ${fncmd}"
                    sendEvent(handleMotion(motionActive=fncmd))
                }
                break            
            case 0x66 :     // (102)
                if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msOSensitivity is ${fncmd}s"
                    }
                    else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {                     // trsfMotionState: (102) for TuYa Radar Sensor with fall function
                        if (settings?.logEnable) log.info "${device.displayName} motion state is ${fncmd}"
                    }
                    else {
                        if (settings?.logEnable) log.info "${device.displayName} Radar fading time is ${fncmd}s"        // 
                        device.updateSetting("fadingTime", [value:fncmd as int , type:"number"])
                    }
                }
                else if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    // // case 102 //reporting time for 4 in 1 
                    if (settings?.txtEnable) log.info "${device.displayName} reporting time is ${fncmd}"
                }
                else {     // battery level for 3 in 1;  
                    if (settings?.logEnable) log.trace "${device.displayName} Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )                    
                }
                break
            case 0x67 :     // (103)
                if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msVacancyDelay is ${fncmd}s"
                    }
                    else if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) { // trsfIlluminanceLux for TuYa Radar Sensor with fall function
                        illuminanceEventLux( fncmd )
                    }
                    else {
                        if (settings?.logEnable) log.info "${device.displayName} Radar DP_103 (CLI) is ${fncmd}"
                    }
                }
                else {        //  Tuya 3 in 1 (103) -> tamper            // TUYATEC- Battery level ????
                    def value = fncmd==0 ? 'clear' : 'detected'
                    if (settings?.txtEnable) log.info "${device.displayName} tamper alarm is ${value} (dp=67,fncmd=${fncmd})"
                	sendEvent(name : "tamper",	value : value, isStateChange : true)
                }
                break            
            case 0x68 :     // (104)
                if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msMode is ${fncmd}s"
                    }
                    else if (isHumanPresenceSensorScene()) { // detection data  for TuYa Radar Sensor with scene
                        if (settings?.logEnable) log.info "${device.displayName} radar detection data is ${fncmd}"
                    }
                    else {
                        illuminanceEventLux( fncmd )
                    }
                }
                else if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    // case 104: // 0x68 temperature calibration
                    def val = fncmd;
                    // for negative values produce complimentary hex (equivalent to negative values)
                    if (val > 4294967295) val = val - 4294967295;                    
                    if (settings?.txtEnable) log.info "${device.displayName} temperature calibration is ${val / 10.0}"
                }
                else {    //  Tuya 3 in 1 (104) -> temperature in °C
                    temperatureEvent( fncmd / 10.0 )
                }
                break            
            case 0x69 :    // 105 
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    // case 105:// 0x69 humidity calibration
                    def val = fncmd;
                    if (val > 4294967295) val = val - 4294967295;                    
                    if (settings?.txtEnable) log.info "${device.displayName} humidity calibration is ${val}"                
                }
                if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msVacantConfirmTime is ${fncmd}s"
                    }
                    else if (isHumanPresenceSensorFall()) {
                        // trsfTumbleSwitch for TuYa Radar Sensor with fall function
                        if (settings?.txtEnable) log.info "${device.displayName} Tumble Switch (dp=69) is ${fncmd}"
                    }
                }
                else {    //  Tuya 3 in 1 (105) -> humidity in %
                    humidityEvent (fncmd)
                }
                break
            case 0x6A : // 106
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    // case 106: // 0x6a lux calibration
                    def val = fncmd;
                    if (val > 4294967295) val = val - 4294967295;                    
                    if (settings?.txtEnable) log.info "${device.displayName} lux calibration is ${val}"                
                }
                if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msReferenceLuminance is ${fncmd}s"
                    }
                    else if (isHumanPresenceSensorFall()) {
                        // trsfTumbleAlarmTime
                        if (settings?.txtEnable) log.info "${device.displayName} Tumble Alarm Time (dp=6A) is ${fncmd}"
                    }
                }
                else {    //  Tuya 3 in 1 temperature scale Celsius/Fahrenheit
                    if (settings?.logEnable) log.info "${device.displayName} Temperature Scale is: ${fncmd == 0 ? 'Celsius' : 'Fahrenheit'} (DP=0x6A fncmd = ${fncmd})"  
                }
                break
            case 0x6B : // 107
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    //  Tuya 4 in 1 (107) -> temperature in °C
                    temperatureEvent( fncmd / 10.0 )
                }
                else if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msLightOnLuminancePrefer is ${fncmd}s"
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} light on luminance (dp=6B) is ${fncmd}"
                    }
                }
                else { // 3in1
                    if (settings?.logEnable) log.info "${device.displayName} Min Temp is: ${fncmd} (DP=0x6B)"  
                }
                break            
            case 0x6C : //  108 Tuya 4 in 1 -> humidity in %
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {
                    humidityEvent (fncmd)
                }
                else if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msLightOffLuminancePrefer is ${fncmd}s"
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} light off luminance (dp=6C) is ${fncmd}"
                    }
                }
                else { // 3in1
                    if (settings?.logEnable) log.info "${device.displayName} Max Temp is: ${fncmd} (DP=0x6C)"  
                }
                break
            case 0x6D :    // 109
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {   // case 109: 0x6d PIR enable
                    if (settings?.txtEnable) log.info "${device.displayName} PIR enable is ${fncmd}"                
                }
                else if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.debug "${device.displayName} msLuminanceLevel is ${fncmd}s"
                        illuminanceEventLux( fncmd )  
                    }
                    else {
                        illuminanceEventLux( fncmd )  
                    }
                }
                else { // 3in1
                    if (settings?.logEnable) log.info "${device.displayName} Min Humidity is: ${fncmd} (DP=0x6D)"  
                }
                break
            case 0x6E : // (110) Tuya 4 in 1
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {
                    if (settings?.logEnable) log.trace "${device.displayName} Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    handleTuyaBatteryLevel( fncmd )
                }
                else if (isRadar()) {
                    if (isHumanPresenceSensorAIR()) {
                        if (settings?.logEnable) log.info "${device.displayName} msLedStatus is ${fncmd}s"
                    }
                    else {
                        if (settings?.txtEnable) log.info "${device.displayName} radar LED status is ${fncmd}"                
                    }
                }
                else {  //  3in1
                    if (settings?.logEnable) log.info "${device.displayName} Max Humidity is: ${fncmd} (DP=0x6E)"  
                }
                break 
            case 0x6F : // (111) Tuya 4 in 1: // 0x6f led enable
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') { 
                    if (settings?.txtEnable) log.info "${device.displayName} LED is: ${fncmd == 1 ? 'enabled' :'disabled'}"
                    device.updateSetting("ledEnable", [value:fncmd as boolean, type:"boolean"])
                }
                else { // 3in1 - temperature alarm switch
                    if (settings?.logEnable) log.info "${device.displayName} Temperature alarm switch is: ${fncmd} (DP=0x6F)"  
                }
                break
            case 0x70 : // (112)
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {   // case 112: 0x70 reporting enable (Alarm type)
                    if (settings?.txtEnable) log.info "${device.displayName} reporting enable is ${fncmd}"                
                }
                if (isRadar()) {
                    // trsfScene                    if ( ) { // detection data  for TuYa Radar Sensor with scene
                    if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {
                        if (settings?.txtEnable) log.info "${device.displayName} Scene (dp=70) is ${fncmd}"
                    }
                    else {
                        if (settings?.txtEnable) log.warn "${device.displayName} unknwon model radar scene is ${fncmd}"                
                    }
                }
                else {
                    if (settings?.logEnable) log.info "${device.displayName} Humidity alarm switch is: ${fncmd} (DP=0x6F)"  
                }
                break
            case 0x71 :
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {   // case 113: 0x71 unknown  ( ENUM)
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (0x71 reporting enable?) DP=0x71 fncmd = ${fncmd}"  
                }
                else {    // 3in1 - Alarm Type
                    if (settings?.txtEnable) log.info "${device.displayName} Alar type is: ${fncmd}"                
                }
                break
            case 0x72 : // (114)
                if (isRadar()) {    // trsfMotionDirection
                    if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {
                        if (settings?.txtEnable) log.info "${device.displayName} radar motion direction is ${fncmd}"                
                    }
                    else {
                        if (settings?.txtEnable) log.warn "${device.displayName} unknown radar motion direction 0x72 fncmd = ${fncmd}"
                    }
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar motion direction 0x72 fncmd = ${fncmd}"
                }
                break
            case 0x73 : // (115)
                if (isRadar()) {    // trsfMotionSpeed
                    if (isHumanPresenceSensorScene() || isHumanPresenceSensorFall()) {
                        if (settings?.txtEnable) log.info "${device.displayName} radar motion speed is ${fncmd}"                
                    }
                    else {
                        if (settings?.txtEnable) log.warn "${device.displayName} unknown radar motion speed 0x73 fncmd = ${fncmd}"
                    }
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar motion speed 0x73 fncmd = ${fncmd}"
                }
                break
            case 0x74 : // (116)
                if (isRadar()) {    // trsfFallDownStatus
                    if (isHumanPresenceSensorFall()) {
                        if (settings?.txtEnable) log.info "${device.displayName} radar fall down status is ${fncmd}"                
                    }
                    else {
                        if (settings?.txtEnable) log.warn "${device.displayName} unknown radar fall down status 0x74 fncmd = ${fncmd}"
                    }
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar fall down status 0x74 fncmd = ${fncmd}"
                }
                break
            case 0x75 : // (117)
                if (isRadar()) {    // trsfStaticDwellAlarm
                    if (isHumanPresenceSensorFall()) {
                        if (settings?.txtEnable) log.info "${device.displayName} radar static dwell alarm is ${fncmd}"                
                    }
                    else {
                        if (settings?.txtEnable) log.warn "${device.displayName} unknown radar static dwell alarm 0x75 fncmd = ${fncmd}"
                    }
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar static dwell alarm 0x75 fncmd = ${fncmd}"
                }
                break
            case 0x76 : // (118)
                if (isRadar()) {    // trsfFallSensitivity
                    if (isHumanPresenceSensorFall()) {
                        if (settings?.txtEnable) log.info "${device.displayName} radar fall sensitivity is ${fncmd}"
                    }
                    else {
                        if (settings?.txtEnable) log.warn "${device.displayName} unknown radar fall sensitivity  0x76 fncmd = ${fncmd}"
                    }
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} non-radar fall sensitivity  0x76 fncmd = ${fncmd}"
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


def handleTuyaBatteryLevel( fncmd ) {
    def rawValue = 0
    if (fncmd == 0) rawValue = 100           // Battery Full
    else if (fncmd == 1) rawValue = 75       // Battery High
    else if (fncmd == 2) rawValue = 50       // Battery Medium
    else if (fncmd == 3) rawValue = 25       // Battery Low
    else if (fncmd == 4) rawValue = 100      // Tuya 3 in 1 -> USB powered ! -> PowerSource = USB     capability "PowerSource" Attributes powerSource - ENUM ["battery", "dc", "mains", "unknown"]
    else rawValue = fncmd
    getBatteryPercentageResult(rawValue*2)
}

// not used
def parseIasReport(Map descMap) {
    if (settings?.logEnable) log.debug "pareseIasReport: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
    def zs = new ZoneStatus(Integer.parseInt(descMap?.value, 16))
    //log.trace "zs = ${zs}"
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
        //if (settings?.logEnable) log.trace "zs = $zs"
        if (zs.alarm1Set == true) {
            handleMotion(motionActive=true)
        }
        else {
            handleMotion(motionActive=false)
        }
    }
    catch (e) {
        log.error "${device.displayName} This driver requires HE version 2.2.7 (May 2021) or newer!"
        return null
    }
}

private handleMotion(motionActive) {    
    //log.warn "handleMotion motionActive=${motionActive}"
    if (motionActive) {
        def timeout = motionResetTimer ?: 0
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code
        if (motionReset == true && timeout != 0) {
            runIn(timeout, resetToMotionInactive, [overwrite: true])
        }
        if (device.currentState('motion')?.value != "active") {
            state.motionStarted = now()
        }
    }
    else {
        if (device.currentState('motion')?.value == "inactive") {
            if (settings?.logEnable) log.debug "${device.displayName} ignored motion inactive event after ${getSecondsInactive()}s"
            return [:]   // do not process a second motion inactive event!
        }
    }
	return getMotionResult(motionActive)
}

def getMotionResult(motionActive) {
	def descriptionText = "Detected motion"
    if (!motionActive) {
		descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s"
    }
    else {
        descriptionText = device.currentValue("motion") == "active" ? "Motion is active ${getSecondsInactive()}s" : "Detected motion"
    }
    if (settings?.txtEnable) log.info "${device.displayName} ${descriptionText}"
	return [
			name			: 'motion',
			value			: motionActive ? 'active' : 'inactive',
            //isStateChange   : true,
			descriptionText : descriptionText
	]
}

def resetToMotionInactive() {
	if (device.currentState('motion')?.value == "active") {
		def descText = "Motion reset to inactive after ${getSecondsInactive()}s (software timeout)"
		sendEvent(
			name : "motion",
			value : "inactive",
			isStateChange : true,
			descriptionText : descText
		)
        if (settings?.txtEnable) log.info "${device.displayName} ${descText}"
	}
    else {
        if (settings?.txtEnable) log.debug "${device.displayName} ignored resetToMotionInactive (software timeout) after ${getSecondsInactive()}s"
    }
}

def getSecondsInactive() {
    if (state.motionStarted) {
        return Math.round((now() - state.motionStarted)/1000)
    } else {
        return motionResetTimer ?: 0
    }
}



def temperatureEvent( temperature ) {
    def map = [:] 
    map.name = "temperature"
    map.unit = "°${location.temperatureScale}"
    String tempConverted = convertTemperatureIfNeeded(temperature, "C", precision=1)
    map.value = tempConverted
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

def illuminanceEvent( rawLux ) {
	def lux = rawLux > 0 ? Math.round(Math.pow(10,(rawLux/10000))) : 0
    sendEvent("name": "illuminance", "value": lux, "unit": "lx")
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}

def illuminanceEventLux( Integer lux ) {
    sendEvent("name": "illuminance", "value": lux, "unit": "lx")
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}

// called on initial install of device during discovery
// also called from initialize() in this driver!
def installed() {
    log.info "${device.displayName} installed()"
    unschedule()
}

// called when preferences are saved
def updated() {
    checkDriverVersion()
    ArrayList<String> cmds = []
    
    if (settings?.txtEnable) log.info "${device.displayName} Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b>"
    if (settings?.txtEnable) log.info "${device.displayName} Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff)    // turn off debug logging after 24 hours
        if (settings?.txtEnable) log.info "${device.displayName} Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    
    
    if (true /*state.hashStringPars != calcParsHashString()*/) {    // an configurable device parameter was changed
        if (settings?.logEnable) log.debug "${device.displayName} Config parameters changed! old=${state.hashStringPars} new=${calcParsHashString()}"
        //
        if (getHashParam(ledEnableParamIndex) != calcHashParam(ledEnableParamIndex)) {
            if (is4in1()) {
                cmds += sendTuyaCommand("6F", DP_TYPE_BOOL, settings?.ledEnable == true ? "01" : "00")
                if (settings?.logEnable) log.warn "${device.displayName} changing ledEnable to : ${settings?.ledEnable }"                
            }
        }
        if (true /*getHashParam(sensitivityParamIndex) != calcHashParam(sensitivityParamIndex)*/) {
            if (isRadar()) { 
                cmds += sendTuyaCommand("02", DP_TYPE_VALUE, zigbee.convertToHexString(settings?.sensitivity as int, 8))
                if (settings?.logEnable) log.warn "${device.displayName} changing radar sensitivity to : ${settings?.sensitivity }"                
            }
            else if (isIAS()) {
                cmds += sendSensitivity( settings?.sensitivity )
                if (settings?.logEnable) log.debug "${device.displayName} changing IAS sensitivity to : ${settings?.sensitivity }"                
            }
        }
        if (true /*getHashParam(keepTimeParamIndex) != calcHashParam(keepTimeParamIndex)*/) {
            if (isIAS()) {
                cmds += sendKeepTime( settings?.keepTime )
                if (settings?.logEnable) log.debug "${device.displayName} changing IAS Keep Time to : ${settings?.keepTime }"                
            }
        }
       
        if (getHashParam(detectionDelayParamIndex) != calcHashParam(detectionDelayParamIndex)) {
            if (isRadar()) { 
                cmds += sendTuyaCommand("65", DP_TYPE_VALUE, zigbee.convertToHexString(settings?.detectionDelay as int, 8))
                if (settings?.logEnable) log.warn "${device.displayName} changing radar detection Delay to : ${settings?.detectionDelay }"                
            }
        }
        if (getHashParam(fadingTimeParamIndex) != calcHashParam(fadingTimeParamIndex)) {
            if (isRadar()) { 
                cmds += sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(settings?.fadingTime as int, 8))
                if (settings?.logEnable) log.warn "${device.displayName} changing radar fading time to : ${settings?.fadingTime }"                
            }
        }

      
        if (getHashParam(minimumDistanceParamIndex) != calcHashParam(minimumDistanceParamIndex)) {
            if (isRadar()) { 
                int value = ((settings?.minimumDistance as double) * 100.0) as int
                cmds += sendTuyaCommand("03", DP_TYPE_VALUE, zigbee.convertToHexString(value as int, 8))
                if (settings?.logEnable) log.warn "${device.displayName} changing radar minimum distance to : ${settings?.minimumDistance }"                
            }
        }
        if (getHashParam(maximumDistanceParamIndex) != calcHashParam(maximumDistanceParamIndex)) {
            if (isRadar()) { 
                int value = ((settings?.maximumDistance as double) * 100.0) as int
                cmds += sendTuyaCommand("04", DP_TYPE_VALUE, zigbee.convertToHexString(value as int, 8))
                if (settings?.logEnable) log.warn "${device.displayName} changing radar maximum distance to : ${settings?.maximumDistance }"                
            }
        }

        //

        //
        state.hashStringPars = calcParsHashString()
    }
    else {
        if (settings?.logEnable) log.debug "${device.displayName} no change in state.hashStringPars = {state.hashStringPars}"
    }
    
    if (cmds != null) {
        if (settings?.logEnable) log.debug "${device.displayName} sending the changed AdvancedOptions"
        sendZigbeeCommands( cmds )  
    }
    if (settings?.txtEnable) log.info "${device.displayName} preferencies updates are sent to the device..."
}


def refresh() {
    ArrayList<String> cmds = []
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh()..."}
    //cmds += zigbee.readAttribute(0, 1)
    if (isIAS()) {
        cmds += readSensitivity()
        cmds += readKeepTime()
    }
    sendZigbeeCommands( cmds ) 
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion != null && driverVersionAndTimeStamp() == state.driverVersion) {
        // no driver version change
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

    if (fullInit == true || settings.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit == true || settings.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings.motionReset == null) device.updateSetting("motionReset", false)
    if (fullInit == true || settings.motionResetTimer == null) device.updateSetting("motionResetTimer", 60)
    if (fullInit == true || settings.advancedOptions == null) device.updateSetting("advancedOptions", false)
    if (fullInit == true || settings.sensitivity == null) device.updateSetting("sensitivity", [value:"No selection", type:"enum"])
    if (fullInit == true || settings.keepTime == null) device.updateSetting("keepTime", [value:"No selection", type:"enum"])
    if (fullInit == true || settings.ignoreDistance == null) device.updateSetting("ignoreDistance", true)
    if (fullInit == true || settings.ledEnable == null) device.updateSetting("ledEnable", true)
    if (fullInit == true || settings.temperatureOffset == null) device.updateSetting("humidityOffset", 0.0)
    if (fullInit == true || settings.humidityOffset == null) device.updateSetting("humidityOffset", 0.0)
    if (fullInit == true || settings.luxOffset == null) device.updateSetting("luxOffset", 1.0)
    if (fullInit == true || settings.sensitivity == null) device.updateSetting("sensitivity", 7)
    if (fullInit == true || settings.detectionDelay == null) device.updateSetting("detectionDelay", 15)
    if (fullInit == true || settings.fadingTime == null) device.updateSetting("fadingTime", 60)
    if (fullInit == true || settings.minimumDistance == null) device.updateSetting("minimumDistance", 1.00)
    if (fullInit == true || settings.maximumDistance == null) device.updateSetting("maximumDistance", 6.00)
    //  capability "PowerSource" Attributes powerSource - ENUM ["battery", "dc", "mains", "unknown"]
    if (fullInit == true) sendEvent(name : "powerSource",	value : "unknown", isStateChange : true)
    //
    state.hashStringPars = calcParsHashString()
    if (settings?.logEnable) log.trace "${device.displayName} state.hashStringPars = ${state.hashStringPars}"
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
    runIn( 3, logInitializeRezults, [overwrite: true])
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

def setMotion( mode ) {
    switch (mode) {
        case "active" : 
            sendEvent(handleMotion(motionActive=true))
            break
        case "inactive" :
            sendEvent(handleMotion(motionActive=false))
            break
        default :
            break
    }
}

import java.security.MessageDigest
String generateMD5(String s) {
    if(s != null) {
        return MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
    } else {
        return "null"
    }
}


def calcParsHashString() {
    String hashPars = ''
    for (int i = 0; i< numberOfconfigParams; i++) {
        hashPars += calcHashParam( i )     
    }
    return hashPars
}

def getHashParam(num) {
    try {
        return state.hashStringPars[num*2..num*2+1]
    }
    catch (e) {
        log.error "exception caught getHashParam(${num})"
        return '??' 
    }
}


def calcHashParam(num) {
    def hashByte
    try {
        switch (num) {
            case temperatureOffsetParamIndex : hashByte = generateMD5(temperatureOffset.toString())[-2..-1];  break
            case humidityOffsetParamIndex :    hashByte = generateMD5(humidityOffset.toString())[-2..-1];     break
            case luxOffsetParamIndex :         hashByte = generateMD5(luxOffset.toString())[-2..-1];          break
            case ledEnableParamIndex :         hashByte = generateMD5(ledEnable.toString())[-2..-1];          break
            case sensitivityParamIndex :       hashByte = generateMD5(sensitivity.toString())[-2..-1];        break
            case detectionDelayParamIndex :    hashByte = generateMD5(detectionDelay.toString())[-2..-1];     break
            case fadingTimeParamIndex :        hashByte = generateMD5(fadingTime.toString())[-2..-1];         break
            case minimumDistanceParamIndex :   hashByte = generateMD5(minimumDistance.toString())[-2..-1];    break
            case maximumDistanceParamIndex :   hashByte = generateMD5(maximumDistance.toString())[-2..-1];    break
            case keepTimeParamIndex :          hashByte = generateMD5(keepTime.toString())[-2..-1];           break
            //minimumDistance
            default :
                log.error "invalid par calcHashParam(${num})"
                return '??'
        }
    }
    catch (e) {
        log.error "exception caught calcHashParam(${num})"
        return '??' 
    }
}


def getSensitivityString( value ) {
    return value == 0 ? "low" : value == 1 ? "medium" : value == 2 ? "high" : null
}

def getKeepTimeString( value ) {
    return value == 0 ? "30" : value == 1 ? "60" : value == 2 ? "120" : null
}

def readSensitivity() {
    return zigbee.readAttribute(0x0500, 0x0013, [:], delay=200)
}

def readKeepTime() {
    return zigbee.readAttribute(0x0500, 0xF001, [:], delay=200)
}

//  input (name: "sensitivity", type: "enum", title: "Sensitivity", description:"Select PIR sensor sennsitivity", defaultValue: 0, options:  ["--- Select ---":"--- Select ---", "low":"low", "medium":"medium", "high":"high"])
def sendSensitivity( String mode ) {
    if (mode == null) {
        if (settings?.logEnable) log.warn "${device.displayName} sensitivity is not set for ${device.getDataValue('manufacturer')}"
        return null
    }
    ArrayList<String> cmds = []
    String value = null
    if (!(is2in1() || isConfigurable()))  {
        if (settings?.logEnable) log.warn "${device.displayName} sensitivity configuration may not work for ${device.getDataValue('manufacturer')}"
        // continue anyway ..
    }
    value = mode == "low" ? 0: mode == "medium" ? 1 : mode == "high" ? 02 : null
    if (value != null) {
        cmds += zigbee.writeAttribute(0x0500, 0x0013, DataType.UINT8, value.toInteger(), [:], delay=200)
        if (settings?.logEnable) log.trace "${device.displayName} sending sensitivity : ${mode} (${value.toInteger()})"
        //sendZigbeeCommands( cmds )         // only prepare the cmds here!
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} sensitivity ${mode} is not supported for your model:${device.getDataValue('model') } manufacturer:${device.getDataValue('manufacturer')}"
    }
    return cmds
}

def sendKeepTime( String mode ) {
    if (mode == null) {
        if (settings?.logEnable) log.warn "${device.displayName} Keep Time is not set for ${device.getDataValue('manufacturer')}"
        return null
    }
    ArrayList<String> cmds = []
    String value = null
    if (!(is2in1() || isConfigurable()))  {
        if (settings?.logEnable) log.warn "${device.displayName} Keep Time configuration may not work for ${device.getDataValue('manufacturer')}"
        // continue anyway .. //['30':'30', '60':'60', '120':'120']
    }
    value = mode == "30" ? 0: mode == "60" ? 1 : mode == "120" ? 02 : null
    if (value != null) {
        cmds += zigbee.writeAttribute(0x0500, 0xF001, DataType.UINT8, value.toInteger(), [:], delay=200) 
        if (settings?.logEnable) log.trace "${device.displayName} sending sensitivity : ${mode} (${value.toInteger()})"
        //sendZigbeeCommands( cmds )    // only prepare the cmds here!
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} Keep Time ${mode} is not supported for your model:${device.getDataValue('model') } manufacturer:${device.getDataValue('manufacturer')}"
    }
    return cmds
}


def deleteAllStatesAndJobs() {
    state.clear()
    unschedule()
    device.deleteCurrentState('motion')
    device.deleteCurrentState('temperature')
    device.deleteCurrentState('humidity')
    device.deleteCurrentState('illuminance')
    device.deleteCurrentState('tamper')
    device.deleteCurrentState('distance')
    device.deleteCurrentState('powerSource')
    device.deleteCurrentState('*')
    device.deleteCurrentState('')
    //device.removeDataValue("anyAddedCustomData")
    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}"
}

def test( dpCommand, dpValue, dpTypeString ) {
    ArrayList<String> cmds = []
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    log.warn " sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}    

