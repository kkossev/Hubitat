/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName *//**
 *  Tuya Zigbee Tank Level Monitor - driver for Hubitat Elevation
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
 * ver. 3.3.1  2024-08-08 kkossev  - driver renamed to 'Tuya Zigbee Tank Level Monitor'
 * ver. 3.3.2  2024-08-11 kkossev  - driver renamed to 'Tuya Zigbee Tank Level Monitor', pars renamed to upperLimit/lowerLimit; added MOREYALEC_TUYA_ME201WZ device profile for tests;
 * ver. 3.3.3  2024-08-30 kkossev  - updated _TZE284_kyyu8rbj fingerprint for Morayelec ME201WZ; changeed battery from percentage to voltage; queryAllTuyaDP on refresh
 * ver. 3.3.4  2024-09-06 kkossev  - (dev.branch) default Debig option is off; installationHeight is now a in meters;
 *                                   
 *                                   TODO: 'Installation Height' not updating @digitalturbo
 *                                   TODO: queryAllTuyaDP on refresh to be made optional
 *                                   TODO: HPM
 */

static String version() { "3.3.4" }
static String timeStamp() {"2024/09/06 1:04 PM"}

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = false    // disable it for production

#include kkossev.deviceProfileLib
#include kkossev.commonLib

deviceType = "LevelMonitor"
@Field static final String DEVICE_TYPE = "LevelMonitor"

