library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Tuya Alarm Library",
    name: "tuyaAlarmLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/tuyaAlarmLib.groovy",
    version: "1.0.1",
    documentationLink: ""
)
/*
 * tuyaAlarmLib - Tuya Alarm Library
 *
 * ver. 1.0.0  2023-09-03 kkossev  - (dev. branch) - code transfered from "Tuya Smart Siren Zigbee" driver ver. 1.2.2
 * ver. 1.0.1  2023-09-04 kkossev  - battery percentage bug fix
 *
 *                                   TODO: setDuration infoLog; setMelody infoLog; setVolume infoLog
 *
*/

def tuyaAlarmLibVersion()   {"1.0.1"}
def tuyaAlarmLibTimeStamp() {"2023/09/04 9:37 PM"}

metadata {
        capability "Alarm"    // alarm - ENUM ["strobe", "off", "both", "siren"]; Commands: both() off() siren() strobe()
        capability "Tone"     // Commands: beep()
        capability "Chime"    // soundEffects - JSON_OBJECT; soundName - STRING; status - ENUM ["playing", "stopped"]; Commands: playSound(soundnumber); soundnumber required (NUMBER) - Sound number to play; stop()
        capability "AudioVolume" //Attributes: mute - ENUM ["unmuted", "muted"] volume - NUMBER, unit:%; Commands: mute() setVolume(volumelevel) volumelevel required (NUMBER) - Volume level (0 to 100) unmute() volumeDown() volumeUp()
        //capability "TemperatureMeasurement"
        //capability "RelativeHumidityMeasurement"
        
        attribute "duration", "number"
        attribute "Info", "text"
        
        command "setMelody", [
            [name:"alarmType", type: "ENUM", description: "Sound Type", constraints: soundTypeOptions],
            [name:"melodyNumber", type: "NUMBER", description: "Set the Melody Number 1..18"]
        ]
        command "setDuration", [
            [name:"alarmType", type: "ENUM", description: "Sound Type", constraints: soundTypeOptions],
            [name:"alarmLength", type: "NUMBER", description: "Set the  Duration in seconds 0..180"]
        ]
        command "setVolume", [
            [name:"volumeType", type: "ENUM", description: "Sound Type", constraints: volumeTypeOptions],
            [name:"Volume", type: "ENUM", description: "Set the Volume", constraints: volumeNameOptions ]
        ]
        command "playSound", [
            [name:"soundNumber", type: "NUMBER", description: "Melody Number, 1..18", isRequired: true],
            [name:"volumeLevel", type: "NUMBER", description: "Sound Volume level, 0..100 %"],
            [name:"duration", type: "NUMBER", description: "Duration is seconds"]
        ]
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_t1blo2bj", deviceJoinName: "Tuya NEO Smart Siren"          // vendor: 'Neo', model: 'NAS-AB02B2'
        // not working with this driver - use Markus's driver instead
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A" ,model:"TS0601", manufacturer:"_TZE200_d0yu2xgi", deviceJoinName: "Tuya NEO Smart Siren T&H"      // Neo NAS-AB02B0
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A" ,model:"TS0601", manufacturer:"_TYST11_d0yu2xgi", deviceJoinName: "Tuya NEO Smart Siren T&H"      // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A" ,model:"TS0601", manufacturer:        "d0yu2xgi", deviceJoinName: "Tuya NEO Smart Siren T&H"      // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A" ,model:"TS0601", manufacturer:"_TZE204_t1blo2bj", deviceJoinName: "Tuya Smart Siren"              // https://community.hubitat.com/t/release-tuya-smart-siren-zigbee-driver/91772/67?u=kkossev

        // https://github.com/zigpy/zha-device-handlers/issues/1379#issuecomment-1077772021 
    
    preferences {
        input (name: "beepVolume", type: "enum", title: "<b>Beep Volume</b>", description:"<i>Select the volume used in the Beep command</i>", defaultValue: "low", options: volumeNameOptions)
        //
        input (name: "alarmMelody", type: "enum", title: "<b>Alarm default Melody</b>", description:"<i>Select the melody used in the Alarm commands</i>", defaultValue: '12=Alarm Siren', options: melodiesOptions)
        input (name: "alarmSoundVolume", type: "enum", title: "<b>Alarm default Volume</b>", description:"<i>Select the volume used in the Alarm commands</i>", defaultValue: 'high', options: volumeNameOptions)
        input (name: "alarmSoundDuration", type: "number", title: "<b>Alarm default Duration</b>, seconds", description: "<i>Select the duration used in the Alarm commands, seconds</i>", range: "1..$TUYA_MAX_DURATION", defaultValue: TUYA_MAX_DURATION)
        //
        input (name: "playSoundMelody", type: "enum", title: "<b>Play Sound (Chime) default Melody</b>", description:"<i>Select the default melody used in the playSound (Chime) command</i>", defaultValue: TUYA_DEFAULT_MELODY, options: melodiesOptions)
        input (name: "playSoundVolume", type: "enum", title: "<b>Play Sound (Chime) default Volume</b>", description:"<i>Select the default volume used in the playSound (Chime) command</i>", defaultValue: TUYA_DEFAULT_VOLUME, options: volumeNameOptions)
        input (name: "playSoundDuration", type: "number", title: "<b>Play Sound (Chime) default Duration</b>, seconds", description: "<i>Select the default duration used in the playSound (Chime) command, seconds</i>", range: "1..$TUYA_MAX_DURATION", defaultValue: TUYA_DEFAULT_DURATION)
        //
        if (advancedOptions == true) {
            input (name: "restoreAlarmSettings", type: "bool", title: "<b>Restore Default Alarm Settings</b>", description: "<i>After playing Beep or Chime sounds, the default Alarm settings will be restored after 7 seconds </i>", defaultValue: false)
            input (name: "presetBeepAndChimeSettings", type: "enum", title: "<b>Preset Beep and Chime Settings</b>", description: "<i>Before playing Beep or Chime sounds, the preset Beep/Chime settings will be restored first</i>", defaultValue: "fast", options:["fast", /*"slow",*/ "none"])
        } 
    }    
    
}


