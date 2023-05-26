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
 * ver. 1.2.0 2023-01-22 kkossev  - (dev. branch) _TZE200_d0yu2xgi (NEO) experimental support including temperature and humidity; added separate preferences for alarm and Melody, Volume and Duration
 * ver. 1.2.1 2023-05-26 kkossev  - added _TZE204_t1blo2bj in Neo group; installed() bug fix;
 *
 *                                  TODO: _TZE204_t1blo2bj control @abraham : https://community.hubitat.com/t/release-tuya-smart-siren-zigbee-driver/91772/67?u=kkossev
 *                                  TODO: add on/off preference selection like Zoos S2 Multisiren https://community.hubitat.com/t/hsm-custom-rule-bugs/117061/6?u=kkossev 
 *                                  TODO: mute/unmute
 *
*/

def version() { "1.2.1" }
def timeStamp() {"2023/05/26 8:13 AM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils

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
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        
        attribute "duration", "number"
        attribute "Info", "text"
        
        command "configure",  [[name:"Will load the DEFAULT settings!", type: "TEXT"]]
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
        if (debug==true) {
            command "test"
        }
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_t1blo2bj", deviceJoinName: "Tuya NEO Smart Siren"          // vendor: 'Neo', model: 'NAS-AB02B2'
        // not working with this driver - use Markus's driver instead
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A" ,model:"TS0601", manufacturer:"_TZE200_d0yu2xgi", deviceJoinName: "Tuya NEO Smart Siren T&H"      // Neo NAS-AB02B0
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A" ,model:"TS0601", manufacturer:"_TYST11_d0yu2xgi", deviceJoinName: "Tuya NEO Smart Siren T&H"      // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A" ,model:"TS0601", manufacturer:        "d0yu2xgi", deviceJoinName: "Tuya NEO Smart Siren T&H"      // not tested
        // not working with this driver    // https://github.com/zigpy/zha-device-handlers/issues/1379
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A" ,model:"TS0601", manufacturer:"_TZE204_t1blo2bj", deviceJoinName: "Tuya NEO Smart Siren"          // https://community.hubitat.com/t/release-tuya-smart-siren-zigbee-driver/91772/67?u=kkossev

        // https://github.com/zigpy/zha-device-handlers/issues/1379#issuecomment-1077772021 
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display sensor states in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        //
        input (name: "beepVolume", type: "enum", title: "<b>Beep Volume</b>", description:"<i>Select the volume used in the Beep command</i>", defaultValue: "low", options: volumeNameOptions)
        //
        input (name: "alarmMelody", type: "enum", title: "<b>Alarm default Melody</b>", description:"<i>Select the melody used in the Alarm commands</i>", defaultValue: '12=Alarm Siren', options: melodiesOptions)
        input (name: "alarmSoundVolume", type: "enum", title: "<b>Alarm default Volume</b>", description:"<i>Select the volume used in the Alarm commands</i>", defaultValue: 'high', options: volumeNameOptions)
        input (name: "alarmSoundDuration", type: "number", title: "<b>Alarm default Duration</b>, seconds", description: "<i>Select the duration used in the Alarm commands, seconds</i>", range: "1..$TUYA_MAX_DURATION", defaultValue: TUYA_MAX_DURATION)
        //
        input (name: "playSoundMelody", type: "enum", title: "<b>Play Sound (Chime) default Melody</b>", description:"<i>Select the default melody used in the playSound (Chime) command</i>", defaultValue: TUYA_DEFAULT_MELODY, options: melodiesOptions)
        input (name: "playSoundVolume", type: "enum", title: "<b>Play Sound (Chime) default Volume</b>", description:"<i>Select the default volume used in the playSound (Chime) command</i>", defaultValue: TUYA_DEFAULT_VOLUME, options: volumeNameOptions)
        input (name: "playSoundDuration", type: "number", title: "<b>Play Sound (Chime) default Duration</b>, seconds", description: "<i>Select the default duration used in the playSound (Chime) command, seconds</i>", range: "1..$TUYA_MAX_DURATION", defaultValue: TUYA_DEFAULT_DURATION)
        input (name: "advancedOptions", type: "bool", title: "<b>Advanced options</b>", description: "<i>These are automatically set up in an optimal way</i>", defaultValue: false)
        if (advancedOptions == true) {
            input (name: "restoreAlarmSettings", type: "bool", title: "<b>Restore Default Alarm Settings</b>", description: "<i>After playing Beep or Chime sounds, the default Alarm settings will be restored after 7 seconds </i>", defaultValue: false)
            input (name: "presetBeepAndChimeSettings", type: "enum", title: "<b>Preset Beep and Chime Settings</b>", description: "<i>Before playing Beep or Chime sounds, the preset Beep/Chime settings will be restored first</i>", defaultValue: "fast", options:["fast", /*"slow",*/ "none"])
        } 
    }
}


