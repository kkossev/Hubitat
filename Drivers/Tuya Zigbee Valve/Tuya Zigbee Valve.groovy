/**
 *  Tuya Zigbee Valve driver for Hubitat Elevation
 * 
 *  https://community.hubitat.com/t/alpha-tuya-zigbee-valve-driver/92788 
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
 *  ver. 1.0.0 2022-04-22 kkossev - inital version
 *  ver. 1.0.1 2022-04-23 kkossev - added Refresh command; [overwrite: true] explicit option for runIn calls; capability PowerSource
 *  ver. 1.0.2 2022-08-14 kkossev - added _TZE200_sh1btabb WaterIrrigationValve (On/Off only); fingerprint inClusters correction; battery capability; open/close commands changes
 *  ver. 1.0.3 2022-08-19 kkossev - decreased delay betwen Tuya commands to 200 milliseconds; irrigation valve open/close commands are sent 2 times; digital/physicla timer changed to 3 seconds;
 *  ver. 1.0.4 2022-11-28 kkossev - added Power-On Behaviour preference setting
 *  ver. 1.0.5 2023-01-21 kkossev - added _TZE200_81isopgh (SASWELL) battery, timer_state, timer_time_left, last_valve_open_duration, weather_delay; added _TZE200_2wg5qrjy _TZE200_htnnfasr (LIDL); 
 *  ver. 1.1.0 2023-01-29 kkossev - added healthStatus
 *  ver. 1.2.0 2023-02-26 kkossev - (dev. branch) added deviceProfiles; stats; Advanced Option to manually select device profile; dynamically generated fingerptints; added autOffTimer;
 *                                  added irrigationStartTime, irrigationEndTime, lastIrrigationDuration, waterConsumed
 *
 *            TODO Presence check timer
 *            TODO: timer; water_consumed; cycle_timer_1 
 *
 *
 */
import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

def version() { "1.2.0" }
def timeStamp() {"2023/02/26 11:44 PM"}

@Field static final Boolean _DEBUG = true

metadata {
    definition (name: "Tuya Zigbee Valve", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Valve/Tuya%20Zigbee%20Valve.groovy", singleThreaded: true ) {
        capability "Actuator"    
        capability "Valve"
        capability "Refresh"
        capability "Configuration"
        capability "PowerSource"
        capability "HealthCheck"
        capability "Battery"
        
        attribute "healthStatus", "enum", ["offline", "online"]
        attribute "timerState", "enum", [
            "disabled",
            "active (on)",
            "enabled (off)"
        ]
        attribute "timer_time_left", "number"
        attribute "last_valve_open_duration", "number"
        attribute "weather_delay", "enum", [
            "disabled",
            "24h",
            "48h",
            "72h"
        ]
        attribute "irrigationStartTime", "string"
        attribute "irrigationEndTime", "string"
        attribute "lastIrrigationDuration", "string"
        attribute "waterConsumed", "number"
        
        command "setIrrigationTimer", [[name:"timer", type: "NUMBER", description: "Set Irrigation Timer, seconds", constraints: ["0..86400"]]]
        
        if (_DEBUG == true) {        
            command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]
            command "testTuyaCmd", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
            ]
            command "test", [[name:"description", type: "STRING", description: "description", constraints: ["STRING"]]]
        }

        deviceProfilesV2.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each { 
                    fingerprint it
                }
            }
        }        
    }
    
    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "powerOnBehaviour", type: "enum", title: "<b>Power-On Behaviour</b>", description:"<i>Select Power-On Behaviour</i>", defaultValue: "2", options: powerOnBehaviourOptions)
        if (isSASWELL() || isWaterIrrigationValve()) {
       		input (name: "autoOffTimer", type: "number", title: "<b>Auto off timer</b>", description: "<i>Automatically turn off after how many seconds?</i>", defaultValue: DEFAULT_AUTOOFF_TIMER, required: false)
        }
        input (name: "advancedOptions", type: "bool", title: "<b>Advanced Options</b>", description: "<i>These options should have been set automatically by the driver<br>Manually changes may not always work!</i>", defaultValue: false)
        if (advancedOptions == true) {
            input (name: "forcedProfile", type: "enum", title: "<b>Device Profile</b>", description: "<i>Device Profile<br>Manually setting a device profile may not always work!</i>",  options: getDeviceProfiles())
        }
    }
}