def isNeo()  { (device.getDataValue("manufacturer") in ['_TZE200_d0yu2xgi', '_TZE200_d0yu2xgi', 'd0yu2xgi']) }
def isTuyaAlarm() { (device.getDataValue("manufacturer") in ['_TZE204_t1blo2bj']) }

@Field static final Map disabledEnabledOptions = [
    '0' : 'disabled',
    '1' : 'enabled'
]
@Field static final Map temperatureScaleOptions = [
    '0' : 'Fahrenheit',
    '1' : 'Celsius'
]
@Field static final List<String> volumeNameOptions = [
    'low',
    'medium',
    'high'
]
@Field static final List<String> soundTypeOptions  = [ 'alarm', 'chime']
@Field static final List<String> volumeTypeOptions = [ 'alarm', 'chime', 'beep']
@Field static final LinkedHashMap volumeMapping = [
    'low'      : [ volume: '33',  tuya: '0'],
    'medium'   : [ volume: '66',  tuya: '1'],
    'high'     : [ volume: '100', tuya: '2']
]// as ConfigObject

@Field static final String  TUYA_DEFAULT_VOLUME_NAME = 'medium'
@Field static final Integer TUYA_DEFAULT_DURATION    = 10
@Field static final Integer TUYA_MAX_DURATION        = 180
@Field static final String  TUYA_DEFAULT_MELODY_NAME = '2=Fur Elise'
@Field static final Integer TUYA_MAX_MELODIES        = 18

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



