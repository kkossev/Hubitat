/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName *//**
 *  Tuya Zigbee Rain Sensor - driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 * 	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * 	in compliance with the License. You may obtain a copy of the License at:
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * 	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * 	for the specific language governing permissions and limitations under the License.
 *
 * ver. 3.0.0  2024-08-08 kkossev  - first test version
 * ver. 3.0.1  2024-08-09 kkossev  - (dev.branch) added capability 'WaterSensor'; rainSensorVoltage scale 1000; illuminance changed to illuminanceVoltage and scale 1000; 
 *                                   
 *                                   TODO: HPM
 */

static String version() { "3.0.1" }
static String timeStamp() {"2024/08/09 12:36 PM"}

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true    // disable it for production

import groovy.transform.Field
import groovy.transform.CompileStatic

#include kkossev.deviceProfileLib
#include kkossev.commonLib
#include kkossev.batteryLib
#include kkossev.iasLib
//#include kkossev.illuminanceLib

deviceType = "RainSensor"
@Field static final String DEVICE_TYPE = "RainSensor"

metadata {
    definition (
        name: 'Tuya Zigbee Rain Sensor',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Rain%20Sensor/Tuya_Zigbee_Rain_Sensor_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        capability "WaterSensor"        

        attribute 'dropletDetectionState',       'enum',    ['off', 'on']
        attribute 'battery',                     'number'
        attribute 'illuminanceVoltage',          'number'
        attribute 'averageLightIntensity20mins', 'number'
        attribute 'todaysMaxLightIntensity',     'number'
        attribute 'cleaningReminder',            'enum',    ['off', 'on']
        attribute 'rainSensorVoltage',           'number'

       // no commands

        // itterate through all the figerprints and add them on the fly
        deviceProfilesV3.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                if (profileMap.device?.isDepricated != true) {
                    profileMap.fingerprints.each {
                        fingerprint it
                    }
                }
            }
        }        
    }

    preferences {
        if (device) {
            // input(name: 'info',    type: 'hidden', title: "<a href='https://github.com/kkossev/Hubitat/wiki/Tuya-Multi-Sensor-4-In-1' target='_blank'><i>For more info, click on this link to visit the WiKi page</i></a>")
        }
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables events logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
        // the rest of the preferences are inputIt from the deviceProfileLib and from the included libraries
    }
}

@Field static String ttStyleStr = '<style>.tTip {display:inline-block;border-bottom: 1px dotted black;}.tTip .tTipText {display:none;border-radius: 6px;padding: 5px 0;position: absolute;z-index: 1;}.tTip:hover .tTipText {display:inline-block;background-color:red;color:red;}</style>'

@Field static final Map deviceProfilesV3 = [
    // https://www.aliexpress.us/item/3256807083309300.html
    // https://www.aliexpress.com/item/1005007269233710.html
    // https://community.hubitat.com/t/new-tuya-zigbee-light-and-rain-sensor/141057/6?u=kkossev
    // https://github.com/Koenkk/zigbee2mqtt/issues/23532 
    'TUYA_RAIN_SENSOR'  : [
            description   : 'Tuya Zigbee Rain Sensor',
            models        : ['TS0601'],
            device        : [type: 'Sensor', isIAS:true, powerSource: 'battery', isSleepy:true],    // check powerSource
            capabilities  : ['Battery': true],
            preferences   : [],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences', 'printFingerprints':'printFingerprints', 'printPreferences':'printPreferences'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,0001,0500,EF00", outClusters:"0003,0004,0006,1000,000A,0019", model:"TS0207", manufacturer:"_TZ3210_tgvtvdoc", controllerType: "ZGB", deviceJoinName: 'Tuya Zigbee Rain Sensor'],
                // for tests only!  TOBEDEL
                [profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,0001,0500,EF00", outClusters:"0003,0004,0006,1000,000A,0019", model:"TS0222", manufacturer:"_TYZB01_4mdqxxnn", controllerType: "ZGB", deviceJoinName: 'TEST Tuya Zigbee Rain Sensor'],
            ],
            tuyaDPs:        [
                [dp:1,   name:'dropletDetectionState',       type:'enum',    rw: 'ro', defVal:'0', map:[0:'off', 1:'on'], description:'Droplet Detection State'],
                [dp:4,   name:'battery',                     type:'number',  rw: 'ro', unit:'%', description:'Battery level'],
                [dp:101, name:'illuminanceVoltage',          type:'decimal', rw: 'ro', unit:'V', scale:1000, description:'Illuminance voltage'],
                [dp:102, name:'averageLightIntensity20mins', type:'decimal',  rw: 'ro', unit:'V', scale:1000, description:'20 mins average light intensity'],
                [dp:103, name:'todaysMaxLightIntensity',     type:'decimal',  rw: 'ro', unit:'V', scale:1000, description:'Todays max light intensity'],
                [dp:104, name:'cleaningReminder',            type:'enum',    rw: 'ro', defVal:'0', map:[0:'off', 1:'on'], description:'Cleaning reminder'],
                [dp:105, name:'rainSensorVoltage',           type:'decimal', rw: 'ro', unit:'V', scale:1000, description:'Rain Sensor Voltage'],
            ],
            //refresh:        ['refreshFantem'],
            configuration : ['battery': false],
            deviceJoinName: 'Tuya Zigbee Rain Sensor'
    ]
]


