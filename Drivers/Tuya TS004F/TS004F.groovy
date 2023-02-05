/**
 *  Scene switch TS004F driver for Hubitat Elevation hub.
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
 *  The inital version was based on ST DH "Zemismart Button", namespace: SangBoy, author: YooSangBeom
 * 
 * ver. 1.0.0 2021-05-08 kkossev     - SmartThings version 
 * ver. 2.0.0 2021-10-03 kkossev     - First version for Hubitat in 'Scene Control'mode - AFTER PAIRING FIRST to Tuya Zigbee gateway!
 * ver. 2.1.0 2021-10-20 kkossev     - typos fixed; button wrong event names bug fixed; extended debug logging; added experimental switchToDimmerMode command
 * ver. 2.1.1 2021-10-20 kkossev     - numberOfButtons event bug fix; 
 * ver. 2.2.0 2021-10-20 kkossev     - First succesfuly working version with HE!
 * ver. 2.2.1 2021-10-23 kkossev     - added "Reverse button order" preference option
 * ver. 2.2.2 2021-11-17 kkossev     - added battery reporting capability; added buttons handlers for use in Hubutat Dashboards; code cleanup
 * ver. 2.2.3 2021-12-01 kkossev     - added fingerprint for Tuya Remote _TZ3000_pcqjmcud
 * ver. 2.2.4 2021-12-05 kkossev     - added support for 'YSR-MINI-Z Remote TS004F'
 * ver. 2.3.0 2022-02-13 kkossev     - added support for 'Tuya Smart Knob TS004F'
 * ver. 2.4.0 2022-03-31 kkossev     - added support for 'MOES remote TS0044', singleThreaded: true; bug fix: debouncing timer was not started for TS0044
 * ver. 2.4.1 2022-04-23 kkossev     - improved tracing of debouncing logic code; option [overwrite: true] is set explicitely on debouncing timer restart; debounce timer increased to 1000ms  
 * ver. 2.4.2 2022-05-07 kkossev     - added LoraTap 6 button Scene Controller; device.getDataValue bug fix;
 * ver. 2.4.3 2022-09-18 kkossev     - added TS0042 Tuya Zigbee 2 Gang Wireless Smart Switch; removed 'release' event for TS0044 switches (not supported by hardware); 'release' digital event bug fix.
 * ver. 2.4.4 2022-10-22 kkossev     - _TZ3000_vp6clf9d fingerprint correction; importURL changed to dev. branch; added _TZ3000_w8jwkczz and other TS0041, TS0042, TS0043, TS004 fingerprints
 * ver. 2.4.5 2022-10-27 kkossev     - added icasa ICZB-KPD18S 8 button controller.
 * ver. 2.4.6 2022-11-20 kkossev     - added TS004F _TZ3000_ja5osu5g - 1 button!; isTuya() bug fix
 * ver. 2.4.7 2022-12-22 kkossev     - added TS004F _TZ3000_rco1yzb1 LIDL Smart Button SSBM A1; added _TZ3000_u3nv1jwk 
 * ver. 2.5.0 2023-01-14 kkossev     - bug fix: battery percentage remaining automatic reporting was not configured, now hardcoded to 8 hours; bug fix: 'released'event; debug info improvements; declared supportedButtonValues attribute
 * ver. 2.5.1 2023-01-20 kkossev     - battery percentage remaining HomeKit compatibility
 * ver. 2.5.2 2023-01-28 kkossev     - _TZ3000_vp6clf9d (TS0044) debouncing; added Loratap TS0046 (6 buttons); 
 * ver. 2.6.0 2023-01-28 kkossev     - added healthStatus; Initialize button is disabled;
 * ver. 2.6.1 2023-02-05 kkossev     - added _TZ3000_mh9px7cq; isSmartKnob() typo fix; added capability 'Health Check'; added powerSource attribute 'battery'; added dummy ping() code; added _TZ3000_famkxci2
 *
 *                                   - TODO: add Advanced options; TODO: debounce timer configuration; add 'auto revert to scene mode' option
 *
 */

