/**
 *  Tuya Smart Siren Zigbee driver for Hubitat
 *
 *  https://community.hubitat.com/t/tuya-smart-siren-zigbee-driver-doesnt-work/73624/19
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
 * ver. 1.0.0 2022-04-02 kkossev  - First published version
 * ver. 1.1.0 2022-11-05 kkossev  - (dev branch) alarm events are registered upon confirmation from the device only; added switch capability; added Tone capability (beep command); combined Tuya commands; default settings are restored after the beep command
 *                                 added capability 'Chime'; setVolume; volumeUp, volumeDown; playSound; beepVolume; playSoundVolume; playSoundDuration
 *    TODO: preferences for the beep() command
 *
*/

def version() { "1.1.0" }
def timeStamp() {"2022/11/05 5:17 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

@Field static final Boolean debug = false
 
metadata {
    definition (name: "Tuya Smart Siren Zigbee", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Smart%20Siren%20Zigbee/Tuya%20Smart%20Siren%20Zigbee.groovy", singleThreaded: true ) {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "Switch"
        capability "Alarm"    // alarm - ENUM ["strobe", "off", "both", "siren"]; Commands: both() off() siren() strobe()
        capability "Tone"     // Commands: beep()
        capability "Chime"    // soundEffects - JSON_OBJECT; soundName - STRING; status - ENUM ["playing", "stopped"]; Commands: playSound(soundnumber); soundnumber required (NUMBER) - Sound number to play; stop()
        capability "AudioVolume" //Attributes: mute - ENUM ["unmuted", "muted"] volume - NUMBER, unit:%; Commands: mute() setVolume(volumelevel) volumelevel required (NUMBER) - Volume level (0 to 100) unmute() volumeDown() volumeUp()
        
        attribute "duration", "number"
        
        command "setAlarmMelody", [[name:"Set alarm melody type", type: "ENUM", description: "set alarm type", constraints: melodiesOptions]]
        command "setAlarmDuration", [[name:"Length", type: "NUMBER", description: "0..180 = set alarm length in seconds. 0 = no audible alarm"]]
        command "setAlarmVolume", [[name:"Volume", type: "ENUM", description: "set alarm volume", constraints: volumeOptions ]]
        command "playSound", [
            [name:"soundNumber", type: "NUMBER", description: "Melody number, 1..18", isRequired: true],
            [name:"volumeLevel", type: "NUMBER", description: "sound volume level, 0..100 % "],
            [name:"duration", type: "NUMBER", description: "duration is seconds"]
        ]
        if (debug==true) {
            command "test"
        }
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_t1blo2bj", deviceJoinName: "Tuya NEO Smart Siren"          // vendor: 'Neo', model: 'NAS-AB02B2'
        // https://github.com/zigpy/zha-device-handlers/issues/1379#issuecomment-1077772021 
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display sensor states in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "beepVolume", type: "enum", title: "<b>Beep Volume</b>", description:"<i>Select Beep Volume</i>", defaultValue: "low", options: volumeOptions)
        input (name: "playSoundVolume", type: "enum", title: "<b>Play Sound (Chime) command default volume</b>", description:"<i>Select playSound default Volume</i>", defaultValue: "medium", options: volumeOptions)
        input (name: "playSoundDuration", type: "number", title: "<b>Play Sound Duration</b>, seconds", description: "<i>The duration of the PlaySound command in seconds</i>", range: "1..180", defaultValue: 10)
    }
}

@Field static final List<String> volumeOptions = [
   // '---select---',
//    'muted',
    'low',
    'medium',
    'high'
]

@Field static final LinkedHashMap volumeMapping = [
//    'muted'    : [ volume: '0',   tuya: '-'],
    'low'      : [ volume: '33',  tuya: '0'],
    'medium'   : [ volume: '66',  tuya: '1'],
    'high'     : [ volume: '100', tuya: '2']
]// as ConfigObject