// called from standardProcessTuyaDP in the commonLib for each Tuya dp report in a Zigbee message
// should always return true, as we are processing all the dp reports here
boolean customProcessTuyaDp(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) {
    logDebug "customProcessTuyaDp: dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len} descMap.data = ${descMap?.data}"
    if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {
        return true      // sucessfuly processed from the deviceProfile 
    }

    logWarn "<b>NOT PROCESSED from deviceProfile</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
    localProcessTuyaDP(descMap, dp, dp_id, fncmd, dp_len)
    return true
}

void localProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len) {
    switch (dp) {
        default :
            logDebug "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            break
    }
}


// called from processFoundItem in the deviceProfileLib
void customProcessDeviceProfileEvent(final Map descMap, final String name, valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        default :
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
            logTrace "event ${name} sent w/ value ${valueScaled}"
            logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ?
            break
    }
}

public void customParseIasMessage(final String description) {
    Map zs = zigbee.parseZoneStatusChange(description)
    if (zs == null) {
        logWarn "customParseIasMessage: zs is null!"
        return
    }
    if (zs.alarm1Set == true) {
        logDebug "customParseIasMessage: Alarm 1 is set"
        sendWaterEvent('wet')
    }
    else {
        logDebug "customParseIasMessage: Alarm 1 is cleared"
        sendWaterEvent('dry')
    }
}

void sendWaterEvent( String value, boolean isDigital=false) {
    def type = isDigital == true ? "digital" : "physical"
    String descriptionText
    switch (value) {
        case 'checking' :
            descriptionText = "${device.displayName} checking"
            break
        case 'tested' :
            descriptionText = "${device.displayName} is tested"
            break
        case 'wet' :
            // send 'wet' without delays and additional checks
            descriptionText = "<b>${device.displayName} is wet</b>"
            break
        case 'dry' :
            descriptionText = "${device.displayName} is dry"
            break
        case 'unknown' :
            // 'unknown' is sent when the water leak sensor healthStatus goes in offline state
            descriptionText = "${device.displayName} status is unknown"
            break
        default :
            log.warn "sendWaterEvent: unprocessed water event '${value}'"
            return
    }
    if (isDigital == true) descriptionText += " [digital]"
    if (settings?.txtEnable==true) log.info "$descriptionText"    // includes deviceName
    sendEvent(name: "water", value: value, descriptionText: descriptionText, type: type , isStateChange: true)    
}


List<String> refreshFantem() {
    List<String>  cmds = zigbee.command(0xEF00, 0x07, '00')    // Fantem Tuya Magic
    return cmds
}

List<String> customRefresh() {
    logDebug "customRefresh()"
    List<String> cmds = []
    List<String> devProfCmds = refreshFromDeviceProfileList()
    if (devProfCmds != null && !devProfCmds.isEmpty()) {
        cmds += devProfCmds
    }
    return cmds
}

void customUpdated() {
    logDebug "customUpdated()"
    List<String> cmds = []
    if (settings?.forcedProfile != null) {
        if (this.respondsTo('getProfileKey') == false) {
            logWarn "getProfileKey() is not defined in the driver"
        }
        else {
            logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
            if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
                logInfo "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
                state.deviceProfile = getProfileKey(settings?.forcedProfile)
                initializeVars(fullInit = false)
                resetPreferencesToDefaults(debug = true)
                logInfo 'press F5 to refresh the page'
            }
        }
    }
    /* groovylint-disable-next-line EmptyElseBlock */
    else {
        logDebug "forcedProfile is not set"
    }

    // Itterates through all settings
    cmds += updateAllPreferences()  // defined in deviceProfileLib
    sendZigbeeCommands(cmds)
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null || state.deviceProfile == '' || state.deviceProfile == 'UNKNOWN') {
        setDeviceNameAndProfile('TS0601', '_TZE200_lvkk0hdg')               // in deviceProfileiLib.groovy
    }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
}

void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents()"
    if ((device.currentState('dropletDetectionState')?.value == null)) { sendEvent(name: 'dropletDetectionState', value: 'off', type:'digital') }
    if ((device.currentState('battery')?.value == null)) { sendEvent(name: 'battery', value: 0, unit:'%', type:'digital') }
    if ((device.currentState('illuminanceVoltage')?.value == null)) { sendEvent(name: 'illuminance', value: 0.0, unit:'V', type:'digital') }
    if ((device.currentState('averageLightIntensity20mins')?.value == null)) { sendEvent(name: 'averageLightIntensity20mins', value: 0.0, unit:'V', type:'digital') }
    if ((device.currentState('todaysMaxLightIntensity')?.value == null)) { sendEvent(name: 'todaysMaxLightIntensity', value: 0.0, unit:'V', type:'digital') }
    if ((device.currentState('cleaningReminder')?.value == null)) { sendEvent(name: 'cleaningReminder', value: 'off', type:'digital') }
    if ((device.currentState('rainSensorVoltage')?.value == null)) { sendEvent(name: 'rainSensorVoltage', value: 0.0, unit:'V', type:'digital') }
    if ((device.currentState('water')?.value == null)) { sendEvent(name: 'water', value: 'unknown', unit:'', type:'digital') }
}

void test(String par) {
    long startTime = now()
    logDebug "test() started at ${startTime}"
    //parse('catchall: 0104 EF00 01 01 0040 00 7770 01 00 0000 02 01 00556701000100')
    def parpar = 'catchall: 0104 EF00 01 01 0040 00 7770 01 00 0000 02 01 00556701000100'

    for (int i=0; i<100; i++) { 
        testFunc(parpar) 
    }

    long endTime = now()
    logDebug "test() ended at ${endTime} (duration ${endTime - startTime}ms)"
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