def version() { "2.6.1" }
def timeStamp() {"2023/02/05 8:08 AM"}

@Field static final Boolean debug = false
@Field static final Integer healthStatusCountTreshold = 4

import groovy.transform.Field
import hubitat.helper.HexUtils
import hubitat.device.HubMultiAction
import groovy.json.JsonOutput

metadata {
    definition (name: "Tuya Scene Switch TS004F", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20TS004F/TS004F.groovy", singleThreaded: true ) {
      
    capability "Refresh"
    capability "PushableButton"
    capability "DoubleTapableButton"
    capability "HoldableButton"
   	capability "ReleasableButton"
    capability "Battery"
    capability "PowerSource"
    capability "Configuration"
    capability "Health Check"

    attribute "supportedButtonValues", "JSON_OBJECT"
    attribute "switchMode", "enum", ["dimmer", "scene"]
    attribute "batteryVoltage", "number"
    attribute "healthStatus", "enum", ["offline", "online"]
    attribute "powerSource", "enum", ["battery", "dc", "mains", "unknown"]
        
    if (debug == true) {
        command "switchMode", [[name: "mode*", type: "ENUM", constraints: ["dimmer", "scene"], description: "Select device mode"]]
        command "test", [[name: "test", type: "STRING", description: "test", defaultValue : ""]]
    }
              
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_xabckq1v", model: "TS004F", deviceJoinName: "Tuya Scene Switch TS004F"
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_pcqjmcud", model: "TS004F", deviceJoinName: "YSR-MINI-Z Remote TS004F"
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_4fjiwweb", model: "TS004F", deviceJoinName: "Tuya Smart Knob TS004F"
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_uri7ongn", model: "TS004F", deviceJoinName: "Tuya Smart Knob TS004F"    // not tested
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_ixla93vd", model: "TS004F", deviceJoinName: "Tuya Smart Knob TS004F"    // not tested
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_qja6nq5z", model: "TS004F", deviceJoinName: "Tuya Smart Knob TS004F"    // not tested
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_csflgqj2", model: "TS004F", deviceJoinName: "Tuya Smart Knob TS004F"    // not tested

    fingerprint inClusters: "0000,0001,0006", outClusters: "0019,000A", manufacturer: "_TZ3400_keyjqthh", model: "TS0041", deviceJoinName: "Tuya YSB22 TS0041"
    fingerprint inClusters: "0000,0001,0006", outClusters: "0019,000A", manufacturer: "_TZ3400_tk3s5tyg", model: "TS0041", deviceJoinName: "Tuya TS0041" // not tested
    fingerprint inClusters: "0000,0001,0006", outClusters: "0019,000A", manufacturer: "_TZ3000_xrqsdxq6", model: "TS0041", deviceJoinName: "Zigbee Tuya 1 Button" // not tested
    fingerprint inClusters: "0000,0001,0006", outClusters: "0019,000A", manufacturer: "_TZ3400_tk3s5tyg", model: "TS0041", deviceJoinName: "Zigbee Tuya 1 Button"
    fingerprint inClusters: "0000,0001,0006", outClusters: "0019,000A", manufacturer: "_TZ3000_tk3s5tyg", model: "TS0041", deviceJoinName: "Zigbee Tuya 1 Button"
    fingerprint inClusters: "0000,0001,0006", outClusters: "0019,000A", manufacturer: "_TZ3000_adkvzooy", model: "TS0041", deviceJoinName: "Zigbee Tuya 1 Button" // not tested
    fingerprint inClusters: "0000,000A,0001,0006", outClusters: "0019,000A", manufacturer: "_TZ3000_peszejy7", model: "TS0041", deviceJoinName: "Zigbee Tuya 1 Button"
    fingerprint inClusters: "0000,0001,0006", outClusters: "0019", manufacturer: "_TYZB02_key8kk7r", model: "TS0041", deviceJoinName: "Zigbee Tuya 1 Button"
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000,E001", outClusters: "0019,000A,0003,0004,0006,0008,1000", manufacturer: "_TZ3000_ja5osu5g", model: "TS004F", deviceJoinName: "MOES Smart Button (ZT-SY-SR-MS)" // MOES ZigBee IP55 Waterproof Smart Button Scene Switch & Wireless Remote Dimmer (ZT-SY-SR-MS)
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000,E001", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_rco1yzb1", model: "TS004F", deviceJoinName: "LIDL Smart Button SSBM A1"
        
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TZ3000_tzvbimpq", deviceJoinName: "Tuya 2 button Scene Switch"
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TZ3000_t8hzpgnd", deviceJoinName: "Tuya 2 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TYZB02_keyjhapk", deviceJoinName: "Tuya 2 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TZ3400_keyjhapk", deviceJoinName: "Tuya 2 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TZ3000_dfgbtub0", deviceJoinName: "Tuya 2 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TZ3000_h1c2eamp", deviceJoinName: "Tuya 2 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TZ3000_owgcnkrh", deviceJoinName: "Tuya 2 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TZ3000_tzvbimpq", deviceJoinName: "Tuya 2 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0042", manufacturer:"_TZ3000_fkvaniuu", deviceJoinName: "Tuya 2 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters: "0000,0001,0006", outClusters: "0019", manufacturer: "_TYZB02_key8kk7r", model: "TS0042", deviceJoinName: "Tuya 2 button Scene Switch"
        
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TZ3000_w8jwkczz", deviceJoinName: "Tuya 3 button Scene Switch"
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TZ3000_gbm10jnj", deviceJoinName: "Tuya Zigbee 3 button"          // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TYZB02_key8kk7r", deviceJoinName: "Tuya 3 button Scene Switch"
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TZ3000_qzjcsmar", deviceJoinName: "Tuya 3 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0006", outClusters:"0019", model:"TS0043", manufacturer:"_TZ3000_bi6lpsew", deviceJoinName: "Tuya 3 button Scene Switch"
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TZ3000_imnwsek2", deviceJoinName: "Tuya 3 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TZ3000_rrjr1q0u", deviceJoinName: "Tuya 3 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TZ3000_w4thianr", deviceJoinName: "Tuya 3 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TZ3000_a7ouggvs", deviceJoinName: "Zigbee Lonsonho 3 Button"      // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0043", manufacturer:"_TZ3000_famkxci2", deviceJoinName: "Loratap 3 Button rRemote"      // https://community.hubitat.com/t/zigbee-3-switch-remote-driver/111935/3?u=kkossev

    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0044", manufacturer:"_TZ3000_vp6clf9d", deviceJoinName: "Zemismart Wireless Scene Switch"          
    fingerprint inClusters: "0000,000A,0001,0006", outClusters: "0019", manufacturer: "_TZ3000_vp6clf9d", model: "TS0044", deviceJoinName: "Zemismart 4 Button Remote (ESW-0ZAA-EU)"                      // needs debouncing
    fingerprint inClusters: "0000,000A,0001,0006", outClusters: "0019", manufacturer: "_TZ3000_ufhtxr59", model: "TS0044", deviceJoinName: "Tuya 4 button Scene Switch"
    fingerprint inClusters: "0000,000A,0001,0006", outClusters: "0019", manufacturer: "_TZ3000_wkai4ga5", model: "TS0044", deviceJoinName: "Tuya 4 button Scene Switch"        // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver/92823/79?u=kkossev
    fingerprint inClusters: "0000,000A,0001,0006", outClusters: "0019", manufacturer: "_TZ3000_abci1hiu", model: "TS0044", deviceJoinName: "Tuya 4 button Scene Switch"        // not tested        
    fingerprint inClusters: "0000,000A,0001,0006", outClusters: "0019", manufacturer: "_TZ3000_ee8nrt2l", model: "TS0044", deviceJoinName: "Tuya 4 button Scene Switch"        // not tested
    fingerprint inClusters: "0000,000A,0001,0006", outClusters: "0019", manufacturer: "_TZ3000_dku2cfsc", model: "TS0044", deviceJoinName: "Tuya 4 button Scene Switch"        // not tested
    fingerprint inClusters: "0000,000A,0001,0006", outClusters: "0019", manufacturer: "_TYZB01_cnlmkhbk", model: "TS0044", deviceJoinName: "Tuya 4 button Scene Switch"        // not tested
    fingerprint inClusters: "0000,0001,0006", outClusters: "0019,000A", manufacturer: "_TZ3000_u3nv1jwk", model: "TS0044", deviceJoinName: "Tuya 4 button Scene Switch"        // not tested https://community.hubitat.com/t/zigbee-wireless-scene-switch/108146?u=kkossev
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters:"0019,000A", model:"TS0044", manufacturer:"_TZ3000_mh9px7cq", deviceJoinName: "Moes 4 button controller"    // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver/92823/75?u=kkossev

    fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_abci1hiu", model: "TS0044", deviceJoinName: "MOES Remote TS0044"

    fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_2m38mh6k", deviceJoinName: "LoraTap 6 button Scene Switch"        
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_zqtiam4u", deviceJoinName: "Tuya 6 button Scene Switch"    // not tested
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0046", manufacturer:"_TZ3000_iszegwpd", deviceJoinName: "TLoraTap 6 Scene Switch"       // https://community.hubitat.com/t/loratap-6-button-controller-drivers/91951/20?u=kkossev
        
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0B05,1000", outClusters:"0003,0004,0005,0006,0008,0019,0300,1000", model:"ICZB-KPD18S", manufacturer:"icasa", deviceJoinName: "Icasa 8 button Scene Switch"    //https://community.hubitat.com/t/beginners-question-fantastic-button-controller-not-working/103914

            
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "Enable description text logging", defaultValue: true)
        input (name: "reverseButton", type: "bool", title: "Reverse button order", defaultValue: true)
        // input (name: "advancedOptions", type: "bool", title: "Advanced options", defaultValue: false)
    }
}