private findVolumeByTuyaValue( fncmd ) {
    def volumeName = 'unknown'
    def volumePct = -1
    volumeMapping.each{ k, v -> 
        if (v.tuya as String == fncmd.toString()) {
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
        if (v.volume as String == pct.toString()) {
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
            volumeTuya = safeToInt(v.tuya)
            volumePct = safeToInt(v.volume)
        }
    }
    return [volumeTuya, volumePct]
}


void processTuyaDpAlarm(descMap, dp, dp_id, fncmd) {
        logDebug "processTuyaDpAlarm:  dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
        switch (dp) {
            case 0x74 : // Neo Siren Volume ['low', 'medium', 'high']
                logDebug "Neo Siren Volume is (${fncmd})"
            case TUYA_DP_VOLUME :    // (05) volume [ENUM] 0:low 1: mid 2:high
                def volumeName = 'unknown'
                def volumePct = -1
                (volumeName, volumePct) = findVolumeByTuyaValue( fncmd )
                if (volumeName != 'unknown') {
                    logDebug "confirmed volume ${volumeName} ${volumePct}% (${fncmd})"
                    sendVolumeEvent( volumePct )
                }
                break
            
            case 0x67 : // Neo Alarm Duration 0..1800 seconds
                logDebug "received Neo Alarm duration ${fncmd}"
            case TUYA_DP_DURATION :  // (07) duration [VALUE] in seconds
                logDebug "confirmed duration ${fncmd} s"
                sendEvent(name: "duration", value: fncmd, descriptionText: descriptionText, type: "physical")            
                break
            
            case 0x68 : // Neo Alarm On 0x01 Off 0x00
                logDebug "Neo Alarm status is ${fncmd}"
            case TUYA_DP_ALARM :    // (13) alarm [BOOL]
                def value = fncmd == 0 ? "off" : fncmd == 1 ? (state.lastTx["lastCommand"] ?: "n/a") : "unknown"
                if (settings?.logEnable) logInfo "confirmed alarm state ${value} (${fncmd})"
                if (value == "off") {
                    sendEvent(name: "status", value: "stopped", type: "physical")      
                    if (settings?.restoreAlarmSettings == true) {
                        if (device.currentValue("alarm", true) in ["beep", "playSound"]) {
                            runIn( 7, restoreDefaultSettings, [overwrite: true])
                        }
                    }
                }
                else {
                   unschedule(restoreDefaultSettings)
                   sendEvent(name: "status", value: "playing", type: "physical")
                }
                sendAlarmEvent(value)
                break
            case TUYA_DP_BATTERY :    // (15) battery [VALUE] percentage
                logDebug "received TUYA_DP_BATTERY event fncmd=${fncmd}"
                sendBatteryPercentageEvent(fncmd)
                break
            
            case 0x66 : // Neo Alarm Melody 18 Max ? -> fncmd+1 ? TODO
                logDebug "received Neo Alarm melody ${fncmd}"
            case TUYA_DP_MELODY :     // (21) melody [enum] 0..17
                if (settings?.logEnable) logInfo "confirmed melody ${melodiesOptions[fncmd]} (${fncmd})"
                sendEvent(name: "soundName", value: melodiesOptions[fncmd], descriptionText: descriptionText, type: "physical" )            
                break
            
            case 0x65 : // Neo Power Mode  ['battery_full', 'battery_high', 'battery_medium', 'battery_low', 'usb']
                if (settings?.logEnable) logInfo "Neo Power Mode is ${fncmd}"
                break
            case 0x69 : // Neo Temperature  ( x10 )
                if (settings?.logEnable) logInfo "Neo Temperature is ${fncmd/10.0} C (${fncmd})"
                sendTemperatureEvent( fncmd/10.0 )
                break
            case 0x6A : // Neo Humidity Level (x10 )
                if (settings?.logEnable) logInfo "Neo Humidity Level is ${fncmd/10.0} %RH (${fncmd})"
                sendHumidityEvent( fncmd/10.0 )
                break
            case 0x6B : // Neo Min Alarm Temperature -20 .. 80
                if (settings?.logEnable) logInfo "Neo Min Alarm Temperature is ${fncmd} C"
                break
            case 0x6C : // Neo Max Alarm Temperature -20 .. 80
                if (settings?.logEnable) logInfo "Neo Max Alarm Temperature is ${fncmd} C"
                break
            case 0x6D : // Neo Min Alarm Humidity 1..100
                if (settings?.logEnable) logInfo "Neo Min Alarm Humidity is ${fncmd} %RH"
                break
            case 0x6E : // Neo Max Alarm Humidity 1..100
                if (settings?.logEnable) logInfo "Neo Max Alarm Humidity is ${fncmd} %RH"
                break
            case 0x70 : // Neo Temperature Unit (F 0x00, C 0x01)
                if (settings?.logEnable) logInfo "Neo Temperature Unit is ${temperatureScaleOptions[safeToInt(fncmd).toString()]} (${fncmd})"
                break
            case 0x71 : // Neo Alarm by Temperature status
                if (settings?.logEnable) logInfo "Neo Alarm by Temperature status is ${disabledEnabledOptions[safeToInt(fncmd).toString()]} (${fncmd})"
                break
            case 0x72 : // Neo Alarm by Humidity status
                if (settings?.logEnable) logInfo "Neo Alarm by Humidity status is ${disabledEnabledOptions[safeToInt(fncmd).toString()]} (${fncmd})"
                break
            case 0x73 : // Neo ???
                if (settings?.logEnable) logInfo "Neo unknown parameter (x073) is ${fncmd}"
                break
            default :
                logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                break
        }
}


private wakeUpTuya() {
    logDebug "wakeUpTuya()"
    ping()
    //sendZigbeeCommands(zigbee.readAttribute(0x0000, 0x0005, [:], delay=50) )
    //sendZigbeeCommands(zigbee.command(0xEF00, 0x00, [:], delay=50, "00 00"))
}

private combinedTuyaCommands(String cmds) {
    if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats=[:] }
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] }
    return zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay=200, PACKET_ID + cmds ) 
}