def isNeo() {(device.getDataValue("manufacturer") in ['_TZE200_d0yu2xgi', '_TZE200_d0yu2xgi', 'd0yu2xgi', '_TZE204_t1blo2bj']) || (debug == true)}

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
            volumeTuya = safeToInt(v.tuya)
            volumePct = safeToInt(v.volume)
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
                def value = fncmd == 0 ? "off" : fncmd == 1 ? state.lastCommand : "unknown"
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
                getBatteryPercentageResult( fncmd * 2)
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
    logDebug "swithing alarm ${commandName} (presetBeepAndChimeSettings = ${settings?.presetBeepAndChimeSettings})"
    String cmds = ""
    state.lastCommand = commandName
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
    state.lastCommand = "beep"    
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

def playSound(soundnumber=null, volumeLevel=null, duration=null) {
    wakeUpTuya()
    String cmds = ""
    def volumeName; def volumeTuya; def volumePct
    if (soundnumber == null) {    // use the default melody
        soundnumber = melodiesOptions.indexOf(settings?.playSoundMelody ?: TUYA_DEFAULT_MELODY ) + 1
    }
    int soundNumberIndex = safeToInt(soundnumber)
    soundNumberIndex = soundNumberIndex < 1 ? 1 : soundNumberIndex > TUYA_MAX_MELODIES ? TUYA_MAX_MELODIES : soundNumberIndex; 
    soundNumberIndex -= 1    // Tuya parameter is zero based !
    //
    if (volumeLevel == null) {    // use the default playSoundVolume    
        volumeName = settings?.playSoundVolume ?: TUYA_DEFAULT_VOLUME
        (volumeTuya, volumePct) = findVolumeByName( volumeName )        
        logDebug "volumeLevel parameter is null, using default Chime volume ${volumeName} (${volumeTuya})"
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

// called on initial install of device during discovery
// also called from initialize() in this driver!
def installed() {
    log.info "${device.displayName} installed()"
    unschedule()
    unmute()
    sendEvent(name: "Info", value:"installed", isStateChange: true, type: "digital")
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
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        state.remove('setMelody')
        state.remove('setDuration')
        state.remove('setVolume')
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
        sendEvent(name: "Info", value: "driver updated to new version ${driverVersionAndTimeStamp()}", isStateChange: true, type: "digital")
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
        sendEvent(name: "soundEffects", value: JsonOutput.toJson(melodiesOptions), isStateChange: true, type: "digital")
    }
    //
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0
    state.lastCommand = "unknown"

    if (fullInit == true || settings?.logEnable == null)          device.updateSetting("logEnable", true)
    if (fullInit == true || settings?.txtEnable == null)          device.updateSetting("txtEnable", true)
    /*if (fullInit == true || settings?.beepVolume == null)*/         device.updateSetting("beepVolume", [value:"low", type:"enum"])
    /*if (fullInit == true || settings?.alarmMelody == null)*/         device.updateSetting("alarmMelody",        [value:'12=Alarm Siren', type:"enum"])
    /*if (fullInit == true || settings?.alarmSoundVolume == null)*/    device.updateSetting("alarmSoundVolume",   [value:'high', type:"enum"])
    /*if (fullInit == true || settings?.alarmSoundDuration == null)*/  device.updateSetting("alarmSoundDuration", [value:TUYA_MAX_DURATION, type:"number"])
    /*if (fullInit == true || settings?.playSoundMelody == null)*/     device.updateSetting("playSoundMelody",    [value:TUYA_DEFAULT_MELODY, type:"enum"]) 
    /*if (fullInit == true || settings?.playSoundVolume == null)*/     device.updateSetting("playSoundVolume",    [value: TUYA_DEFAULT_VOLUME, type:"enum"])
    /*if (fullInit == true || settings?.playSoundDuration == null)*/   device.updateSetting("playSoundDuration",  [value:TUYA_DEFAULT_DURATION, type:"number"])
    if (fullInit == true || settings?.advancedOptions == null) device.updateSetting("advancedOptions", false)
    /*if (fullInit == true || settings?.restoreAlarmSettings == null)*/ device.updateSetting("restoreAlarmSettings", false)
    /*if (fullInit == true || settings?.presetBeepAndChimeSettings == null)*/  device.updateSetting("presetBeepAndChimeSettings", [value: "fast", type:"enum"])
    
   
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
    logDebug "wakeUpTuya()"
    sendZigbeeCommands(zigbee.readAttribute(0x0000, 0x0005, [:], delay=50) )
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