// Constants
@Field static final Integer DIMMER_MODE = 0
@Field static final Integer SCENE_MODE  = 1
@Field static final Integer DEBOUNCE_TIME = 1000

def isTuya()  {device.getDataValue("model") in ["TS0601", "TS004F", "TS0044", "TS0043", "TS0042", "TS0041"]}
def isIcasa() {device.getDataValue("manufacturer") == "icasa"}
def isSmartKnob() {device.getDataValue("manufacturer") in ["_TZ3000_4fjiwweb", "_TZ3000_rco1yzb1", "_TZ3000_uri7ongn", "_TZ3000_ixla93vd", "_TZ3000_qja6nq5z", "_TZ3000_csflgqj2" ]}
def needsDebouncing() {device.getDataValue("model") == "TS004F" || (device.getDataValue("manufacturer") in ["_TZ3000_abci1hiu", "_TZ3000_vp6clf9d"])}

// Parse incoming device messages to generate events
def parse(String description) {
    setHealthStatusOnline()
    checkDriverVersion()
    if (logEnable) log.debug "${device.displayName} description is $description"
	def event = null
    try {
        event = zigbee.getEvent(description)
    }
    catch (e) {
        if (logEnable) {log.warn "${device.displayName} exception caught while procesing event $description"}
    }
	def result = []
    def buttonNumber = 0
    
	if (event) {
        if (logEnable) log.debug "${device.displayName} Event enter: $event"
        switch (event.name) {
            case 'battery' :
                event.value = event.value as int    // HomeKit
                event.unit = '%'
                break
            case 'batteryVoltage' :
                event.unit = 'V'
                break
            default :
                if (logEnable) log.debug "${device.displayName} Unexpected event: $event"
                break
        }
        event.descriptionText = "${event.name} is ${event.value} ${event.unit}"
        event.isStateChange = true
        event.type = 'physical'
        if (txtEnable) log.info "${device.displayName} ${event.descriptionText}"
        result = event
    }
    else if (description?.startsWith("catchall")) {
        def descMap = zigbee.parseDescriptionAsMap(description)            
        if (logEnable) log.debug "${device.displayName} catchall descMap: $descMap"
        def buttonState = "unknown"
        // when TS004F initialized in Scene switch mode!
        if (descMap.clusterInt == 0x0006 && descMap.command == "FD") {
            if (descMap.sourceEndpoint == "03") {
     	        buttonNumber = reverseButton==true ? 3 : 1
            }
            else if (descMap.sourceEndpoint == "04") {
      	        buttonNumber = reverseButton==true  ? 4 : 2
            }
            else if (descMap.sourceEndpoint == "02") {
                buttonNumber = reverseButton==true  ? 2 : 3
            }
            else if (descMap.sourceEndpoint == "01") {
       	        buttonNumber = reverseButton==true  ? 1 : 4
            }
            if (descMap.data[0] == "00")
                buttonState = "pushed"
            else if (descMap.data[0] == "01")
                buttonState = "doubleTapped"
            else if (descMap.data[0] == "02")
                buttonState = "held"
            else {
                if (logEnable) {log.warn "${device.displayName} unkknown data in event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint} data[0] = ${descMap.data[0]}"}
                return null 
            }
        } // command == "FD"
        else if (descMap.clusterInt == 0x0006 && descMap.command == "FC") {
            // Smart knob
            if (descMap.data[0] == "00") {            // Rotate one click right
                buttonNumber = 2
            }
            else if (descMap.data[0] == "01") {       // Rotate one click left
                buttonNumber = 3
            }
            buttonState = "pushed"
        }
        else if (descMap.clusterId in ['0000', '0006', 'E001'] && descMap.command == "01") { // read attribute response
            if (descMap.data[2] == '86') {
                if (logEnable) log.debug "${device.displayName} readAttributeResponse cluster: ${descMap.clusterId} unsupported attribute ${descMap.data[1]+descMap.data[0]} status:${descMap.data[2]}"
            }
            else {
                if (logEnable) log.debug "${device.displayName} readAttributeResponse cluster: ${descMap.clusterId} ${descMap.data[1]+descMap.data[0]} status:${descMap.data[2]} value:${descMap?.value}"
            }
            return null
        }
        else if (descMap.clusterInt == 0x0006 && descMap.command == "04") { // write attribute response
            if (logEnable) log.debug "${device.displayName} writeAttributeResponse cluster: ${descMap.clusterId} status:${descMap.data[0]}"
            return null
        }
        else if (descMap?.profileId == '0000' && descMap?.clusterId == '0013') { // device announcement
            if (logEnable) log.debug "${device.displayName} received device announcement, Device network ID: ${descMap.data[2]+descMap.data[1]}"
            return null
        }
        else if (descMap.clusterId == "EF00" && descMap.command == "01") { // check for LoraTap button events
            if (descMap.data.size() == 10 && descMap.data[2] == "0A" ) {
                def value = zigbee.convertHexToInt(descMap?.data[9])
                def descText = "battery is ${value} %"
                if (txtEnable) log.info "${device.displayName} ${descText}"
                return createEvent(name: "battery", value: value, unit: '%', descriptionText: descText, type: 'physical')
            }
            else if (descMap.data.size() == 7 && descMap.data[2] >= "01" && descMap.data[2] <= "06") {
                buttonNumber = zigbee.convertHexToInt(descMap.data[2])
                if (descMap.data[6] == "00")
                    buttonState = "pushed"
                else if (descMap.data[6] == "01")
                    buttonState = "doubleTapped"
                else if (descMap.data[6] == "02")
                    buttonState = "held"
            }
            else {
                if (logEnable) {log.debug "${device.displayName} unprocessed Tuya cluster EF00 command descMap: $descMap"}
            }
        }
        else if (isIcasa()) {
            (buttonNumber,buttonState) = processIcasa( descMap )
        }
        else {
            if (logEnable) {log.warn "${device.displayName} unprocessed catchall from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint}"}
            if (logEnable) {log.debug "${device.displayName} catchall descMap: $descMap"}
        }
        //
        if (buttonNumber != 0 ) {
            if (needsDebouncing()) {
                if ( state.lastButtonNumber == buttonNumber ) {    // debouncing timer still active!
                    if (logEnable) {log.warn "${device.displayName} ignored event for button ${state.lastButtonNumber} - still in the debouncing time period!"}
                    runInMillis(DEBOUNCE_TIME, buttonDebounce, [overwrite: true])    // restart the debouncing timer again
                    if (logEnable) {log.debug "${device.displayName} restarted debouncing timer ${DEBOUNCE_TIME}ms for button ${buttonNumber} (lastButtonNumber=${state.lastButtonNumber})"}
                    return null 
                }
            }
            state.lastButtonNumber = buttonNumber
        }
        else {
            if (logEnable) {log.warn "${device.displayName} UNHANDLED event for button ${buttonNumber},  lastButtonNumber=${state.lastButtonNumber}"}
        }
        if (buttonState != "unknown" && buttonNumber != 0) {
	        def descriptionText = "button $buttonNumber was $buttonState"
	        event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
            if (txtEnable) {log.info "${device.displayName} $descriptionText"}
        }
        
        if (event) {
            //if (logEnable) {log.debug "${device.displayName} Creating event: ${event}"}
		    result = createEvent(event)
            if (device.getDataValue("model") == "TS004F" || device.getDataValue("manufacturer") == "_TZ3000_abci1hiu") {
                runInMillis(DEBOUNCE_TIME, buttonDebounce, [overwrite: true])
            }
	    } 
	} // if catchall
    else {
        def descMap = zigbee.parseDescriptionAsMap(description)
        if (logEnable) log.debug "${device.displayName} raw: descMap: $descMap"
        //log.trace "${device.displayName} descMap.cluster=${descMap.cluster} descMap.attrId=${descMap.attrId} descMap.command=${descMap.command} "
        if (descMap.cluster == "0006" && descMap.attrId == "8004" /* && command in ["01", "0A"] */) {
            if (descMap.value == "00") {
                sendEvent(name: "switchMode", value: "dimmer", isStateChange: true) 
                if (txtEnable) log.info "${device.displayName} mode is <b>dimmer</b>"
            }
            else if (descMap.value == "01") {
                sendEvent(name: "switchMode", value: "scene", isStateChange: true)
                if (txtEnable) log.info "${device.displayName} mode is <b>scene</b>"
            }
            else {
                if (logEnable) log.warn "${device.displayName} unknown attrId ${descMap.attrId} value ${descMap.value}"
            }
        }
        else if (descMap?.cluster == '0000' && descMap?.command in ['01'] ) { // Basic Cluster responses
            if (logEnable) log.debug "${device.displayName} skipping Basic cluster ${descMap?.cluster} response"
            return null
        }
        else {
            if (logEnable) {log.warn "${device.displayName} DID NOT PARSE MESSAGE for description : $description"}
        }
	}
    return result
}