private appendTuyaCommand(Integer dp, String dp_type, Integer fncmd) {
    Integer fncmdLen =  dp_type== DP_TYPE_VALUE? 8 : 2
    String cmds = zigbee.convertToHexString(dp, 2) + dp_type + zigbee.convertToHexString((int)(fncmdLen/2), 4) + zigbee.convertToHexString(fncmd, fncmdLen) 
    //logDebug "appendTuyaCommand = ${cmds}"
    return cmds
}

void sendSimpleTuyaCommand(Integer command, String payload) {
  Random rnd = new Random()
  String fullPayload = "00${HexUtils.integerToHexString(rnd.nextInt(255),1)}" + payload
  sendSimpleZigbeeCommands(zigbeeCommand(0x01, 0xEF00, command, 101, fullPayload))
  logDebug "Payload sent: ${fullPayload}"
}

void sendSimpleZigbeeCommands(ArrayList<String> cmd) {
    logDebug "sendZigbeeCommands(cmd=${cmd})"
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    sendHubCommand(allActions)
}

ArrayList<String> zigbeeCommand(Integer cluster, Integer command, Map additionalParams, int delay = 201, String... payload) {
    ArrayList<String> cmd = zigbee.command(cluster, command, additionalParams, delay, payload)
    cmd[0] = cmd[0].replace('0xnull', '0x01')
     
    return cmd
}

String integerToHexString(BigDecimal value, Integer minBytes, boolean reverse=false) {
    return integerToHexString(value.intValue(), minBytes, reverse=reverse)
}

ArrayList<String> zigbeeCommand(Integer endpoint, Integer cluster, Integer command, int delay = 203, String... payload) {
    zigbeeCommand(endpoint, cluster, command, [:], delay, payload)
}

ArrayList<String> zigbeeCommand(Integer endpoint, Integer cluster, Integer command, Map additionalParams, int delay = 204, String... payload) {
    String mfgCode = ""
    if(additionalParams.containsKey("mfgCode")) {
        mfgCode = " {${HexUtils.integerToHexString(HexUtils.hexStringToInt(additionalParams.get("mfgCode")), 2)}}"
    }
    String finalPayload = payload != null && payload != [] ? payload[0] : ""
    String cmdArgs = "0x${device.deviceNetworkId} 0x${HexUtils.integerToHexString(endpoint, 1)} 0x${HexUtils.integerToHexString(cluster, 2)} " + 
                       "0x${HexUtils.integerToHexString(command, 1)} " + 
                       "{$finalPayload}" + 
                       "$mfgCode"
    ArrayList<String> cmd = ["he cmd $cmdArgs", "delay $delay"]
    return cmd
}

String integerToHexString(Integer value, Integer minBytes, boolean reverse=false) {
    if(reverse == true) {
        return HexUtils.integerToHexString(value, minBytes).split("(?<=\\G..)").reverse().join()
    } else {
        return HexUtils.integerToHexString(value, minBytes)
    }
    
}