metadata {
    definition (
        name: 'Tuya Zigbee Tank Level Monitor',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Tank%20Level%20Monitor/Tuya_Zigbee_Tank_Level_Monitor_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        // no standard capabilities

        attribute 'liquidState', 'enum', ['normal', 'low', 'high', 'unknown']  // Liquid State
        attribute 'liquidDepth', 'number'                           // Liquid depth
        attribute 'upperLimit', 'number'                            // Liquid percentage to set high state (above this value)
        attribute 'lowerLimit', 'number'                            // Liquid percentage to set low state (below this value)
        attribute 'installationHeight', 'number'                    // Height from sensor to tank bottom
        attribute 'liquidDepthMax', 'number'                        // Distance from sensor to max liquid level
        attribute 'level', 'number'                                 // Liquid level percentage
        attribute 'batteryVoltage', 'number'                        // Battery voltage

        // no commands
        if (_DEBUG) {
            command 'sendCommand', [
                [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
                [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
            ]
            command 'setPar', [
                    [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                    [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
            ]
            }
        //*/
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
    // https://www.amazon.com/EPTTECH-TLC2206-ZB-Contactless-Waterproof-Industrial/dp/B0CY8H86VM
    // https://www.aliexpress.com/item/1005006395402636.html 
    // https://www.aliexpress.com/item/1005005758585356.html
    // https://github.com/Koenkk/zigbee2mqtt/issues/21015#issuecomment-2263850384 
    // https://www.eptsensor.com/level-monitor/tlc2206p-wireless-tuya-tank-level-monitor.html
    'EPTTECH_TLC2206'  : [
            description   : 'EPTTECH TLC2206 Zigbee Tank Level Monitor',
            models        : ['TS0601'],
            device        : [type: 'Sensor', powerSource: 'dc', isSleepy:false],    // check powerSource
            capabilities  : ['Battery': false],
            preferences   : ['upperLimit':'7', 'lowerLimit':'8', 'installationHeight':'19', 'liquidDepthMax':'21'],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences', 'printFingerprints':'printFingerprints', 'printPreferences':'printPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE200_lvkk0hdg', deviceJoinName: 'EPTTECH TLC2206 Zigbee Tank Level Monitor'],
            ],
            tuyaDPs:        [
                [dp:1,   name:'liquidState',          type:'enum',    rw: 'ro', defVal:'0', map:[0:'normal', 1:'low', 2:'high'], description:'Liquid State'],
                [dp:2,   name:'liquidDepth',          type:'decimal', rw: 'ro', min:0.0, max:9.9,  defVal:1.0,   scale:100, unit:'m',  title:'<b>Liquid Depth</b>', description:'Liquid depth'],
                [dp:7,   name:'upperLimit',           type:'number',  rw: 'rw', min:0,   max:100,  defVal:75,    scale:1,   unit:'%',  title:'<b>High Set</b>', description:'Liquid percentage to set high state (above this value)'],
                [dp:8,   name:'lowerLimit',           type:'number',  rw: 'rw', min:0,   max:100,  defVal:25,    scale:1,   unit:'%',  title:'<b>Low Set</b>', description:'Liquid percentage to set low state (below this value)'],
                [dp:19,  name:'installationHeight',   type:'number',  rw: 'rw', min:100, max:3000, defVal:2000,  scale:1,   unit:'mm', title:'<b>Installation Height</b>', description:'Height from sensor to tank bottom'], 
                [dp:21,  name:'liquidDepthMax',       type:'number',  rw: 'rw', min:100, max:2000, defVal:100,   scale:1,   unit:'mm', title:'<b>Liquid Depth Max</b>', description:'Distance from sensor to max liquid level'], 
                [dp:22,  name:'level',                type:'number',  rw: 'ro', min:0,   max:100,  defVal:0,     scale:1,   unit:'%',  title:'<b>Liquid Level Percent</b>', description:'Liquid level percentage"'], 
            ],
            refresh:        ['refreshQueryAllTuyaDP'],
            configuration : ['battery': false],
            deviceJoinName: 'EPTTECH TLC2206 Zigbee Tank Level Monitor'
    ],

    'MOREYALEC_TUYA_ME201WZ'  : [
            description   : 'Morayelec Zigbee Tank Level Monitor',
            models        : ['TS0601'],
            device        : [type: 'Sensor', powerSource: 'dc', isSleepy:false],
            capabilities  : ['Battery': false],
            preferences   : ['upperLimit':'7', 'lowerLimit':'8', 'installationHeight':'19'],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences', 'printFingerprints':'printFingerprints', 'printPreferences':'printPreferences'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000,ED00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE284_kyyu8rbj", controllerType: "ZGB", deviceJoinName: 'Moraylec ME201WZ Zigbee Tank Level Monitor']
            ],
            tuyaDPs:        [
                [dp:1,   name:'liquidState',          type:'enum',    rw: 'ro', defVal:'0', map:[0:'normal', 1:'low', 2:'high'], description:'Liquid level status'],
                [dp:2,   name:'liquidDepth',          type:'decimal', rw: 'ro', min:0.0, max:4.0,  defVal:0.0,   scale:100, unit:'m',  description:'Liquid level depth'],
                [dp:5,   name:'batteryVoltage',       type:'decimal', rw: 'ro', scale:10,  unit:'V',  description:'Battery voltage'],        // or 供电电压 Supply voltage ?
                [dp:7,   name:'upperLimit',           type:'number',  rw: 'rw', min:0,   max:100,  defVal:75,    scale:1,   unit:'%',  title:'<b>Upper Limit Setting</b>', description:'Liquid percentage to set high state (above this value)'],
                [dp:8,   name:'lowerLimit',           type:'number',  rw: 'rw', min:0,   max:100,  defVal:25,    scale:1,   unit:'%',  title:'<b>Lower Limit Setting</b>', description:'Liquid percentage to set low state (below this value)'],
                [dp:19,  name:'installationHeight',   type:'decimal', rw: 'rw', min:0.0, max:4.0,  defVal:2.0,   scale:100, unit:'m', title:'<b>Installation Height</b>', description:'Height from sensor to tank bottom (meters)'], 
                [dp:22,  name:'level',                type:'number',  rw: 'ro', min:0,   max:100,  defVal:0,     scale:1,   unit:'%',  title:'<b>Liquid Level Percent</b>', description:'Liquid level percentage"'], 
                [dp:24,  name:'relaySwitch',          type:'enum',    rw: 'rw', defVal:'0', map:[0:'off', 1:'on'], title:'Relay Switch', description:'Relay Switch'],
            ],
            spammyDPsToIgnore : [24], 
            spammyDPsToNotTrace : [5,24],           
            refresh:        ['refreshQueryAllTuyaDP'],
            configuration : ['battery': false],
            deviceJoinName: 'Morayelec ME201WZ Zigbee Tank Level Monitor'
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

List<String> customRefresh() {
    logDebug "customRefresh()"
    List<String> cmds = []
    List<String> devProfCmds = refreshFromDeviceProfileList()
    if (devProfCmds != null && !devProfCmds.isEmpty()) {
        cmds += devProfCmds
    }
    return cmds
}

List<String> refreshQueryAllTuyaDP() {
    return queryAllTuyaDP()
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
    /*
    if (state.deviceProfile == null || state.deviceProfile == '' || state.deviceProfile == 'UNKNOWN') {
        setDeviceNameAndProfile('TS0601', '_TZE200_lvkk0hdg')               // in deviceProfileiLib.groovy
    }
    */
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
}

void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents()"
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