@Field static final List<String> melodiesOptions = [
    '1=Doorbell',
    '2=Classical song 1',
    '3=Classical song 2',
    '4=Classical song 3',
    '5=Classical song 4',
    '6=Classical song 5',
    '7=Alarm 1',
    '8=Alarm 2',
    '9=Alarm 3',
    '10=Alarm 4',
    '11=Barking dog',
    '12=Alarm',
    '13=Chime',
    '14=Telephone',
    '15=Siren',
    '16=Evacuation bell',
    '17=Clock alarm',
    '18=Fire alarm'
] //as String[]

private findVolumeByTuyaValue( fncmd ) {
    def volumeName = 'unknown'
    def volumePct = -1
    volumeMapping.each{ k, v -> 
        //log.warn "${k}:${v}    v.tuya.value=${v.tuya.value} fncmd=${fncmd.toString()}" 
        if (v.tuya.value as String == fncmd.toString()) {
            //log.warn "found volumeName = ${k} percent = ${v.volume}"
            volumeName = k
            volumePct = v.volume
        }
    }
    return [volumeName, volumePct]
}

private findVolumeByPct( pct ) {
    def volumeName = 'unknown'
    def volumeTuya = -1
    volumeMapping.each{ k, v -> 
        //log.warn "${k}:${v}    v.volume.value=${v.volume.value} fncmd=${pct.toString()}" 
        if (v.volume.value as String == pct.toString()) {
            //log.warn "found volumeName = ${k} percent = ${v.volume}"
            volumeName = k
            volumeTuya = v.tuya
        }
    }
    return [volumeName, volumeTuya]
}

private findVolumeByName( name ) {
    def volumeTuya = -1
    def volumePct = -1
    volumeMapping.each{ k, v -> 
        //log.warn "${k}:${v}    v.tuya.value=${v.tuya.value} name=${name}" 
        if (k as String == name as String) {
            //log.warn "found volumeName = ${k}, tuya=${v.tuya} percent = ${v.volume}"
            volumeTuya = v.tuya
            volumePct = v.volume
        }
    }
    return [volumeTuya, volumePct]
}