def offAlarm() {
    sendTuyaAlarm("off")
}

def onAlarm() {
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
    logDebug "swithing alarm ${commandName} (presetBeepAndChimeSettings = ${settings?.presetBeepAndChimeSettings})"
    String cmds = ""
    state.lastTx["lastCommand"] = commandName
    def mode = settings?.presetBeepAndChimeSettings ?: "fast"
    switch (mode) {
        case "none" :
            if (commandName != "off") {
                sendSimpleTuyaCommand(0x00, isNeo() ? "6801000101" : "0D01000101")
            }
            else {
                sendSimpleTuyaCommand(0x00, isNeo() ? "6801000100" : "0D01000100")
            }
            break
        case "fast" :
            wakeUpTuya()
            if (commandName != "off") {
                // volume
                def volumeName = settings?.alarmSoundVolume ?: 'high'
                def volumeTuya = volumeNameOptions.indexOf(volumeName)
                if (volumeTuya >= 0 && volumeTuya <=2) {
                    cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, volumeTuya as int ) 
                } 
                // duration
                def durationTuya = safeToInt( settings?.alarmSoundDuration )
                if (durationTuya >=1 && durationTuya <= TUYA_MAX_DURATION) {
                    cmds += appendTuyaCommand( isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, DP_TYPE_VALUE, durationTuya as int ) 
                }
                // melody
                def melodyName = settings?.alarmMelody ?: '12=Alarm Siren'
                def melodyTuya = melodiesOptions.indexOf(melodyName)
                if (melodyTuya >=0 && melodyTuya <= TUYA_MAX_MELODIES-1) {
                    cmds += appendTuyaCommand( isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, DP_TYPE_ENUM, melodyTuya as int) 
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
            break
        case "slow" :
            logWarn "NOT IMPLEMENTED!"
            break
    }
      
}

// capability "Tone"
def beep() {
    String cmds = ""
    state.lastTx["lastCommand"] = "beep"    
    logDebug "sending beep() beepVolume = ${settings?.beepVolume}"
    
    if (settings?.presetBeepAndChimeSettings == "none") {
        sendSimpleTuyaCommand(0x00, isNeo() ? "6801000101" : "0D01000101") // just turn the siren on!  // TODO!
    }
    else {
        wakeUpTuya()
        Integer volumeTuya; Integer volumePct
        (volumeTuya, volumePct) = findVolumeByName(settings?.beepVolume )
        if (volumeTuya >= 0 && volumeTuya <=2) {
            cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, volumeTuya as int) 
        }
        else {
            logWarn "volumeTuya <=2 is ${volumeTuya}, added cmds=${cmds} "
        }
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, DP_TYPE_VALUE, 1 ) 
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, DP_TYPE_ENUM, 2 ) 
        unschedule(restoreDefaultSettings)
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_ALARM : TUYA_DP_ALARM, DP_TYPE_BOOL, 1 )
        sendZigbeeCommands( combinedTuyaCommands(cmds) )
    }
}

def restoreDefaultSettings() {
    wakeUpTuya()
    String cmds = ""
    // restore alarm volume
    def volumeName = settings?.alarmSoundVolume ?: 'high'
    def volumeTuya = volumeNameOptions.indexOf(volumeName)
    if (volumeTuya >= 0 && volumeTuya <=2) {
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, volumeTuya as int ) 
    }    
    // restore alarm duration
    def durationTuya = safeToInt(settings?.alarmSoundDuration, TUYA_MAX_DURATION)
    if (durationTuya >=1 && durationTuya <= TUYA_MAX_DURATION) {
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, DP_TYPE_VALUE, durationTuya as int ) 
    }
    // restore alarm melody
    def melodyName = settings?.alarmMelody ?: '12=Alarm Siren'
    def melodyTuya = melodiesOptions.indexOf(melodyName)
    if (melodyTuya >=0 && melodyTuya <= TUYA_MAX_MELODIES-1) {
        cmds += appendTuyaCommand( isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, DP_TYPE_ENUM, melodyTuya as int) 
    }
    logDebug "restoring default settings volume=${volumeName}, duration=${durationTuya}, melody=${melodyName}"
    sendZigbeeCommands( combinedTuyaCommands(cmds) )    
}