@Field static final Map deviceProfilesV2 = [
    "TS0001_VALVE_ONOFF"  : [
            model         : "TS0001",
            manufacturers : ["_TZ3000_iedbgyxt",  "_TZ3000_o4cjetlm", "_TZ3000_oxslv1c9", "_TYZB01_4tlksk8a","_TZ3000_h3noz0a5"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0003,0004,0005,0006,E000,E001,0000", outClusters:"0019,000A",     model:"TS0001", manufacturer:"_TZ3000_iedbgyxt"],    // https://community.hubitat.com/t/generic-zigbee-3-0-valve-not-getting-fingerprint/92614
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,E000,E001", outClusters:"0019,000A",     model:"TS0001", manufacturer:"_TZ3000_o4cjetlm"],    // https://community.hubitat.com/t/water-shutoff-valve-that-works-with-hubitat/32454/59?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0006",                     outClusters:"0003,0006,0004",model:"TS0001", manufacturer:"_TYZB01_4tlksk8a"],    // clusters verified
                [profileId:"0104", endpointId:"01", inClusters:"0003,0004,0005,0006,E000,E001,0000", outClusters:"0019,000A",     model:"TS0001", manufacturer:"_TZ3000_h3noz0a5"],    // clusters verified
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,0006",                outClusters:"0019",          model:"TS0011", manufacturer:"_TYZB01_rifa0wlb"],    // https://community.hubitat.com/t/tuya-zigbee-water-gas-valve/78412 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0702,0B04", outClusters:"0019",          model:"TS0011", manufacturer:"_TYZB01_ymcdbl3u"]     // clusters verified
            ],
            deviceJoinName: "Tuya Zigbee Valve TS0001",
            capabilities  : ["valve": true, "battery": false],
            configuration : ["battery": false],
            attributes    : ["healthStatus"],
            preferences   : [
                "powerOnBehaviour" : [ name: "powerOnBehaviour", type: "enum", title: "<b>Power-On Behaviour</b>", description:"<i>Select Power-On Behaviour</i>", defaultValue: "2", options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],
    
    "TS0011_VALVE_ONOFF"  : [
            model         : "TS0011",
            manufacturers : ["_TYZB01_rifa0wlb",  "_TYZB01_ymcdbl3u"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,0006",                outClusters:"0019",          model:"TS0011", manufacturer:"_TYZB01_rifa0wlb"],     // https://community.hubitat.com/t/tuya-zigbee-water-gas-valve/78412 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0702,0B04", outClusters:"0019",          model:"TS0011", manufacturer:"_TYZB01_ymcdbl3u"]      // clusters verified
            ],
        
            deviceJoinName: "Tuya Zigbee Valve TS0011",
            capabilities  : ["valve": true, "battery": false],
            configuration : ["battery": false],
            attributes    : ["healthStatus"],
            preferences   : [
                "powerOnBehaviour" : [ name: "powerOnBehaviour", type: "enum", title: "<b>Power-On Behaviour</b>", description:"<i>Select Power-On Behaviour</i>", defaultValue: "2", options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],
            
    "TS011F_VALVE_ONOFF"  : [
            model         : "TS0011",
            manufacturers : ["_TZ3000_rk2yzt0u"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,E000,E001", outClusters:"0019,000A",     model:"TS011F", manufacturer:"_TZ3000_rk2yzt0u"]     // clusters verified! model: 'ZN231392'
            ],
            deviceJoinName: "Tuya Zigbee Valve TS011F",
            capabilities  : ["valve": true, "battery": false],
            configuration : ["battery": false],
            attributes    : ["healthStatus"],
            preferences   : [
                "powerOnBehaviour" : [ name: "powerOnBehaviour", type: "enum", title: "<b>Power-On Behaviour</b>", description:"<i>Select Power-On Behaviour</i>", defaultValue: "2", options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],
            
    "TS0601_VALVE_ONOFF"  : [
            model         : "TS0601",
            manufacturers : ["_TZE200_vrjkcam9"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",                outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_vrjkcam9"]     // https://community.hubitat.com/t/tuya-zigbee-water-gas-valve/78412?u=kkossev
            ],
            deviceJoinName: "Tuya Zigbee Valve TS0601",
            capabilities  : ["valve": true, "battery": false],
            configuration : ["battery": false],
            attributes    : ["healthStatus"],
            preferences   : [
                "powerOnBehaviour" : [ name: "powerOnBehaviour", type: "enum", title: "<b>Power-On Behaviour</b>", description:"<i>Select Power-On Behaviour</i>", defaultValue: "2", options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],
            
    "TS0601_IRRIGATION_VALVE"    : [         // https://www.aliexpress.com/item/1005004222098040.html
            model         : "TS0601",        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/21a66c05aa533de356a51c8417073f28092c6e9d/devices/giex.js 
            manufacturers : ["_TZE200_sh1btabb"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000",                outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_sh1btabb"]     // WaterIrrigationValve 
            ],
            deviceJoinName: "Tuya Zigbee Irrigation Valve",
            capabilities  : ["valve": true, "battery": true],
            configuration : ["battery": false],
            attributes    : ["healthStatus"],
            preferences   : [
                "powerOnBehaviour" : [ name: "powerOnBehaviour", type: "enum", title: "<b>Power-On Behaviour</b>", description:"<i>Select Power-On Behaviour</i>", defaultValue: "2", options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],

    "TS0601_SASWELL_VALVE"    : [
            model         : "TS0601",
            manufacturers : ["_TZE200_akjefhj5", "_TZE200_81isopgh", "_TZE200_2wg5qrjy", "_TZE200_htnnfasr"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0702,EF00", outClusters:"0019",          model:"TS0601", manufacturer:"_TZE200_akjefhj5"],     // SASWELL SAS980SWT-7-Z01 (_TZE200_akjefhj5, TS0601) https://github.com/zigpy/zha-device-handlers/discussions/1660 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0702,EF00", outClusters:"0019",          model:"TS0601", manufacturer:"_TZE200_81isopgh"],     // not tested // SASWELL SAS980SWT-7 Solenoid valve and watering programmer 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0702,EF00", outClusters:"0019",          model:"TS0601", manufacturer:"_TZE200_2wg5qrjy"],     // not tested // 
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0702,EF00", outClusters:"0019",          model:"TS0601", manufacturer:"_TZE200_htnnfasr"]      // not tested // // PARKSIDE® Smart Irrigation Computer //https://www.lidl.de/p/parkside-smarter-bewaesserungscomputer-zigbee-smart-home/p100325201
            ],
            deviceJoinName: "Saswell Zigbee Irrigation Valve",
            capabilities  : ["valve": true, "battery": true],
            configuration : ["battery": false],
            attributes    : ["healthStatus"],
            preferences   : [
                "powerOnBehaviour" : [ name: "powerOnBehaviour", type: "enum", title: "<b>Power-On Behaviour</b>", description:"<i>Select Power-On Behaviour</i>", defaultValue: "2", options:  ['0': 'closed', '1': 'open', '2': 'last state']] //,
            ]
    ],
    
    "UNKNOWN"      : [                // TODO: _TZE200_5uodvhgc https://github.com/sprut/Hub/issues/1316 https://www.youtube.com/watch?v=lpL6xAYuBHk 
        model         : "UNKNOWN",
        manufacturers : [],
        deviceJoinName: "Unknown device",
        capabilities  : ["valve": true],
        configuration : ["battery": true],
        attributes    : ["healthStatus"],
        batteries     : "unknown"
    ]
]    

// Constants
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3
@Field static final Integer DEFAULT_POLLING_INTERVAL = 15
@Field static final Integer DEFAULT_AUTOOFF_TIMER = 60
@Field static final Integer DEBOUNCING_TIMER = 300
@Field static final Integer DIGITAL_TIMER = 3000
@Field static final Integer REFRESH_TIMER = 3000
@Field static String UNKNOWN = "UNKNOWN"

@Field static final Map powerOnBehaviourOptions = [   
    '0': 'closed',
    '1': 'open',
    '2': 'last state'
]

@Field static final Map switchTypeOptions = [   
    '0': 'toggle',
    '1': 'state',
    '2': 'momentary'
]

@Field static final Map timerStateOptions = [   
    '0': 'disabled',
    '1': 'active (on)',
    '2': 'enabled (off)'
]

@Field static final Map weatherDelayOptions = [   
    '0': 'disabled',
    '1': '24h',
    '2': '48h',
    '3': '72h'
]

@Field static final Map batteryStateOptions = [   
    '0': 'low',
    '1': 'middle',
    '2': 'high'
]

@Field static final Map smartWeatherOptions = [   
    '0': 'sunny',
    '1': 'clear',
    '2': 'cloud',
    '3': 'cloudy',
    '4': 'rainy',
    '5': 'snow',
    '6': 'fog'
]

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

def getDeviceProfiles()      { deviceProfilesV2.keySet() }
def isConfigurable(model)    { return (deviceProfilesV2["$model"]?.preferences != null && deviceProfilesV2["$model"]?.preferences != []) }
def isConfigurable()         { def model = getModelGroup(); return isConfigurable(model) }
def isWaterIrrigationValve() { return getModelGroup().contains("IRRIGATION") }
def isSASWELL()              { return getModelGroup().contains("SASWELL") }
def isBatteryPowered()       { return isWaterIrrigationValve() || isSASWELL()}

def parse(String description) {
    checkDriverVersion()
    state.stats["RxCtr"] = (state.stats["RxCtr"] ?: 0) + 1
    setHealthStatusOnline()
    logDebug "parse: description is $description"
    
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

def switchEvent( switchValue ) {
    def value = (switchValue == null) ? 'unknown' : (switchValue == 'on') ? 'open' : (switchValue == 'off') ? 'closed' : 'unknown'
    def map = [:] 
    boolean bWasChange = false
    boolean debounce   = state.states["debounce"] ?: false
    def lastSwitch = state.states["lastSwitch"] ?: "unknown"
    if (debounce == true && value == lastSwitch) {    // some devices send only catchall events, some only readattr reports, but some will fire both...
        if (logEnable) {log.debug "${device.displayName} Ignored duplicated switch event for model ${getModelGroup()}"} 
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
        return null
    }
    else {
        //log.trace "value=${value}  lastSwitch=${state.states['lastSwitch']}"
    }
    def isDigital = state.states["isDigital"]
    map.type = isDigital == true ? "digital" : "physical"
    if (lastSwitch != value ) {
        bWasChange = true
        if (logEnable) {log.debug "${device.displayName} Valve state changed from <b>${lastSwitch}</b> to <b>${value}</b>"}
        state.states["debounce"]   = true
        state.states["lastSwitch"] = value
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])        
    }
    else {
        state.states["debounce"] = true
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])     
    }
        
    map.name = "valve"
    map.value = value
    boolean isRefresh = state.states["isRefresh"] ?: false
    if (isRefresh == true) {
        map.descriptionText = "${device.displayName} is ${value} (Refresh)"
    }
    else {
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]"
    }
    //if ( bWasChange==true ) 
    //{
        if (txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
        sendEvent(map)
    //}
    clearIsDigital()
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
        case "02" : // version 1.0.2 
            def status = descMap.data[2]
            def attrId = descMap.data[1] + descMap.data[0] 
            if (status == "86") {
                if (logEnable==true) log.warn "${device.displayName} Read attribute response: unsupported Attributte ${attrId} cluster ${descMap.clusterId}  descMap = ${descMap}"
            }
            else {
                switch (descMap.clusterId) {
                    case "EF00" :
                        //if (logEnable==true) log.debug "${device.displayName} Tuya cluster read attribute response: code ${status} Attributte ${attrId} cluster ${descMap.clusterId} data ${descMap.data}"
                        def cmd = descMap.data[2]
                        def value = getAttributeValue(descMap.data)
                        if (logEnable==true) log.trace "${device.displayName} Tuya cluster cmd=${cmd} value=${value} ()"
                        def map = [:]
                        switch (cmd) {
                            case "01" : // switch
                                if (!isWaterIrrigationValve()) {
                                    switchEvent(value==0 ? "off" : "on")    // also SASWELL?
                                    // There is no way to disable the "Auto off" timer for when the valve is turned on manually
                                    // https://github.com/Koenkk/zigbee2mqtt/issues/13199#issuecomment-1239914073 
                                }
                                else {    // WaterMode  for _TZE200_sh1btabb : duration=0 / capacity=1
                                    if (txtEnable==true) log.info "${device.displayName} Water Valve Mode (dp=${cmd}) is: ${value}"  // 0 - 'duration'; 1 - 'capacity'     // TODO - Send to device ?
                                }
                                break
                            case "02" : // isWaterIrrigationValve() - WaterValveState   1=on 0 = 0ff        // _TZE200_sh1btabb WaterState # off=0 / on=1
                                def timerState = timerStateOptions[value.toString()]
                                logInfo "Water Valve State (dp=${cmd}) is ${timerState} (${value})"
                                switchEvent(value==0 ? "off" : "on")
                                sendEvent(name: 'timerState', value: timerState, type: "physical")
                                break
                            case "03" : // flow_state or percent_state?  (0..100%) SASWELL ?
                                if (txtEnable==true) log.info "${device.displayName} flow_state (${cmd}) is: ${value} %"
                                break                                
                            case "04" : // failure_to_report
                                if (txtEnable==true) log.info "${device.displayName} failure_to_report (${cmd}) is: ${value}"
                                break                                
                            case "05" : // isSASWELL() - measuredValue ( water_once, or last irrigation volume ) ( 0..1000, divisor:10, unit: 'L')
                                // assuming value is reported in fl. oz. ? => { water_consumed: (value / 33.8140226).toFixed(2) }
                                if (txtEnable==true) log.info "${device.displayName} SASWELL measuredValue (dp=${cmd}) is: ${value} (data=${descMap.data})"
                                break
                            case "07" : // Battery for SASWELL (0..100%), Countdown for the others?
                                if (isSASWELL()) {
                                    if (txtEnable==true) log.info "${device.displayName} battery (${cmd}) is: ${value} %"
                                    sendBatteryEvent(value)                                    
                                }
                                else {
                                    if (txtEnable==true) log.info "${device.displayName} Countdown (${cmd}) is: ${value}"
                                }
                                break
                            case "08" : // battery_state batteryStateOptions
                                def valueString = batteryStateOptions[safeToInt(value).toString()]
                                if (txtEnable==true) log.info "${device.displayName} battery_state (${cmd}) is: ${valueString} (${value})"
                                break                                
                            case "09" : // accumulated_usage_time (0..2592000, seconds)
                                if (txtEnable==true) log.info "${device.displayName} accumulated_usage_time (${cmd}) is: ${value} seconds"
                                break                                
                            case "0A" : // (10) weather_delay //   0 -> disabled; 1 -> "24h"; 2 -> "48h";  3 -> "72h"
                                def valueString = weatherDelayOptions[safeToInt(value).toString()]
                                if (txtEnable==true) log.info "${device.displayName} weather_delay (${cmd}) is: ${valueString} (${value})"
                                sendEvent(name: 'weather_delay', value: valueString, type: "physical")
                                break
                            case "0B" : // (11) SASWELL countdown timeLeft in seconds timer_time_left "irrigation_time" (0..86400, seconds)
                                if (txtEnable==true) log.info "${device.displayName} timer time left (${cmd}) is: ${value} seconds"
                                sendEvent(name: 'timer_time_left', value: value, type: "physical")
                                break
                            case "0C" : // (12) SASWELL ("work_state") state 0-disabled 1-active on (open) 2-enabled off (closed) ? or auto/manual/idle ?
                                def valueString = timerStateOptions[safeToInt(value).toString()]
                                if (txtEnable==true) log.info "${device.displayName} timer_state (work state) (${cmd}) is: ${valueString} (${value})"
                                sendEvent(name: 'timer_state', value: valueString, type: "physical")
                                break
                            case "0D" : // (13) "smart_weather" for SASWELL or relay status for others?
                                if (isSASWELL()) {
                                    def valueString = smartWeatherOptions[safeToInt(value).toString()]
                                    if (txtEnable==true) log.info "${device.displayName} smart_weather (${cmd}) is: ${valueString} (${value})"
                                }
                                else {
                                    if (txtEnable==true) log.info "${device.displayName} relay status (${cmd}) is: ${value}"
                                }
                                break
                            case "0E" : // (14) SASWELL "smart_weather_switch"
                                if (txtEnable==true) log.info "${device.displayName} smart_weather_switch (${cmd}) is: ${value}"
                                break
                            case "0F" : // (15) SASWELL lastValveOpenDuration in seconds last_valve_open_duration (once_using_time, last irrigation duration) (0..86400, seconds)
                                if (txtEnable==true) log.info "${device.displayName} last valve open duration (${cmd}) is: ${value} seconds"
                                sendEvent(name: 'last_valve_open_duration', value: value, type: "physical")
                                break
                            case "10" : // (16) SASWELL RawToCycleTimer1 ?     ("cycle_irrigation")
                                // https://github.com/Koenkk/zigbee2mqtt/issues/13199#issuecomment-1205015123 
                                if (txtEnable==true) log.info "${device.displayName} SASWELL RawToCycleTimer1 (${cmd}) is: ${value}"
                                break
                            case "11" : // (17) SASWELL RawToCycleTimer2 ?     ("normal_timer")
                                if (txtEnable==true) log.info "${device.displayName} SASWELL RawToCycleTimer2 (${cmd}) is: ${value}"
                                break
                            case "13" : // (19) inching switch ( once enabled, each time the device is turned on, it will automatically turn off after a period time as preset
                                if (txtEnable==true) log.info "${device.displayName} inching switch(!?!) is: ${value}"
                                break
                            case "65" : // (101) WaterValveIrrigationStartTime     // IrrigationStartTime       # (string) ex: "08:12:26"
                                def str = getAttributeString(descMap.data)
                                logInfo "IrrigationStartTime (${cmd}) is: ${str}"
                                sendEvent(name: 'irrigationStartTime', value: str, type: "physical")
                                break
                            case "66" : // (102) WaterValveIrrigationEndTime      // IrrigationStopTime        # (string) ex: "08:13:36"
                                def str = getAttributeString(descMap.data)
                                logInfo "IrrigationEndTime (${cmd}) is: ${str}"
                                sendEvent(name: 'irrigationEndTime', value: str, type: "physical")
                                break
                            case "67" : // (103) WaterValveCycleIrrigationNumTimes          // CycleIrrigationNumTimes   # number of cycle irrigation times, set to 0 for single cycle        // TODO - Send to device cycle_irrigation_num_times ?
                                if (txtEnable==true) log.info "${device.displayName} CycleIrrigationNumTimes (${cmd}) is: ${value}"
                                break
                            case "68" : // (104) WaterValveIrrigationTarget                // IrrigationTarget          # duration in minutes or capacity in Liters (depending on mode)
                                if (txtEnable==true) log.info "${device.displayName} IrrigationTarget (${cmd}) is: ${value}"            // TODO - Send to device irrigation_target?
                                break
                            case "69" : // (105) WaterValveCycleIrrigationInterval        // CycleIrrigationInterval   # cycle irrigation interval (minutes, max 1440)                        // TODO - Send to device cycle_irrigation_interval ?
                                if (txtEnable==true) log.info "${device.displayName} CycleIrrigationInterval (${cmd}) is: ${value}"
                                break
                            case "6A" : // (106) WaterValveCurrentTempurature            // CurrentTemperature        # (value ignored because isn't a valid tempurature reading.  Misdocumented and usage unclear)
                                if (txtEnable==true) log.info "${device.displayName} ?CurrentTempurature? (${cmd}) is: ${value}"        // ignore!
                                break
                            case "6C" : // (108) WaterValveBattery - _TZE200_sh1btabb    // 0001/0021,mul:2           # match to BatteryPercentage
                                if (txtEnable==true) log.info "${device.displayName} Battery (${cmd}) is: ${value}"
                                sendBatteryEvent(value)
                                break
                            case "6F" : // (111) WaterValveWaterConsumed                // WaterConsumed             # water consumed (Litres)
                                if (txtEnable==true) log.info "${device.displayName} WaterConsumed (${cmd}) is: ${value} (Litres)"
                                sendEvent(name: 'waterConsumed', value: value, type: "physical")
                                break
                            case "72" : // (114) WaterValveLastIrrigationDuration    LastIrrigationDuration    # (string) Ex: "00:01:10,0"
                                def str = getAttributeString(descMap.data)
                                if (txtEnable==true) log.info "${device.displayName} LastIrrigationDuration (${cmd}) is: ${value}"
                                sendEvent(name: 'lastIrrigationDuration', value: str, type: "physical")
                                break
                            case "D1" : // cycle timer
                                if (txtEnable==true) log.info "${device.displayName} cycle timer (${cmd}) is: ${value}"
                                break
                            case "D2" : // random timer
                                if (txtEnable==true) log.info "${device.displayName} cycle timer (${cmd}) is: ${value}"
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
        
        case "04" : //write attribute response
            logDebug "parseZHAcommand writeAttributeResponse cluster: ${descMap.clusterId} status:${descMap.data[0]}"
            break
        case "07" : // Configure Reporting Response
            logDebug "Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            // Status: Unreportable Attribute (0x8c)
            break
        case "0B" : // ZCL Default Response
            def status = descMap.data[1]
            if (status != "00") {
                switch (descMap.clusterId) {
                    case "0006" : // Switch state
                        if (logEnable==true) log.warn "${device.displayName} standard ZCL Switch state is not supported."
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

private String getAttributeString(ArrayList _data) {
    String retValue = ""
    try {    
        if (_data.size() >= 6) {
            for (int i=6; i< _data.size(); i++) {
                retValue = retValue + (zigbee.convertHexToInt(_data[i]) as char)
            }            
        }
    }
    catch(e) {
        log.error "${device.displayName} Exception caught : data = ${_data}"
    }
    return retValue
}

def close() {
    state.states["isDigital"] = true
    if (logEnable) {log.debug "${device.displayName} closing"}
    //def cmds
    ArrayList<String> cmds = []
    if (isWaterIrrigationValve()) {
        Short paramVal = 0
        def dpValHex = zigbee.convertToHexString(paramVal as int, 2)
        cmds = sendTuyaCommand("02", DP_TYPE_BOOL, dpValHex)
        cmds += sendTuyaCommand("02", DP_TYPE_BOOL, dpValHex)
        if (logEnable) log.debug "${device.displayName} closing WaterIrrigationValve cmds = ${cmds}"       
    }
    else if (getModelGroup().contains("TS0601")) {
        cmds = sendTuyaCommand("01", DP_TYPE_BOOL, "00")
    }
    else {
        cmds = zigbee.off()    // for all models that support the standard Zigbee OnOff cluster   
    }
    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    sendZigbeeCommands( cmds )
}

def open() {
    state.states["isDigital"] = true
    if (logEnable) {log.debug "${device.displayName} opening"}
    ArrayList<String> cmds = []
    if (isWaterIrrigationValve()) {
        Short paramVal = 1
        def dpValHex = zigbee.convertToHexString(paramVal as int, 2)
        cmds = sendTuyaCommand("02", DP_TYPE_BOOL, dpValHex)
        cmds += sendTuyaCommand("02", DP_TYPE_BOOL, dpValHex)
        if (logEnable) log.debug "${device.displayName} opening WaterIrrigationValve cmds = ${cmds}"       
    }
    else if (getModelGroup().contains("TS0601")) {
        cmds = sendTuyaCommand("01", DP_TYPE_BOOL, "01")
    }
    else {
        cmds =  zigbee.on()
    }
    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    if (isSASWELL() || isWaterIrrigationValve()) {
        logDebug "scheduled to set the autoOff timer to ${settings?.autoOffTimer} after 5 seconds"
        runIn( 5, "sendAutoOffTimer")
    }
    sendZigbeeCommands( cmds )
}

def sendAutoOffTimer() {
   ArrayList<String> cmds = []
   String autoOffTime = "00010B020004" + zigbee.convertToHexString((settings?.autoOffTimer) as Integer, 8)
   cmds = zigbee.command(0xEF00, 0x0, autoOffTime)
   logDebug "sendAutoOffTimer= ${settings?.autoOffTimer} : ${cmds}"
   sendZigbeeCommands(cmds) 
}

def sendBatteryEvent( roundedPct, isDigital=false ) {
    sendEvent(name: 'battery', value: roundedPct, unit: "%", type:  isDigital == true ? "digital" : "physical", isStateChange: true )
    if (isDigital==false) {
        state.states["lastBattery"] = roundedPct.toString()
    }
}


def clearIsDigital() { state.states["isDigital"] = false }
def switchDebouncingClear() { state.states["debounce"] = false }
def isRefreshRequestClear() { state.states["isRefresh"] = false }


// * PING is used by Device-Watch in attempt to reach the Device
def ping() {
    return refresh()
}

// Sends refresh / readAttribute commands to the device
def poll() {
    if (logEnable) {log.trace "${device.displayName} polling.."}
    checkDriverVersion()
    List<String> cmds = []
    state.states["isRefresh"] = true
    if (device.getDataValue("model") != 'TS0601') {
        cmds = zigbee.onOffRefresh()
    }
    if (deviceProfilesV2[getModelGroup()]?.capabilities?.battery?.value == true) {
        cmds += zigbee.readAttribute(0x001, 0x0020, [:], delay = 100)
        cmds += zigbee.readAttribute(0x001, 0x0021, [:], delay = 200)
    }
    if (isSASWELL() || isWaterIrrigationValve()) {
        cmds += zigbee.command(0xEF00, 0x0, "00020100")
    }
    runInMillis( REFRESH_TIMER, isRefreshRequestClear, [overwrite: true])           // 3 seconds
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }

}


def refresh() {
    if (logEnable) {log.debug "${device.displayName} sending refresh() command..."}
    poll()
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
    List<String> cmds = []
    cmds += tuyaBlackMagic()
    cmds += refresh()
    cmds += zigbee.onOffConfig()    // TODO - skip for TS0601 device types !
    
    if (settings?.forcedProfile != null) {
        if (settings?.forcedProfile != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${settings?.forcedProfile}"
            state.deviceProfile = settings?.forcedProfile
            logInfo "press F5 to refresh the page"
        }
    }
    
    if (settings?.powerOnBehaviour != null) {
        def modeName =  powerOnBehaviourOptions.find{it.key==settings?.powerOnBehaviour}
        if (modeName != null) {
            // TODO - skip it for the battery powered irrigation timers? (Response cluster: E001 status:86)
            logDebug "setting powerOnBehaviour to ${modeName.value} (${settings?.powerOnBehaviour})"
            cmds += zigbee.writeAttribute(0xE001, 0xD010, DataType.ENUM8, (byte) safeToInt(settings?.powerOnBehaviour), [:], delay=251)
            //cmds += zigbee.readAttribute(0xE001, 0xD010, [:], delay=101)
        }
    }
    
    if (deviceProfilesV2[getModelGroup()]?.configuration?.battery?.value == true) {
        // TODO - configure battery reporting
        logDebug "settings.batteryReporting = ${settings?.batteryReporting}"
    }
    sendZigbeeCommands(cmds)
}

def getModelGroup() {
    return state.deviceProfile ?: "UNKNOWN"
}

// called from  initializeVars( fullInit = true)
void setDeviceName() {
    String deviceName
    def currentModelMap = null
    def deviceModel = device.getDataValue('model')
    def deviceManufacturer = device.getDataValue('manufacturer')
    deviceProfilesV2.each { profileName, profileMap ->
        if ((profileMap.model?.value as String) == (deviceModel as String)) {
            if ((profileMap.manufacturers.value as String).contains(deviceManufacturer as String))
            {
                currentModelMap = profileName
                state.deviceProfile = currentModelMap
                deviceName = deviceProfilesV2[currentModelMap].deviceJoinName
                //log.debug "FOUND! currentModelMap=${currentModelMap}, deviceName =${deviceName}"
            }
        }
    }

    if (currentModelMap == null) {
        logWarn "unknown model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')}"
        // don't change the device name when unknown
        state.deviceProfile = 'UNKNOWN'
    }
    if (deviceName != NULL) {
        device.setName(deviceName)
        logInfo "device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} deviceName was set to ${deviceName}"
    } else {
        logWarn "device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} was not found!"
    }
}


// This method is called when the preferences of a device are updated.
def updated(){
    checkDriverVersion()
    if (txtEnable==true) log.info "Updating ${device.getLabel()} (${device.getName()}) model ${getModelGroup()} "
    if (txtEnable==true) log.info "Debug logging is <b>${logEnable}</b> Description text logging is  <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(/*1800*/86400, logsOff, [overwrite: true])    // turn off debug logging after /*30 minutes*/24 hours
        if (txtEnable==true) log.info "Debug logging will be automatically switched off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    configure()
}

def resetStats() {
    state.stats = [:]
    state.states = [:]
    state.stats["RxCtr"] = 0
    state.stats["TxCtr"] = 0
    state.states["isDigital"] = false
    state.states["isRefresh"] = false
    state.states["debounce"] = false
    state.states["lastSwitch"] = "unknown"
    if (isBatteryPowered()) { state.states["lastBattery"] = "100" }
    state.states["notPresentCtr"] = 0
}


void initializeVars( boolean fullInit = true ) {
    logInfo "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        unschedule()
        resetStats()
        setDeviceName()
        state.comment = 'Works with Tuya TS0001 TS0011 TS011F shutoff valves; Tuya TS0601 & Saswell irrigation valves'
        logInfo "all states and scheduled jobs cleared!"
        state.driverVersion = driverVersionAndTimeStamp()    
    }
    
    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (fullInit == true || state.states["lastSwitch"] == null) state.states["lastSwitch"] = "unknown"
    if (fullInit == true || state.states["notPresentCtr"] == null) state.states["notPresentCtr"]  = 0
    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", true)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings?.powerOnBehaviour == null) device.updateSetting("powerOnBehaviour", [value:"2", type:"enum"])    // last state
    if (fullInit == true || settings?.switchType == null) device.updateSetting("switchType", [value:"0", type:"enum"])                // toggle
    if (fullInit == true || settings?.advancedOptions == null) device.updateSetting("advancedOptions", [value:false, type:"bool"])                // toggle
    if (fullInit == true || settings?.autoOffTimer == null) device.updateSetting("autoOffTimer", [value: DEFAULT_AUTOOFF_TIMER, type: "number"])
    
    if (isBatteryPowered()) {
        if (state.states["lastBattery"] == null) state.states["lastBattery"] = "100"
    }
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown')    

    def mm = device.getDataValue("model")
    if ( mm != null) {
        if (logEnable==true) log.trace " model = ${mm}"
    }
    else {
        if (txtEnable==true) log.warn " Model not found, please re-pair the device!"
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
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        scheduleDeviceHealthCheck()
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

def logInitializeRezults() {
    if (logEnable==true) log.info "${device.displayName} Initialization finished"
}

// NOT called when the driver is initialized as a new device, because the Initialize capability is NOT declared!
def initialize() {
    log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars(fullInit = true)
    updated()            // calls also configure()
    runIn(3, logInitializeRezults, [overwrite: true])
}

// This method is called when the device is first created.
def installed() {
    if (txtEnable==true) log.info "${device.displayName} Installed()..."
    initializeVars()
    runIn( 5, initialize, [overwrite: true])
    if (logEnable==true) log.debug "calling initialize() after 5 seconds..."
    // HE will autoomaticall call configure() method here
}

void uninstalled() {
    if (logEnable==true) log.info "${device.displayName} Uninstalled()..."
    unschedule()     //Unschedule any existing schedules
}

void scheduleDeviceHealthCheck() {
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // for quick test
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/3 * * ? *", 'deviceHealthCheck')
}

// called when any event was received from the Zigbee device in parse() method..
def setHealthStatusOnline() {
    state.states["notPresentCtr"]  = 0
    if (!((device.currentValue('healthStatus', true) ?: "unknown") in ['online'])) {   
        sendHealthStatusEvent('online')
        if (isBatteryPowered()) {
        	sendEvent(name: "powerSource", value: "battery", type: "digital") 
        }
        else {
        	sendEvent(name: "powerSource", value: "dc", type: "digital") 
        }
        logInfo "is online"
    }
}

def deviceHealthCheck() {
    def ctr = state.states["notPresentCtr"] ?: 0
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue("healthStatus", true) ?: "unknown") != "offline" ) {
            sendHealthStatusEvent("offline")
            if (logEnable==true) log.warn "${device.displayName} not present!"
    	    sendEvent(name: "powerSource", value: "unknown", type: "digital")
            if (isBatteryPowered()) {
                if (safeToInt(device.currentValue('battery', true)) != 0) {
                    logWarn "${device.displayName} forced battery to '<b>0 %</b>"
                    sendBatteryEvent( 0, isDigital=true )
                }
            }
        }
    }
    else {
        logDebug "${device.displayName} deviceHealthCheck - online (notPresentCounter=${ctr})"
    }
    state.states["notPresentCtr"] = ctr + 1
}

def sendHealthStatusEvent(value) {
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}


private getPACKET_ID() {
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    //cmds += zigbee.command(CLUSTER_TUYA, SETDATA, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay=200, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "${device.displayName} sendTuyaCommand = ${cmds}"
    state.stats["TxCtr"] = state.stats["TxCtr"] != null ? state.stats["TxCtr"] + 1 : 1
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.debug "${device.displayName} <b>sendZigbeeCommands</b> (cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            state.stats["TxCtr"] = state.stats["TxCtr"] != null ? state.stats["TxCtr"] + 1 : 1
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
    logDebug "Tuya cluster: E000 or E001 - try to parse it..."
    def descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch ( e ) {
        logWarn "<b>exception</b> caught while parsing description:  ${description}"
        logDebug "TuyaE00xCluster Desc Map: ${descMap}"
        // cluster E001 is the one that is generating exceptions...
        return true
    }
    if (descMap.cluster == "E001" && descMap.attrId == "D010") {
        
        logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})"
    }
    else if (descMap.cluster == "E001" && descMap.attrId == "D030") {
        logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})"
    }
    else {
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap"
    }
    
    
    //
    return true
}