// Constants
@Field static final Integer TUYA_DP_VOLUME     = 5
@Field static final Integer TUYA_DP_DURATION   = 7
@Field static final Integer TUYA_DP_ALARM      = 13
@Field static final Integer TUYA_DP_BATTERY    = 15
@Field static final Integer TUYA_DP_MELODY     = 21

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
                getBatteryResult(Integer.parseInt(descMap.value, 16))
            }
            else {
                if (settings?.logEnable) log.warn "unparesed attrint $descMap.attrInt"
            }
        }     
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster( descMap )
        } 
        else if (descMap?.clusterId == "0013") {    // device announcement, profileId:0000
            if (settings?.logEnable) log.debug "${device.displayName} device announcement"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0001") {
            if (settings?.logEnable) log.debug "${device.displayName} application version is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFDF") {
            if (settings?.logEnable) log.debug "${device.displayName} Tuya check-in"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFE2") {
            if (settings?.logEnable) log.debug "${device.displayName} Tuya AppVersion is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFE4") {
            if (settings?.logEnable) log.debug "${device.displayName} Tuya UNKNOWN attribute FFE4 value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFFE") {
            if (settings?.logEnable) log.debug "${device.displayName} Tuya UNKNOWN attribute FFFE value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000") {
            if (settings?.logEnable) log.debug "${device.displayName} basic cluster report  : descMap = ${descMap}"
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
    else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02"))
    {
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
        def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
        def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
        //if (settings?.logEnable) log.trace "${device.displayName}  dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
        switch (dp) {
            case TUYA_DP_VOLUME :    // (05) volume [ENUM] 0:low 1: mid 2:high
                def volumeName = 'unknown'
                def volumePct = -1
                (volumeName, volumePct) = findVolumeByTuyaValue( fncmd )
                if (volumeName != 'unknown') {
                    if (settings?.txtEnable) log.debug "${device.displayName} volume received is ${volumeName} ${volumePct}% (${fncmd})"
                    sendVolumeEvent( volumePct )
                }
                break
            case TUYA_DP_DURATION :  // (07) duration [VALUE] in seconds
                if (settings?.txtEnable) log.info "${device.displayName} duration is ${fncmd} s"
                sendEvent(name: "duration", value: fncmd, descriptionText: descriptionText )            
                break
            case TUYA_DP_ALARM :    // (13) alarm [BOOL]
                def value = fncmd == 0 ? "off" : fncmd == 1 ? state.lastCommand : "unknown"
                if (settings?.logEnable) log.info "${device.displayName} alarm state received is ${value} (${fncmd})"
                if (value == "off") {
                    sendEvent(name: "status", value: "stopped")      
                     if (device.currentValue("alarm", true) in ["beep", "playSound"]) {
                        runIn( 7, restoreDefaultSettings, [overwrite: true])
                        //restoreDefaultSettings()
                     }
                }
                else {
                   unschedule(restoreDefaultSettings)
                   sendEvent(name: "status", value: "playing")
                }
                sendAlarmEvent(value)
                break
            case TUYA_DP_BATTERY :    // (15) battery [VALUE] percentage
                getBatteryPercentageResult( fncmd * 2)
                break
            case TUYA_DP_MELODY :     // (21) melody [enum] 0..17
                if (settings?.txtEnable) log.info "${device.displayName} melody is ${melodiesOptions[fncmd]} (${fncmd})"
                sendEvent(name: "soundName", value: melodiesOptions[fncmd], descriptionText: descriptionText )            
                break
            default :
                if (settings?.logEnable) log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
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


def off() {
    sendTuyaAlarm("off")
}

def on() {
    sendTuyaAlarm("on")
}

def both() {
    sendTuyaAlarm("both")
}

def strobe() {
    sendTuyaAlarm("strobe")
}

def siren() {
    sendTuyaAlarm( "siren")
}

def sendTuyaAlarm( commandName ) {
    wakeUpTuya()
    String cmds = ""
    if (settings?.logEnable) log.debug "${device.displayName} swithing alarm ${commandName}()"
    state.lastCommand = commandName
    if (commandName != "off") {
        //log.warn "state.setVolume = ${state.setVolume}"
        // volume
        def volumeName; def volumeTuya; 
        (volumeName, volumeTuya) = findVolumeByPct( state.setVolume )
        if (volumeTuya >= 0 ) {
            cmds += appendTuyaCommand( TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya) ) 
        }
        // duration
        cmds += appendTuyaCommand( TUYA_DP_DURATION, DP_TYPE_VALUE, safeToInt(state.setDuration) ) 
        // melody
        def melodyNumber = safeToInt(melodiesOptions.indexOf(state.setMelody))
        cmds += appendTuyaCommand( TUYA_DP_MELODY, DP_TYPE_ENUM, melodyNumber ) 
        // play it
        unschedule(restoreDefaultSettings)
        cmds += appendTuyaCommand( TUYA_DP_ALARM, DP_TYPE_BOOL, 1 ) 
        sendZigbeeCommands( combinedTuyaCommands(cmds) )    
    }
    else {
        unschedule(restoreDefaultSettings)
        sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(TUYA_DP_ALARM, 2), DP_TYPE_BOOL, "00"))    
    }
    
}

// capability "Tone"
def beep() {
if ( true ) {
    wakeUpTuya()
    String cmds = ""
    state.lastCommand = "beep"    
    log.warn "settings?.beepVolume = ${settings?.beepVolume}"
    def volumeTuya; def volumePct
    (volumeTuya, volumePct) = findVolumeByName(settings?.beepVolume )
    if (volumeTuya >= 0 ) {
        cmds += appendTuyaCommand( TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya) ) 
    }
    cmds += appendTuyaCommand( TUYA_DP_DURATION, DP_TYPE_VALUE, 1 ) 
    cmds += appendTuyaCommand( TUYA_DP_MELODY, DP_TYPE_ENUM, 2 ) 
    unschedule(restoreDefaultSettings)
    cmds += appendTuyaCommand( TUYA_DP_ALARM, DP_TYPE_BOOL, 1 )
    sendZigbeeCommands( combinedTuyaCommands(cmds) )
}
else {
    ArrayList<String> cmds = []
    state.lastCommand = "beep"    
    cmds += sendTuyaCommand( zigbee.convertToHexString(TUYA_DP_VOLUME,2), DP_TYPE_ENUM, "01", delay=50)
    cmds += sendTuyaCommand( zigbee.convertToHexString(TUYA_DP_DURATION,2), DP_TYPE_VALUE, "00000001", delay=50 ) 
    cmds += sendTuyaCommand( zigbee.convertToHexString(TUYA_DP_MELODY,2), DP_TYPE_ENUM, "02", delay=100 ) 
    unschedule(restoreDefaultSettings)
    cmds += sendTuyaCommand( zigbee.convertToHexString(TUYA_DP_ALARM,2), DP_TYPE_BOOL, "01" , delay=200) 
    sendZigbeeCommands( cmds )
}
}

