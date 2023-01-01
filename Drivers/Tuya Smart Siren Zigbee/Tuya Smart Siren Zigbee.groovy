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
 * ver. 1.1.0 2022-11-05 kkossev  - Alarm events are registered upon confirmation from the device only; added switch capability; added Tone capability (beep command); combined Tuya commands; default settings are restored after the beep command
 *                                 added capability 'Chime'; setVolume; volumeUp, volumeDown; playSound; beepVolume; playSoundVolume; playSoundDuration; unschedule() is called when preferences are updated.
 * ver. 1.1.1 2022-12-27 kkossev  - bug fix: playing a sound from RM rule without specifying the volume level was making the device freeze; debug logs cleanup; sounds titles improvements;
 * ver. 1.1.2 2022-12-31 kkossev  - bug fix: the sounds titles changes in the previous version could make the siren freeze!; Import button changed to the development branch
 * ver. 1.2.0 2023-01-01 kkossev  - (dev. branch) _TZE200_d0yu2xgi (NEO) experimental support (w/o T/H); added separate preferences for alarm and melody number. volume and duration
 *
 *
 *
*/

def version() { "1.1.2" }
def timeStamp() {"2023/01/01 6:24 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

@Field static final Boolean debug = false
 
metadata {
    definition (name: "Tuya Smart Siren Zigbee", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Smart%20Siren%20Zigbee/Tuya%20Smart%20Siren%20Zigbee.groovy", singleThreaded: true ) {
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
        //
        input (name: "beepVolume", type: "enum", title: "<b>Beep Volume</b>", description:"<i>Select the volume used in the Beep command</i>", defaultValue: "low", options: volumeOptions)
        //
        input (name: "alarmMelody", type: "enum", title: "<b>Alarm default melody</b>", description:"<i>Select the melody used in the Alarm commands</i>", defaultValue: TUYA_DEFAULT_MELODY, options: melodiesOptions)
        input (name: "alarmSoundVolume", type: "enum", title: "<b>Alarm default volume</b>", description:"<i>Select the volume used in the Alarm commands</i>", defaultValue: 'high', options: volumeOptions)
        input (name: "alarmSoundDuration", type: "number", title: "<b>Alarm default duration</b>, seconds", description: "<i>Select the duration used in the Alarm commands, seconds</i>", range: "1..$TUYA_MAX_DURATION", defaultValue: TUYA_MAX_DURATION)
        //
        input (name: "playSoundMelody", type: "enum", title: "<b>Play Sound (Chime) default melody</b>", description:"<i>Select the default melody used in the playSound (Chime) command</i>", defaultValue: '12=Alarm Siren', options: melodiesOptions)
        input (name: "playSoundVolume", type: "enum", title: "<b>Play Sound (Chime) default volume</b>", description:"<i>Select the default volume used in the playSound (Chime) command</i>", defaultValue: TUYA_DEFAULT_VOLUME, options: volumeOptions)
        input (name: "playSoundDuration", type: "number", title: "<b>Play Sound (Chime) default duration</b>, seconds", description: "<i>Select the default duration used in the playSound (Chime) command, seconds</i>", range: "1..$TUYA_MAX_DURATION", defaultValue: TUYA_DEFAULT_DURATION)
    }
}

def isNeo() {device.getDataValue("manufacturer") in ['_TZE200_d0yu2xgi', '_TZE200_d0yu2xgi', 'd0yu2xgi']}

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

@Field static final String  TUYA_DEFAULT_VOLUME    = 'medium'
@Field static final Integer TUYA_DEFAULT_DURATION  = 10
@Field static final Integer TUYA_MAX_DURATION      = 180
@Field static final String  TUYA_DEFAULT_MELODY    = '2=Fur Elise'
@Field static final Integer TUYA_MAX_MELODIES      = 18

@Field static final List<String> melodiesOptions = [
    '1=Doorbell 1',
    '2=Fur Elise',
    '3=Westminster',
    '4=4 Key Chime',
    '5=William Tell',
    '6=Mozart Piano',
    '7=Space Alarm',
    '8=Klaxon',
    '9=meep meep',
    '10=Wheep',
    '11=Barking dog',
    '12=Alarm Siren',
    '13=Doorbell 2',
    '14=Old Phone',
    '15=Police Siren',
    '16=Evacuation bell',
    '17=Clock alarm',
    '18=Fire alarm'
] //as String[]

private findVolumeByTuyaValue( fncmd ) {
    def volumeName = 'unknown'
    def volumePct = -1
    volumeMapping.each{ k, v -> 
        if (v.tuya.value as String == fncmd.toString()) {
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
        if (v.volume.value as String == pct.toString()) {
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
        if (k as String == name as String) {
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

@Field static final Integer NEO_DP_VOLUME     = 116
@Field static final Integer NEO_DP_DURATION   = 103
@Field static final Integer NEO_DP_ALARM      = 104
@Field static final Integer NEO_DP_BATTERY    = 101    // enum
@Field static final Integer NEO_DP_MELODY     = 102


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
                logWarn "unparesed attrint ${descMap.attrInt}"
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
            logWarn "ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
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
            case 0x74 : // Neo Siren Volume ['low', 'medium', 'high']
                logDebug "Neo Siren Volume is ${fncmd}"
            case TUYA_DP_VOLUME :    // (05) volume [ENUM] 0:low 1: mid 2:high
                def volumeName = 'unknown'
                def volumePct = -1
                (volumeName, volumePct) = findVolumeByTuyaValue( fncmd )
                if (volumeName != 'unknown') {
                    if (settings?.txtEnable) log.debug "${device.displayName} volume received is ${volumeName} ${volumePct}% (${fncmd})"
                    sendVolumeEvent( volumePct )
                }
                break
            
            case 0x67 : // Neo Alarm Duration 0..1800 seconds
                logDebug "Neo Alarm Duration is ${fncmd}"
            case TUYA_DP_DURATION :  // (07) duration [VALUE] in seconds
                if (settings?.txtEnable) log.info "${device.displayName} duration is ${fncmd} s"
                sendEvent(name: "duration", value: fncmd, descriptionText: descriptionText )            
                break
            
            case 0x68 : // Neo Alarm On 0x01 Off 0x00
                logDebug "Neo Alarm is ${fncmd}"
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
            
            case 0x66 : // Neo Alarm Melody 18 Max ? -> fncmd+1 ? TODO
                logDebug "Neo Alarm Melody is ${fncmd}"
            case TUYA_DP_MELODY :     // (21) melody [enum] 0..17
                if (settings?.txtEnable) log.info "${device.displayName} melody is ${melodiesOptions[fncmd]} (${fncmd})"
                sendEvent(name: "soundName", value: melodiesOptions[fncmd], descriptionText: descriptionText )            
                break
            
            case 0x65 : // Neo Power Mode  ['battery_full', 'battery_high', 'battery_medium', 'battery_low', 'usb']
                logInfo "Neo Power Mode is ${fncmd}"
                break
            case 0x69 : // Neo Temperature  ( x10 ?)
                logInfo "Neo Temperature is ${fncmd}"
                break
            case 0x6A : // Neo Humidity Level (x100 ?)
                logInfo "Neo Humidity Level is ${fncmd}"
                break
            case 0x6B : // Neo Min Alarm Temperature -20 .. 80
                logInfo "Neo Min Alarm Temperature is ${fncmd}"
                break
            case 0x6C : // Neo Max Alarm Temperature -20 .. 80
                logInfo "Neo Max Alarm Temperature is ${fncmd}"
                break
            case 0x6D : // Neo Min Alarm Humidity 1..100
                logInfo "Neo Min Alarm Humidity is ${fncmd}"
                break
            case 0x6E : // Neo Max Alarm Humidity 1..100
                logInfo "Neo Max Alarm Humidity is ${fncmd}"
                break
            case 0x70 : // Neo Temperature Unit (F 0x00, C 0x01)
                logInfo "Neo Temperature Unit is ${fncmd}"
                break
            case 0x71 : // Neo Alarm by Temperature status
                logInfo "Neo Alarm by Temperature status is ${fncmd}"
                break
            case 0x72 : // Neo Alarm by Humidity status
                logInfo "Neo Alarm by Humidity status is ${fncmd}"
                break
            case 0x73 : // Neo ???
                logInfo "Neo ??? is ${fncmd}"
                break
            default :
                logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
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
    logDebug "swithing alarm ${commandName}()"
    state.lastCommand = commandName
    if (commandName != "off") {
        // volume
        def volumeName; def volumeTuya; 
        (volumeName, volumeTuya) = findVolumeByPct( state.setVolume )
        log.warn "state.setVolume=${state.setVolume} volumeName=${volumeName} volumeTuya=${volumeTuya}"
        if (volumeTuya >= 0 && volumeTuya <=2) {
            cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, volumeTuya as int ) 
        }
        else {
            state.setVolume = 66 
        }
        // duration
        def durationTuya = safeToInt(state.setDuration)
        if (durationTuya >=1 && durationTuya <= TUYA_MAX_DURATION) {
            cmds += appendTuyaCommand( isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, DP_TYPE_VALUE, durationTuya as int ) 
        }
        else {
            state.setDuration = TUYA_DEFAULT_DURATION
        }
        // melody
        
        def melodyTuya = safeToInt(melodiesOptions.indexOf(state.setMelody))
        if (melodyTuya >=0 && melodyTuya <= TUYA_MAX_MELODIES-1) {
            cmds += appendTuyaCommand( isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, DP_TYPE_ENUM, melodyTuya as int) 
        }
        else {
            state.setMelody = melodiesOptions[0]
        }        
        // play it
        unschedule(restoreDefaultSettings)
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_ALARM : TUYA_DP_ALARM, DP_TYPE_BOOL, 1 ) 
        sendZigbeeCommands( combinedTuyaCommands(cmds) )    
    }
    else {
        unschedule(restoreDefaultSettings)
        sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(isNeo() ? NEO_DP_ALARM : TUYA_DP_ALARM, 2), DP_TYPE_BOOL, "00"))    
    }
    
}

// capability "Tone"
def beep() {
if ( true ) {
    wakeUpTuya()
    String cmds = ""
    state.lastCommand = "beep"    
    logDebug "sending beep() beepVolume = ${settings?.beepVolume}"
    def volumeTuya; def volumePct
    (volumeTuya, volumePct) = findVolumeByName(settings?.beepVolume )
    if (volumeTuya >= 0 ) {
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya) ) 
    }
    cmds += appendTuyaCommand( isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, DP_TYPE_VALUE, 1 ) 
    cmds += appendTuyaCommand( isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, DP_TYPE_ENUM, 2 ) 
    unschedule(restoreDefaultSettings)
    cmds += appendTuyaCommand( isNeo() ? NEO_DP_ALARM : TUYA_DP_ALARM, DP_TYPE_BOOL, 1 )
    sendZigbeeCommands( combinedTuyaCommands(cmds) )
}
else {
    ArrayList<String> cmds = []
    state.lastCommand = "beep"    
    cmds += sendTuyaCommand( zigbee.convertToHexString(isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, 2), DP_TYPE_ENUM, "01", delay=50)
    cmds += sendTuyaCommand( zigbee.convertToHexString(isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION ,2), DP_TYPE_VALUE, "00000001", delay=50 ) 
    cmds += sendTuyaCommand( zigbee.convertToHexString(isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, 2), DP_TYPE_ENUM, "02", delay=100 ) 
    unschedule(restoreDefaultSettings)
    cmds += sendTuyaCommand( zigbee.convertToHexString(isNeo() ? NEO_DP_ALARM : TUYA_DP_ALARM,2), DP_TYPE_BOOL, "01" , delay=200) 
    sendZigbeeCommands( cmds )
}
}