//capability "AudioVolume" //Attributes: mute - ENUM ["unmuted", "muted"] volume - NUMBER, unit:%; Commands: mute() setVolume(volumelevel) volumelevel required (NUMBER) - Volume level (0 to 100) unmute() volumeDown() volumeUp()
def mute() {
    sendEvent(name: "mute", value: "muted", type: "digital")       
}

def unmute() {
    sendEvent(name: "mute", value: "unmuted", type: "digital")       
}

def getNearestTuyaVolumeLevel( volumelevel ) {
    def nearestlevel = 0
    if (volumelevel <= 33) nearestlevel = 33
    else if (volumelevel <= 66) nearestlevel = 66
    else nearestlevel = 100
    return nearestlevel
}

def setVolumeLevel( volumelevel ) {
    // - Volume level (0 to 100)
    String cmds = ""
    def nearestlevel =  getNearestTuyaVolumeLevel( volumelevel )
    if      (nearestlevel == 0 && device.currentValue("mute", true) == "unmuted")  mute()
    else if (nearestlevel != 0 && device.currentValue("mute", true) == "muted") unmute() 
    def volumeName
    def volumeTuya
    (volumeName, volumeTuya) =  findVolumeByPct( nearestlevel ) 
    logDebug "matched volumelevel=${volumelevel} to nearestLlevel=${nearestlevel} (volumeTuya=${volumeTuya})"

    if (settings?.presetBeepAndChimeSettings == "none") {
        switch(volumeName) {
            case "high":
                sendSimpleTuyaCommand(0x00, "0504000102")
                break
            case "medium":
                sendSimpleTuyaCommand(0x00, "0504000101")
                break
            default:
                sendSimpleTuyaCommand(0x00, "0504000100")
                break
          }
    }
    else {
    //state.volume = nearestlevel
        if (safeToInt(volumeTuya) >= 0) {
            cmds += appendTuyaCommand( isNeo() ? NEO_DP_VOLUME : TUYA_DP_VOLUME, DP_TYPE_ENUM, safeToInt(volumeTuya) ) 
        }
        logDebug "setting volume=${volumeName}"
        sendZigbeeCommands( combinedTuyaCommands(cmds) )
    }
}

def volumeDown() {
    setVolumeLevel( (device.currentValue("volume") ?: 0 ) - 34)
}

def volumeUp() {
    setVolumeLevel( (device.currentValue("volume") ?: 0 ) + 33)
}