boolean otherTuyaOddities( String description )
{
    return false    // !!!!!!!!!!!
    if(description.indexOf('cluster: 0000') >= 0 || description.indexOf('attrId: 0004') >= 0) {
        if (logEnable) log.debug " other Tuya oddities - don't know how to handle it, skipping it for now..."
        return true
    }
    else
        return false
}

Integer safeToInt(val, Integer defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
	return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

def logDebug(msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

def logInfo(msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

def logWarn(msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}
def setIrrigationTimer( timer ) {
    ArrayList<String> cmds = []
    def timerSec = safeToInt(timer, -1)
    if (timerSec < 0 || timerSec > 86400) {
        logWarn "timer must be withing 0 and 86400 seconds"
        return
    }
    logDebug "setting the irrigation timer to ${timerSec} seconds"
    device.updateSetting("autoOffTimer", [value: timerSec, type: "number"])
    
    runIn( 1, "sendAutoOffTimer")
    /*   
    def dpValHex = zigbee.convertToHexString(timerSec as int, 8)
    cmds = sendTuyaCommand("0B", DP_TYPE_VALUE, dpValHex)
    sendZigbeeCommands( cmds )
    */
}


def testTuyaCmd( dpCommand, dpValue, dpTypeString ) {
    ArrayList<String> cmds = []
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    log.warn " sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}    
 

def test( description ) {
    
    log.warn "testing <b>${description}</b>"
    parse(description)
    
}