def restoreDefaultSettings() {
    String cmds = ""
    def volumeName
    def volumeTuya
    (volumeName, volumeTuya) =  findVolumeByPct( state.setVolume ) 
    if (volumeTuya >= 0 && volumeTuya <=2) {
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya) ) 
    }
    else {
        state.setVolume = 66    // default volume is 'medium'
    }
    def durationTuya = safeToInt(state.setDuration)
    if (durationTuya >=1 && durationTuya <= TUYA_MAX_DURATION) {
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, DP_TYPE_VALUE, durationTuya as int ) 
    }
    else {
        state.setDuration = TUYA_DEFAULT_DURATION    // 10
    }
    def melodyTuya = safeToInt(melodiesOptions.indexOf(state.setMelody))
    if (melodyTuya >=0 && melodyTuya <= TUYA_MAX_MELODIES-1) {
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, DP_TYPE_ENUM, melodyTuya as int) 
    }
    else {
        state.setMelody = melodiesOptions[0]
    }
    logDebug "restoring default settings volume=${volumeName}, duration=${state.setDuration}, melody=${state.setMelody}"
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
    String cmds = ""
    def nearestlevel =  getNearestTuyaVolumeLevel( volumelevel )
    if      (nearestlevel == 0 && device.currentValue("mute", true) == "unmuted")  mute()
    else if (nearestlevel != 0 && device.currentValue("mute", true) == "muted") unmute() 
    state.volume = nearestlevel
    def volumeName
    def volumeTuya
    (volumeName, volumeTuya) =  findVolumeByPct( nearestlevel ) 
    logDebug "matched volumelevel=${volumelevel} to nearestLlevel=${nearestlevel} (volumeTuya=${volumeTuya})"
    if (volumeTuya >= 0) {
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya) ) 
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
def playSound(soundnumber=null, volumeLevel=null, duration=null) {
    wakeUpTuya()
    String cmds = ""
    def volumeName; def volumeTuya; def volumePct
    if (soundnumber == null) {
        // use the default melody
        soundnumber = melodiesOptions.indexOf(settings?.playSoundMelody ?: TUYA_DEFAULT_MELODY ) + 1
    }
    int soundNumberIndex = safeToInt(soundnumber)
    soundNumberIndex = soundNumberIndex < 1 ? 1 : soundNumberIndex > TUYA_MAX_MELODIES ? TUYA_MAX_MELODIES : soundNumberIndex; 
    soundNumberIndex -= 1    // Tuya parameter is zero based !
    //
    if (volumeLevel == null) {    
        // use the default playSoundVolume
        volumeName = settings?.playSoundVolume ?: TUYA_DEFAULT_VOLUME
        (volumeTuya, volumePct) = findVolumeByName( volumeName )        
        logDebug "volumeLevel is null, volumeTuya = ${volumeTuya}"
    }
    else {
        def nearestVolume = getNearestTuyaVolumeLevel( volumeLevel )
        (volumeName, volumeTuya) =  findVolumeByPct( nearestVolume ) 
    }
    //
    if (duration == null) {
        duration = settings?.playSoundDuration ?: TUYA_DEFAULT_DURATION as int
    }
    else {
        duration = duration <1 ? 1 : duration > TUYA_MAX_DURATION ? TUYA_MAX_DURATION : duration as int
    }
    state.lastCommand = "playSound"
    cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya)) 
    cmds += appendTuyaCommand( isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, DP_TYPE_VALUE, safeToInt(duration) ) 
    cmds += appendTuyaCommand( isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, DP_TYPE_ENUM, soundNumberIndex) 
    unschedule(restoreDefaultSettings)
    cmds += appendTuyaCommand( isNeo() ? NEO_DP_ALARM : TUYA_DP_ALARM, DP_TYPE_BOOL, 1 )
    logDebug "playSound ${soundnumber} (${melodiesOptions.get(soundNumberIndex)}) index=${soundNumberIndex}, duration=${duration}, volume=${volumeName}(${volumeTuya})"
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
    if (melodyIndex <0) {
        logWarn "setMelody invalid ${melodyName}"
        return
    }
    logDebug "setMelody $melodyName ($melodyIndex)"
    state.setMelody = melodyName
    sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, 2), DP_TYPE_ENUM, zigbee.convertToHexString(melodyIndex, 2)))
}