def playSound(soundnumberPar=null, volumeLevelPar=null, durationPar=null) {
    logWarn "playSound: soundnumberPar=${soundnumberPar} volumeLevelPar=${volumeLevelPar} durationPar=${durationPar}"
    def soundnumber = safeToInt(soundnumberPar)
    def volumeLevel = safeToInt(volumeLevelPar)
    def duration = safeToInt(durationPar)
    wakeUpTuya()
    String cmds = ""
    def volumeName; def volumeTuya; def volumePct
    if (soundnumber == null || soundnumber <= 0)  {    // use the default melody
        soundnumber = melodiesOptions.indexOf(settings?.playSoundMelody ?: TUYA_DEFAULT_MELODY_NAME ) + 1
        logWarn "playSound: using the default soundnumber ${soundnumber}"
    }
    int soundNumberIndex = safeToInt(soundnumber)
    soundNumberIndex = soundNumberIndex < 1 ? 1 : soundNumberIndex > TUYA_MAX_MELODIES ? TUYA_MAX_MELODIES : soundNumberIndex; 
    soundNumberIndex -= 1    // Tuya parameter is zero based !
    //
    if (volumeLevel == null || volumeLevel <= 0) {    // use the default playSoundVolume    
        volumeName = settings?.playSoundVolume ?: TUYA_DEFAULT_VOLUME_NAME
        (volumeTuya, volumePct) = findVolumeByName( volumeName )        
        logWarn "playSound: using default Chime volume ${volumeName} (${volumeTuya})"
    }
    else {
        def nearestVolume = getNearestTuyaVolumeLevel( volumeLevel )
        (volumeName, volumeTuya) =  findVolumeByPct( nearestVolume ) 
    }
    //
    if (duration == null || duration <= 0) {
        duration = settings?.playSoundDuration ?: TUYA_DEFAULT_DURATION as int
        logWarn "playSound: using the default duration ${duration}"
    }
    else {
        duration = duration <1 ? 1 : duration > TUYA_MAX_DURATION ? TUYA_MAX_DURATION : duration as int
    }
    if (volumeTuya < 0) {
        volumeTuya = 1 // medium
        logWarn "playSound: using the default volumeTuya ${volumeTuya}"
    }
    state.lastTx["lastCommand"] = "playSound"
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
    if (((device.currentValue("volume") ?: 0 ) as int) != (volume as int)) {
        if (txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    }
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

// TODO - use the main code!
def sendTemperatureEvent( temperature, isDigital=false ) {
    def map = [:]
    map.name = "temperature"
    def Scale = location.temperatureScale
    if (Scale == "F") {
        temperature = (temperature * 1.8) + 32
        map.unit = "\u00B0"+"F"
    }
    else {
        map.unit = "\u00B0"+"C"
    }
    def tempCorrected = temperature
    map.value  =  Math.round((tempCorrected - 0.05) * 10) / 10
    map.type = isDigital == true ? "digital" : "physical"
    map.descriptionText = "${map.name} is ${tempCorrected} ${map.unit}"
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    sendEvent(map)
}

// TODO - use the main code!
def sendHumidityEvent( humidity, isDigital=false ) {
    def map = [:]
    def humidityAsDouble = safeToDouble(humidity) +safeToDouble(settings?.humidityOffset)
    humidityAsDouble = humidityAsDouble < 0.0 ? 0.0 : humidityAsDouble > 100.0 ? 100.0 : humidityAsDouble
    map.value = Math.round(humidityAsDouble)
    map.name = "humidity"
    map.unit = "% RH"
    map.type = isDigital == true ? "digital" : "physical"
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${humidityAsDouble.round(1)} ${map.unit}"
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    sendEvent(map)
}


void setMelody( alarmType, melodyNumber ) {
    int index = safeToInt( melodyNumber )
    if (index < 1 || index> TUYA_MAX_MELODIES) {
        logWarn "melody number must be between 1 and ${TUYA_MAX_MELODIES}"
        return
    }
    index = index - 1
    if (alarmType == 'alarm') {
        device.updateSetting("alarmMelody", [value:melodiesOptions[index], type:"enum"])
        sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, 2), DP_TYPE_ENUM, zigbee.convertToHexString(index, 2)))
    }
    else if (alarmType == 'chime') {
        device.updateSetting("playSoundMelody", [value:melodiesOptions[index], type:"enum"])
        sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(isNeo() ? NEO_DP_MELODY :TUYA_DP_MELODY, 2), DP_TYPE_ENUM, zigbee.convertToHexString(index, 2)))
    }
    else {
        logWarn "alarmType must be one of ${soundTypeOptions}"
        return
    }    
    logDebug "setMelody ${alarmType} ${melodiesOptions[index]} (${index})"
}

void setDuration( alarmType, alarmLength) {
    int duration = safeToInt( alarmLength )
    if (duration > TUYA_MAX_DURATION) duration = TUYA_MAX_DURATION
    if (duration < 1 ) duration = 1
    logDebug "setAlarmDuration ${duration}"
    if (alarmType == 'alarm') {
        device.updateSetting("alarmSoundDuration", [value:duration, type:"number"])
        sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, 2), DP_TYPE_VALUE, zigbee.convertToHexString(duration, 8)))
    }
    else if (alarmType == 'chime') {
        device.updateSetting("playSoundDuration", [value:duration, type:"number"])
        sendZigbeeCommands( sendTuyaCommand(zigbee.convertToHexString(isNeo() ? NEO_DP_DURATION : TUYA_DP_DURATION, 2), DP_TYPE_VALUE, zigbee.convertToHexString(duration, 8)))
    }
    else {
        logWarn "alarmType must be one of ${soundTypeOptions}"
    }
}