def restoreDefaultSettings() {
    String cmds = ""
    def volumeName
    def volumeTuya
    (volumeName, volumeTuya) =  findVolumeByPct( state.setVolume ) 
    if (volumeTuya >= 0) {
        cmds += appendTuyaCommand( TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya) ) 
    }
    cmds += appendTuyaCommand( TUYA_DP_DURATION, DP_TYPE_VALUE, safeToInt(state.setDuration)  ) 
    cmds += appendTuyaCommand( TUYA_DP_MELODY, DP_TYPE_ENUM, safeToInt(melodiesOptions.indexOf(state.setMelody))) 
    if (settings?.logEnable) log.debug "${device.displayName} restoring default settings volume=${volumeName}, duration=${state.setDuration}, melody=${state.setMelody}"
    sendZigbeeCommands( combinedTuyaCommands(cmds) )    
}

//capability "AudioVolume" //Attributes: mute - ENUM ["unmuted", "muted"] volume - NUMBER, unit:%; Commands: mute() setVolume(volumelevel) volumelevel required (NUMBER) - Volume level (0 to 100) unmute() volumeDown() volumeUp()
def mute() {
    sendEvent(name: "mute", value: "muted")       
}

def unmute() {
    sendEvent(name: "mute", value: "unmuted")       
}

def getNearestTuyaVolumeLevel( volumelevel ) {
    def nearestlevel = 0
    //if (volumelevel <= 0 ) level = 0
    /*else*/ if (volumelevel <= 33) nearestlevel = 33
    else if (volumelevel <= 66) nearestlevel = 66
    else nearestlevel = 100
    return nearestlevel
}

def setVolume(volumelevel) {
    // - Volume level (0 to 100)
    //log.trace "volumelevel( ${volumelevel} )"
    String cmds = ""
    def nearestlevel =  getNearestTuyaVolumeLevel( volumelevel )
    if      (nearestlevel == 0 && device.currentValue("mute", true) == "unmuted")  mute()
    else if (nearestlevel != 0 && device.currentValue("mute", true) == "muted") unmute() 
    state.volume = nearestlevel
    def volumeName
    def volumeTuya
    (volumeName, volumeTuya) =  findVolumeByPct( nearestlevel ) 
    log.warn "matched volumelevel=${volumelevel} to nearestLlevel=${nearestlevel} (volumeTuya=${volumeTuya})"
    if (volumeTuya >= 0) {
        cmds += appendTuyaCommand( TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya) ) 
    }
    if (settings?.logEnable) log.debug "${device.displayName} setting volume=${volumeName}"
    sendZigbeeCommands( combinedTuyaCommands(cmds) )      
}

def volumeDown() {
    setVolume( state.volume - 34)
}

def volumeUp() {
    setVolume( state.volume + 33)
}


