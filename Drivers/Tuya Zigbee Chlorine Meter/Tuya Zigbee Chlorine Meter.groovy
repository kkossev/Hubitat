/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName *//**
 *  Tuya Zigbee Chlorine Meter- driver for Hubitat Elevation
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
 * ver. 3.3.0  2024-08-03 kkossev  - first test version
 *                                   
 *                                   TODO:
 */

static String version() { "3.3.0" }
static String timeStamp() {"2024/08/18 11:41 PM"}

@Field static final Boolean _DEBUG = true
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true    // disable it for production

/*
import groovy.transform.Field
import groovy.transform.CompileStatic
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
*/

#include kkossev.deviceProfileLib
#include kkossev.commonLib

deviceType = "MultiMeter"
@Field static final String DEVICE_TYPE = "MultiMeter"

metadata {
    definition (
        name: 'Tuya Zigbee Chlorine Meter',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Chlorine%20Meter/Tuya_Chlorine_meter_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        // no standard capabilities

        attribute 'tds', 'number'                                 // Total Dissolved Solids
        attribute 'temperature', 'number'                         // Temperature
        attribute 'battery', 'number'                             // Battery level remaining
        attribute 'ph', 'number'                                  // pH value
        attribute 'ec', 'number'                                  // Electrical conductivity
        attribute 'orp', 'number'                                 // Oxidation Reduction Potential value
        attribute 'freeChlorine', 'number'                        // Free chlorine value
        attribute 'backlightvalue', 'number'                      // Bbacklight value
        attribute 'phMmax', 'number'                              // pH maximal value
        attribute 'phMmin', 'number'                              // pH minimal value
        attribute 'ecMmax', 'number'                              // Electrical Conductivity maximal value
        attribute 'ecMmin', 'number'                              // Electrical Conductivity minimal value
        attribute 'orpMmax', 'number'                             // Oxidation Reduction Potential maximal value
        attribute 'orpMmin', 'number'                             // Oxidation Reduction Potential minimal value
        attribute 'freeChlorineMax', 'number'                     // Free Chlorine maximal value
        attribute 'freeChlorineMin', 'number'                     // Free Chlorine minimal value
        attribute 'salinity', 'number'                            // Salt value
        attribute 'backlightvalue', 'number'                      // Bbacklight value


       // no commands
       if (_DEBUG) {
            command 'tuyaDataQuery'
        }

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

/*
Measures :

    PH : test ranges: 0.0-14.0ph; Resolution: 0.1ph; Accuracy: ±0.1ph
    CL : test ranges: 0.0-4.0mg/L; Resolution: 0.1mg/L; Accuracy: ±0.1mg/L
    Salt : test ranges: 0-999ppm, 1000-9990ppm; Resolution: 1ppm, 10ppm; Accuracy: ±2% F.S
    EC : test ranges: 0-2000us/c m , 2000-9990uS/c m，10.01- 19.99mS/c m; Resolution: 1uS/c m 10uS/c m 0.01mS/C M; Accuracy: ±2% F.S.
    Total Dissolved Solids : test ranges: 0-999ppm，1000- 9990pp, Resolution: 1ppm 10ppm; Accuracy: ±2% F.S.
    ORP : test ranges: -999mv ~+999mv; Resolution: 1mv; Accuracy:15mv
    Temperature : test ranges: 0.0℃-50.0℃ 32.0℉-122.0℉; Resolution: 0.1℃/0.1℉; Accuracy: ±0.5℃
*/

@Field static final Map deviceProfilesV3 = [
    // https://github.com/Koenkk/zigbee2mqtt/issues/18704
    // https://community.home-assistant.io/t/pool-monitoring-device-yieryi-ble-yl01-zigbee-ph-orp-free-chlorine-salinity-etc/659545/10
    // https://github.com/Koenkk/zigbee2mqtt/issues/18704#issuecomment-1732263086 
    // https://github.com/zigbeefordomoticz/z4d-certified-devices/blob/e65463300dda776145ca4b2953ebe162c2f60b3d/z4d_certified_devices/Certified/Tuya/TS0601-BLE-YL01.json#L7
    'CHLORINE_METER_BLE_YL01'  : [
            description   : 'BLE_YL01 Tuya Zigbee Chlorine Meter',
            models        : ['TS0601'],
            device        : [type: 'Sensor', powerSource: 'dc', isSleepy:false],    // check powerSource
            capabilities  : ['Battery': true, 'TemperatureMeasurement': true],
            preferences   : ['phMmax': '106', 'phMmin': '107', 'ecMmax': '108', 'ecMmin': '109', 'orpMmax': '110', 'orpMmin': '111', 'freeChlorineMax': '112', 'freeChlorineMin': '113'],
            //     "Param": {         "tempCompensation": 0,        "ph7Compensation": 0,        "ecCompensation": 0,        "orpCompensation": 0    }
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences', 'printFingerprints':'printFingerprints', 'printPreferences':'printPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE200_v1jqz5cy', deviceJoinName: 'BLE_YL01 Tuya Zigbee Chlorine Meter'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE200_d9mzkhoq', deviceJoinName: 'BLE_YL01 Tuya Zigbee Chlorine Meter'],
            ],
            tuyaDPs:        [
                [dp:1,   name:'tds',                  type:'number',  rw: 'ro',  scale:1,   unit:'ppm',   description:'Total Dissolved Solids'],
                [dp:2,   name:'temperature',          type:'decimal', rw: 'ro',  scale:10,  unit:'C',     description:'Temperature'],
                [dp:7,   name:'battery',              type:'number',  rw: 'ro',  scale:1,   unit:'%',     description:'Battery level remaining'],
                [dp:10,  name:'ph',                   type:'decimal', rw: 'ro',  scale:100, unit:'pH',    description:'pH value'],     // 'pH value, if the pH value is lower than 6.5, it means that the water quality is too acidic and has impurities, and it is necessary to add disinfectant water for disinfection
                [dp:11,  name:'ec',                   type:'decimal', rw: 'ro',  scale:1,   unit:'µS/cm', description:'Electrical conductivity'],
                [dp:101, name:'orp',                  type:'decimal', rw: 'ro',  scale:1,   unit:'mV',    description:'Oxidation Reduction Potential value'],   // 'Oxidation Reduction Potential value. If the ORP value is above 850mv, it means that the disinfectant has been added too much, and it is necessary to add water or change the water for neutralization. If the ORP value is below 487mv, it means that too little disinfectant has been added and the pool needs to be disinfected again'
                [dp:102, name:'freeChlorine',         type:'decimal', rw: 'ro',  scale:1,   unit:'mg/L',  description:'Free chlorine value'],   // The water in the swimming pool should be between 6.5-8ph and ORP should be between 487-840mv, and the chlorine value will be displayed normally. Chlorine will not be displayed if either value is out of range
                // "67": { "sensor_type": "phCalibration1" },
                // "68": { "store_tuya_attribute": "backlight_status", "EvalExp": "(value)" },
                // "69": { "store_tuya_attribute": "backlight_level", "EvalExp": "(value)" },
                [dp:105, name:'backlightvalue',       type:'number',  rw: 'ro',  scale:1,   unit:'',      description:'Backlight value'],      // unknown
                [dp:106, name:'phMmax',               type:'decimal', rw: 'rw',  min:0,   max:20,   /*defVal:1.5,*/  scale:10,  unit:'pH',    title:'<b>pH maximal value</b>'],
                [dp:107, name:'phMmin',               type:'decimal', rw: 'rw',  min:0,   max:20,   /*defVal:0.5,*/  scale:10,  unit:'pH',    title:'<b>pH minimal value</b>'],
                [dp:108, name:'ecMmax',               type:'decimal', rw: 'rw',  min:0,   max:100,  /*defVal:80.0,*/ scale:1,   unit:'µS/cm', title:'<b>Electrical Conductivity maximal value</b>'],
                [dp:109, name:'ecMmin',               type:'decimal', rw: 'rw',  min:0,   max:100,  /*defVal:20.0,*/ scale:1,   unit:'µS/cm', title:'<b>Electrical Conductivity minimal value</b>'],
                [dp:110, name:'orpMmax',              type:'decimal', rw: 'rw',  min:0,   max:1000, /*defVal:20.0,*/ scale:1,   unit:'mV',    title:'<b>Oxidation Reduction Potential maximal value</b>'],
                [dp:111, name:'orpMmin',              type:'decimal', rw: 'rw',  min:0,   max:1000, /*defVal:20.0,*/ scale:1,   unit:'mV',    title:'<b>Oxidation Reduction Potential minimal value</b>'],
                [dp:112, name:'freeChlorineMax',      type:'decimal', rw: 'rw',  min:0,   max:15,   /*defVal:20.0,*/ scale:10,  unit:'mg/L',  title:'<b>Free Chlorine maximal value</b>'],
                [dp:113, name:'freeChlorineMin',      type:'decimal', rw: 'rw',  min:0,   max:15,   /*defVal:20.0,*/ scale:10,  unit:'mg/L',  title:'<b>Free Chlorine minimal value</b>'],
                // "72": { "sensor_type": "phCalibration2"},
                // "73": { "sensor_type": "ecCalibration"},
                // "74": { "sensor_type": "orpCalibration"},
                [dp:117, name:'salinity',             type:'decimal', rw: 'ro',  scale:1,   unit:'gg', description:'Salt value'],
                // "78": { "store_tuya_attribute": "ORP_Calibration" }

            ],
            refresh:        ['tuyaDataQuery'],
            configuration : ['battery': false],
            deviceJoinName: 'BLE_YL01 Tuya Zigbee Chlorine Meter'
    ]
    // second manufacturer ?
    // https://www.amazon.com/YINMIK-Chlorine-Swimming-Salinity-Inground/dp/B0C2T8YLYW 
    // https://github.com/Koenkk/zigbee-herdsman-converters/pull/7613

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
}

// https://github.com/dresden-elektronik/deconz-rest-plugin/blob/0107459aa42f8ac5333c67f415e2482069e4ff79/device_access_fn.cpp#L825

void customParseZdoClusters(Map descMap) {
    if (descMap.clusterInt == 0x0013) {
        logDebug "customParseZdoClusters() - device announce"
        tyyaDataQuery()
    }
}

void tuyaDataQuery() {
    logDebug "tuyaDataQuery()"
    List<String> cmds = []
    //cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd )
    cmds += zigbee.command(0xEF00, 0x03)    // Tuya Data Query
    sendZigbeeCommands(cmds)
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