@Field static final Integer BUTTON_I = 8
@Field static final Integer BUTTON_O = 7

def processIcasa( descMap ) {
    buttonNumber = 0
    buttonState = "unknown"
    //log.trace "descMap=${descMap}"
    if (descMap?.clusterId == "0006") {
        switch (descMap?.command) {
            case "00" :    // button "O" -> 7
                buttonNumber = BUTTON_O
                buttonState = "pushed"
                break
            case "01" :    // pushed button "I" -> 8
                buttonNumber = BUTTON_I
                buttonState = "pushed"
                break
            default :
                if (logEnable) log.warn "${device.displayName} unprocessed ICASA cluster 0006 command ${descMap?.command}"
        }
    }
    else if (descMap?.clusterId == "0008") {
        switch (descMap?.command) {
            case "05" :    // HELD for both buttons I and O
                if (descMap?.data[0] == '00') buttonNumber = BUTTON_I
                else if (descMap?.data[0] == '01') buttonNumber = BUTTON_O
                else {if (logEnable) log.warn "${device.displayName} unprocessed ICASA cluster 0008 HOLD command data=${descMap?.data}" }
                buttonState = "held"
                break
            case "07" :    // RELEASE after HOLD for both buttons I and O
                buttonNumber = state.lastButtonNumber
                buttonState = "released"
                if (logEnable) log.debug "${device.displayName} generating release for state.lastButtonNumber ${state.lastButtonNumber}"
                break
            default :
                if (logEnable) log.warn "${device.displayName} unprocessed ICASA cluster 0008 command ${descMap?.command}"
        }
    }
    else if (descMap?.clusterId == "0005") {
        switch (descMap?.command) {
            case "05" :    // CLICK for buttons S1..S6
                buttonNumber = zigbee.convertHexToInt(descMap?.data[2])
                buttonState = "pushed"
                break
            case "04" :    //  HELD for buttons S1..S6
                buttonNumber = zigbee.convertHexToInt(descMap?.data[2])
                buttonState = "held"
                break
            default :
                if (logEnable) log.warn "${device.displayName} unprocessed ICASA cluster 0005 command ${descMap?.command}"
        }
    }
    else {
        if (logEnable) log.warn "${device.displayName} unprocessed ICASA cluster ${decMap?.clusterId} message ${descMap}"
    }
       
    return [buttonNumber, buttonState]
}