// capability "Chime"    // soundEffects - JSON_OBJECT; soundName - STRING; status - ENUM ["playing", "stopped"]; Commands: playSound(soundnumber); soundnumber required (NUMBER) - Sound number to play; stop()
//      command "playSound", [
//          [name:"soundNumber", type: "NUMBER", description: "Melody number, 1..18", isRequired: true],
//          [name:"duration", type: "NUMBER", description: "duration is seconds"],
//          [name:"volume", type: "NUMBER", description: "sound volume, %"]
//      ]
def playSound(soundnumber, volumeLevel=null, duration=null) {
    wakeUpTuya()
    String cmds = ""
    def volumeName; def volumeTuya; def volumePct
    int soundNumberIndex = safeToInt(soundnumber)
    soundNumberIndex = soundNumberIndex < 1 ? 1 : soundNumberIndex > 18 ? 18 : soundNumberIndex; soundNumberIndex -= 1
    if (volumeLevel == null) {    
        // use the default playSoundVolume
        volumeName = settings?.playSoundVolume
        (volumeTuya, volumePct) = findVolumeByName( volumeName )        
        log.warn "settings?.playSoundVolume = ${volumeName}, found volumeTuya = ${volumeTuya}"
    }
    else {
        def nearestVolume = getNearestTuyaVolumeLevel( volumeLevel )
        (volumeName, volumeTuya) =  findVolumeByPct( nearestVolume ) 
    }
    if (duration == null) {
        duration = settings?.playSoundDuration ?:10 as int
    }
    else {
        duration = duration <1 ? 1 : duration > 180 ? 180 : duration as int
    }
    state.lastCommand = "playSound"
    cmds += appendTuyaCommand( TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya)) 
    cmds += appendTuyaCommand( TUYA_DP_DURATION, DP_TYPE_VALUE, safeToInt(duration) ) 
    cmds += appendTuyaCommand( TUYA_DP_MELODY, DP_TYPE_ENUM, soundNumberIndex) 
    unschedule(restoreDefaultSettings)
    cmds += appendTuyaCommand( TUYA_DP_ALARM, DP_TYPE_BOOL, 1 )
    log.warn "playSound ${soundnumber} (${melodiesOptions.get(soundNumberIndex)}) index=${soundNumberIndex}, duration=${duration}, volume=${volumeName}(${volumeTuya})"
    sendZigbeeCommands( combinedTuyaCommands(cmds) )
}

def stop() {
    off()
}

// capability "MusicPlayer"
def pause() {
}

def play() {
}

def sendVolumeEvent( volume,  isDigital=false ) {
    def map = [:] 
    map.name = "volume"
    map.value = volume
    map.unit = "%"
    map.type = isDigital == true ? "digital" : "physical"
    map.descriptionText = "${map.name} is ${map.value}"
    if (txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    sendEvent(map)
}

def sendAlarmEvent( mode, isDigital=false ) {
    def map = [:] 
    map.name = "alarm"
    map.value = mode
    //map.unit = "Hz"
    map.type = isDigital == true ? "digital" : "physical"
    map.descriptionText = "${map.name} is ${map.value}"
    if (txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    sendEvent(map)
    sendEvent(name: "switch", value: mode=="off"?"off":"on", descriptionText: map.descriptionText, type:"digital")       
}


void setAlarmMelody( melodyName ) {
    def melodyIndex = melodiesOptions.indexOf(melodyName)
    log.warn "melodyIndex=${melodyIndex}"
    if (melodyIndex <0) {
        if (settings?.logEnable) log.warn "${device.displayName} setMelody invalid ${melodyName}"
        return
    }
    if (settings?.logEnable) log.debug "${device.displayName} setMelody $melodyName ($melodyIndex)"
    state.setMelody = melodyName
    sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(TUYA_DP_MELODY, 2), DP_TYPE_ENUM, zigbee.convertToHexString(melodyIndex, 2)))
}


void setAlarmDuration(BigDecimal length) {
    int duration = length > 255 ? 255 : length < 0 ? 0 : length
    if (settings?.logEnable) log.debug "${device.displayName} setDuration ${duration}"
    state.setDuration = duration
    sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(TUYA_DP_DURATION, 2), DP_TYPE_VALUE, zigbee.convertToHexString(duration, 8)))
}