void setAlarmDuration(BigDecimal length) {
    int duration = length > 255 ? 255 : length < 0 ? 0 : length
    if (settings?.logEnable) log.debug "${device.displayName} setDuration ${duration}"
    state.setDuration = duration
    sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, 2), DP_TYPE_VALUE, zigbee.convertToHexString(duration, 8)))
}

void setAlarmVolume(String volumeOption) {
    def index = volumeMapping.findIndexOf{it.key==volumeOption}
    if (index < 0) {
        logWarn "setVolume not supported parameter ${volume}"
        return
    }
    //log.trace "volumeMapping[${volumeOption}] = ${volumeMapping[volumeOption]}"
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
            sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, 2), DP_TYPE_ENUM, zigbee.convertToHexString(tuyaValue as int, 2)))
            break
        default :
            logWarn "setVolume not supported parameter ${volume}"
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
        runIn(86400, logsOff, [overwrite: true, misfire: "ignore"])    // turn off debug logging after 24 hours
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
    state.setDuration = TUYA_DEFAULT_DURATION    // 10
    state.setVolume = 66

    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", true)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings?.beepVolume == null) device.updateSetting("beepVolume", [value:"low", type:"enum"])
    if (fullInit == true || settings?.playSoundVolume == null) device.updateSetting("playSoundVolume", [value: TUYA_DEFAULT_VOLUME, type:"enum"])
    if (fullInit == true || settings?.playSoundDuration == null) device.updateSetting("playSoundDuration", [value:TUYA_DEFAULT_DURATION, type:"number"])
    
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
    logDebug "sendTuyaCommand = ${cmds}"
    state.txCounter = state.txCounter ?: 0 + 1
    return cmds
}

private wakeUpTuya() {
    sendZigbeeCommands(zigbee.readAttribute(0x0000, 0x0004, [:], delay=50) )
}

private combinedTuyaCommands(String cmds) {
    state.txCounter = state.txCounter ?: 0 + 1
    return zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay=200, PACKET_ID + cmds ) 
}

private appendTuyaCommand(Integer dp, String dp_type, Integer fncmd) {
    Integer fncmdLen =  dp_type== DP_TYPE_VALUE? 8 : 2
    String cmds = zigbee.convertToHexString(dp, 2) + dp_type + zigbee.convertToHexString((int)(fncmdLen/2), 4) + zigbee.convertToHexString(fncmd, fncmdLen) 
    //logDebug "appendTuyaCommand = ${cmds}"
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    logDebug "sendZigbeeCommands(cmd=$cmd)"
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            state.txCounter = state.txCounter ?: 0 + 1
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
        logWarn "ignoring BatteryPercentageResult(${rawValue})"
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
        logWarn "ignoring BatteryResult(${rawValue})"
    }    
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


def test( str ) {
    log.trace "${melodiesOptions[0]}"
}