def refresh() {
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        if (txtEnable==true) log.debug "${device.displayName} updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        scheduleDeviceHealthCheck()
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void initializeVars(boolean fullInit = true ) {
    if (settings?.txtEnable) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (fullInit == true || settings?.logEnable == null) device.updateSetting("logEnable", false)
    if (fullInit == true || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings?.reverseButton == null) device.updateSetting("reverseButton", true)
    if (fullInit == true || settings?.advancedOptions == null) device.updateSetting("advancedOptions", false)
    if (fullInit == true || state.notPresentCounter == null) state.notPresentCounter = 0
}

def configure() {
	if (logEnable) log.debug "${device.displayName} Configuring device ${device.getDataValue("model")} in Scene Switch mode..."
    initialize()
}

def installed() 
{
  	initialize()
}

def initialize() {
    if (true /*isTuya()*/) {
        tuyaMagic()
    }
    else {
    	if (logEnable) log.debug "${device.displayName} skipped TuyaMagic() for non-Tuya device ${device.getDataValue("model")} ..."
    }
    def numberOfButtons = 4
    def supportedValues = ["pushed", "double", "held"]
    if (device.getDataValue("model") == "TS0041" || device.getDataValue("manufacturer") =="_TZ3000_ja5osu5g") {
    	numberOfButtons = 1
    }
    else if (device.getDataValue("model") == "TS0042") {
    	numberOfButtons = 2
    }
    else if (device.getDataValue("model") == "TS0043") {
    	numberOfButtons = 3
    }
    else if (device.getDataValue("model") == "TS004F" || device.getDataValue("model") == "TS0044") {
        if (isSmartKnob()) {    // Smart Knob 
            log.debug "${device.displayName} device ${device.data.manufacturer} identified as Smart Knob model ${device.data.model}"
        	numberOfButtons = 3
            supportedValues = ["pushed", "double", "held", "released"]
        }
        else {
            log.debug "${device.displayName} device ${device.data.manufacturer} identified as 4 keys scene switch model ${device.data.model}"
        	numberOfButtons = 4
            supportedValues = ["pushed", "double", "held"]    // no released events are generated in scene switch mode
        }
    }
    else if (device.getDataValue("model") == "TS0045") {    // just in case a new Tuya devices manufacturer decides to invent a new  model! :) 
    	numberOfButtons = 5
    }
    else if (device.getDataValue("model") in ["TS0601", "TS0046"]) {
        numberOfButtons = 6
    }
    else if (isIcasa()) {
        numberOfButtons = 8
        supportedValues = ["pushed", "held", "released"]
    }
    else {
    	numberOfButtons = 4	// unknown
        supportedValues = ["pushed", "double", "held", "released"]
        log.warn "${device.displayName}<b>unknown device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')}. Please report this log to the developer.</b>"
    }
    sendEvent(name: "numberOfButtons", value: numberOfButtons, isStateChange: true)
    sendEvent(name: "supportedButtonValues", value: JsonOutput.toJson(supportedValues), isStateChange: true)
    if(device.currentValue('healthStatus') == null) setHealthStatusValue('unknown')
    if(device.currentValue('powerSource') == null) sendEvent(name: "powerSource", value: "battery", isStateChange: true)
    
    state.lastButtonNumber = 0
    scheduleDeviceHealthCheck()
}

def updated() 
{
    if (logEnable) {log.debug "${device.displayName} updated()"}
    scheduleDeviceHealthCheck()
}

def buttonDebounce(button) {
    if (logEnable) log.debug "${device.displayName} debouncing timer for button ${state.lastButtonNumber} expired."
    state.lastButtonNumber = 0
}

def switchToSceneMode()
{
    if (logEnable) log.debug "${device.displayName} Switching TS004F into Scene mode"
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01))
}