void setAlarmVolume(String volumeOption) {
    def index = volumeMapping.findIndexOf{it.key==volumeOption}
    //log.warn "index=${index}"
    if (index < 0) {
        if (settings?.txtEnable) log.warn "${device.displayName} setVolume not supported parameter ${volume}"
        return
    }
    log.trace "volumeMapping[${volumeOption}] = ${volumeMapping[volumeOption]}"
    def volumePct = volumeMapping[volumeOption].find{it.key=='volume'}.value
    def tuyaValue = volumeMapping[volumeOption].find{it.key=='tuya'}.value
    //log.trace "volume=${volumePct}, tuya=${tuyaValue} "
    switch (volumeOption) {
        case "muted" :
            mute()
            return
        case "low" :
        case "medium" :
        case "high" :
            sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(TUYA_DP_VOLUME, 2), DP_TYPE_ENUM, zigbee.convertToHexString(tuyaValue as int, 2)))
            break
        default :
            if (settings?.txtEnable) log.warn "${device.displayName} setVolume not supported parameter ${volume}"
            return
    }
    unmute()
    if (settings?.logEnable) log.debug "${device.displayName} setVolume ${volumeOption} ${volumePct}% (Tuya:${tuyaValue})"
    state.setVolume = volumePct
}

// called on initial install of device during discovery
// also called from initialize() in this driver!
def installed() {
    log.info "${device.displayName} installed()"
    unschedule()
    unmute()
}

// called when preferences are saved
// runs when save is clicked in the preferences section
def updated() {
    if (settings?.txtEnable) log.info "${device.displayName} Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b>"
    if (settings?.txtEnable) log.info "${device.displayName} Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    unschedule()
    if (logEnable==true) {
        runIn(86400, logsOff)    // turn off debug logging after 24 hours
        if (settings?.txtEnable) log.info "${device.displayName} Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
}


def refresh() {
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh()..."}
    zigbee.readAttribute(0, 1)
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
        sendEvent(name: "soundEffects", value: JsonOutput.toJson(melodiesOptions), isStateChange: true)
    }
    //
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0
    state.lastCommand = "unknown"
    state.setMelody = "1=Doorbell"
    state.setDuration = 10
    state.setVolume = 66

    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", true)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings?.beepVolume == null) device.updateSetting("beepVolume", [value:"low", type:"enum"])
    if (fullInit == true || settings?.playSoundVolume == null) device.updateSetting("playSoundVolume", [value:"medium", type:"enum"])
    if (fullInit == true || settings?.playSoundDuration == null) device.updateSetting("playSoundDuration", [value:10, type:"number"])
    
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
    List<String> cmds = []
    cmds += tuyaBlackMagic()    
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

private sendTuyaCommand(dp, dp_type, fncmd, delay=200) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "${device.displayName} sendTuyaCommand = ${cmds}"
    state.txCounter = state.txCounter ?:0 + 1
    return cmds
}

private wakeUpTuya() {
    sendZigbeeCommands(zigbee.readAttribute(0x0000, 0x0004, [:], delay=50) )
}

private combinedTuyaCommands(String cmds) {
    state.txCounter = state.txCounter ?:0 + 1
    return zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay=200, PACKET_ID + cmds ) 
}

private appendTuyaCommand(Integer dp, String dp_type, Integer fncmd) {
    Integer fncmdLen =  dp_type== DP_TYPE_VALUE? 8 : 2
    String cmds = zigbee.convertToHexString(dp, 2) + dp_type + zigbee.convertToHexString((int)(fncmdLen/2), 4) + zigbee.convertToHexString(fncmd, fncmdLen) 
    //if (settings?.logEnable) log.trace "${device.displayName} appendTuyaCommand = ${cmds}"
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.trace "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            state.txCounter = state.txCounter ?:0 + 1
    }
    sendHubCommand(allActions)
}


private getPACKET_ID() {
//    state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
//    return zigbee.convertToHexString(state.packetID, 4)
    return zigbee.convertToHexString(Math.abs(new Random().nextInt() % 65536), 4) 
}


private getDescriptionText(msg) {
	def descriptionText = "${device.displayName} ${msg}"
	if (settings?.txtEnable) log.info "${descriptionText}"
	return descriptionText
}

def logsOff(){
    log.info "${device.displayName} debug logging disabled..."
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
        result.type  == "physical"
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
        result.type  == "physical"
        if (settings?.txtEnable) log.info "${result.descriptionText}"
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

def test( str ) {
    sendEvent(name: "soundEffects", value: JsonOutput.toJson(melodiesOptions), isStateChange: true)
}