void setVolume( volumeType, volumeName) {
    if (!(volumeType in volumeTypeOptions)) {
        logWarn "setVolume not supported type ${volumeType}, must be one of ${volumeTypeOptions}"
        return
    }
    if (!(volumeName in volumeNameOptions)) {
        logWarn "setVolume not supported type ${volumeType}, must be one of ${volumeNameOptions}"
        return
    }
    def volumePct = volumeMapping[volumeName].find{it.key=='volume'}.value
    def tuyaValue = volumeMapping[volumeName].find{it.key=='tuya'}.value
    //log.trace "volumeType=${volumeType} volumeName=${volumeName} volumePct=${volumePct}, tuyaValue=${tuyaValue} "
    switch (volumeName) {
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
    logDebug "setVolume ${volumeType} ${volumeName} ${volumePct}% (Tuya:${tuyaValue})"
}










def configureDeviceAlarm() {
    ArrayList<String> cmds = []
    logDebug 'configureDeviceAlarm() '
    /*
        // https://forum.phoscon.de/t/aqara-tvoc-zhaairquality-data/1160/21
        final int tScale = (settings.temperatureScale as Integer) ?: TemperatureScaleOpts.defaultValue
        final int tUnit =  (settings.tVocUnut as Integer) ?: TvocUnitOpts.defaultValue
        logDebug "setting temperatureScale to ${TemperatureScaleOpts.options[tScale]} (${tScale})"
        int cfg = tUnit
        cfg |= (tScale << 4)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0114, DataType.UINT8, cfg, [mfgCode: 0x115F], delay=200)
        cmds += zigbee.readAttribute(0xFCC0, 0x0114, [mfgCode: 0x115F], delay=200)    
*/
    return cmds
}


def initializeDeviceAlarm() {
    ArrayList<String> cmds = []
    // nothing to initialize?
    return cmds
}

void updatedAlarm() {
    if (isVINDSTYRKA()) {
        final int intervalAirQuality = (settings.airQualityIndexCheckInterval as Integer) ?: 0
        if (intervalAirQuality > 0) {
            logInfo "updatedAirQuality: scheduling Air Quality Index check every ${intervalAirQuality} seconds"
            scheduleAirQualityIndexCheck(intervalAirQuality)
        }
        else {
            unScheduleAirQualityIndexCheck()
            logInfo "updatedAirQuality: Air Quality Index polling is disabled!"
            // 09/02/2023
            device.deleteCurrentState("airQualityIndex")
        }
            
    }
    else {
        logDebug "updatedAirQuality: skipping airQuality polling "
    }
}

def refreshAlarm() {
    List<String> cmds = []
    if (isAqaraTVOC()) {
            // TODO - check what is available for VINDSTYRKA
	        cmds += zigbee.readAttribute(0x042a, 0x0000, [:], delay=200)                    // pm2.5    attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; 3:Tolerance
	        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay=200)      // tVOC   !! mfcode="0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value;
    }
        else if (false) {
            // TODO - check what is available for Aqara 
        }
        else {
            // TODO - unknown AirQuaility sensor - try all ??
        }
    
    logDebug "refreshAirQuality() : ${cmds}"
    return cmds
}

def initVarsAlarm(boolean fullInit=false) {
    logDebug "initVarsAlarm(${fullInit})"
    device.updateSetting("beepVolume", [value:"low", type:"enum"])
    device.updateSetting("alarmMelody",        [value:'12=Alarm Siren', type:"enum"])
    device.updateSetting("alarmSoundVolume",   [value:'high', type:"enum"])
    device.updateSetting("alarmSoundDuration", [value:TUYA_MAX_DURATION, type:"number"])
    device.updateSetting("playSoundMelody",    [value:TUYA_DEFAULT_MELODY_NAME, type:"enum"]) 
    device.updateSetting("playSoundVolume",    [value: TUYA_DEFAULT_VOLUME_NAME, type:"enum"])
    device.updateSetting("playSoundDuration",  [value:TUYA_DEFAULT_DURATION, type:"number"])
    device.updateSetting("restoreAlarmSettings", false)
    device.updateSetting("presetBeepAndChimeSettings", [value: "fast", type:"enum"])
}

void initEventsAlarm(boolean fullInit=false) {
    // nothing to do ?
     unmute()
}