def switchToDimmerMode()
{
    if (logEnable) log.debug "${device.displayName} Switching TS004F into Dimmer mode"
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x00))
}

def buttonEvent(buttonNumber, buttonState, isDigital=false) {

    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital==true ? 'digital' : 'physical']
    if (txtEnable) {log.info "${device.displayName} $event.descriptionText"}
    sendEvent(event)
}

def push(buttonNumber) {
    buttonEvent(buttonNumber, "pushed", isDigital=true)
}

def doubleTap(buttonNumber) {
    buttonEvent(buttonNumber, "doubleTapped", isDigital=true)
}

def hold(buttonNumber) {
    buttonEvent(buttonNumber, "held", isDigital=true)
}

def release(buttonNumber) {
    buttonEvent(buttonNumber, "released", isDigital=true)
}

def switchMode( mode ) {
    if (mode == "dimmer") {
        switchToDimmerMode()
    }
    else if (mode == "scene") {
        switchToSceneMode()
    }
}

def tuyaMagic() {
    ArrayList<String> cmd = []
    //cmd += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, Unknown 0xfffe
    cmd +=  "raw 0x0000  {10 00 00 04 00 00 00 01 00 05 00 07 00 FE FF}"
    cmd +=  "send 0x${device.deviceNetworkId} 1 255"
    cmd += "delay 200"
    cmd += zigbee.readAttribute(0x0006, 0x8004, [:], delay=50)                      // success / 0x00
    cmd += zigbee.readAttribute(0xE001, 0xD011, [:], delay=50)                      // Unsupported attribute (0x86)
    cmd += zigbee.readAttribute(0x0001, [0x0020, 0x0021], [:], delay=50)            // Battery voltage + Battery Percentage Remaining
    cmd += zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01, [:], delay=50)         // switch into Scene Mode !
    cmd += zigbee.readAttribute(0x0006, 0x8004, [:], delay=50)
    // binding for battery reporting IS neccessery!         // changed 2023/01/04
    cmd += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 600, 28800, 0x01, [:], delay=150)
    cmd += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 600, 28800, 0x01, [:], delay=150)        // 0x21 is NOT supported by all devices?
    sendZigbeeCommands(cmd)
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (logEnable) {log.trace "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    sendHubCommand(allActions)
}

void scheduleDeviceHealthCheck() {
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // test
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/3 * * ? *", 'deviceHealthCheck')
}

// called every 3 hours
def deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter > healthStatusCountTreshold) {
        if (!(device.currentValue('healthStatus', true) in ['offline'])) {
            setHealthStatusValue('offline')
            log.warn "${device.displayName} is offline!"
        }
    }
    else {
        if (logEnable) log.debug "${device.displayName} deviceHealthCheck - online (notPresentCounter=${state.notPresentCounter})"
    }
}

def setHealthStatusOnline() {
    state.notPresentCounter = 0
    if (!(device.currentValue('healthStatus', true) in ['online'])) {   
        setHealthStatusValue('online')
        log.info "${device.displayName} is online"
    }
}

def setHealthStatusValue(value) {
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

def ping() {
    if (logEnable) log.debug "ping() is not implemented"
}


def test(String description) {
    log.warn "test: ${description}"
    parse(description)
    
}
