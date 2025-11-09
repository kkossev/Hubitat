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
 * ver. 3.3.0  2024-08-03 kkossev  - first dummy version
 *                                   
 *                                   TODO: Open/Close the valbe (DP13)
 */

static String version() { "3.3.2" }
static String timeStamp() { "2024/09/30 2:49 PM" }

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = false  // disable it for production

#include kkossev.deviceProfileLib
#include kkossev.commonLib

deviceType = "WaterFlowMeter"
@Field static final String DEVICE_TYPE = "WaterFlowMeter"

metadata {
    definition (
        name: 'Tuya Ultrasonic Water Flow Meter',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Chlorine%20Meter/Tuya_Zigbee_Chlorine_Meter_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        // no standard capabilities

        attribute 'waterConsumed', 'number'                       // Water consumed
        attribute 'monthConsumption', 'number'                    // Month consumption
        attribute 'dailyConsumption', 'number'                    // Daily consumption
        attribute 'reportPeriod', 'enum'                          // Report period
        attribute 'warning', 'bitmap'                             // Warning
        attribute 'monthAndDailyFrozenSet', 'number'              // Month and daily frozen set
        attribute 'state', 'bool'                                 // Valve state
        attribute 'autoClean', 'bool'                             // Auto clean
        attribute 'meterId', 'number'                             // Meter ID
        attribute 'reverseWaterConsumption', 'number'             // Reverse water consumption
        attribute 'instantaneousFlowRate', 'number'               // Instantaneous flow rate
        attribute 'batteryVoltage', 'number'                      // Battery voltage


       // no commands
       if (_DEBUG) {
            command 'tuyaDataQuery'
        }

        // itterate through all the figerprints and add them on the fly
        deviceProfilesV3.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each {
                    fingerprint it
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
        if (device) {
            input name: 'pollingInterval', type: 'enum', title: '<b>Polling Interval</b>', options: PollingIntervalOpts.options, defaultValue: PollingIntervalOpts.defaultValue, required: true, description: 'Changes how often the hub will poll the sensor.'
        }
    }
}

@Field static String ttStyleStr = '<style>.tTip {display:inline-block;border-bottom: 1px dotted black;}.tTip .tTipText {display:none;border-radius: 6px;padding: 5px 0;position: absolute;z-index: 1;}.tTip:hover .tTipText {display:inline-block;background-color:red;color:red;}</style>'

@Field static final Map PollingIntervalOpts = [
    defaultValue: 300,
    options     : [0: 'Disabled', 5: 'Every 5 seconds (DONT DO THAT!)', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes (default)', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour']
]

/*
Measures :

*/

// https://www.alibaba.com/product-detail/Smart-Ultrasonic-Water-Flow-Meter-With_1600722839075.html 
// https://github.com/Koenkk/zigbee2mqtt/issues/21255
@Field static final Map deviceProfilesV3 = [
    'ULTRASONIC_FLOW_METER'  : [
            description   : 'Tuya Ultrasonic Water Flow Meter',
            models        : ['TS0601'],
            device        : [type: 'Sensor', powerSource: 'battery', isSleepy:false],
            capabilities  : ['Battery': true, 'TemperatureMeasurement': true],
            preferences   : [:],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences', 'printFingerprints':'printFingerprints', 'printPreferences':'printPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE200_vuwtqx0t', deviceJoinName: 'Tuya 214C Ultrasonic Water Flow Meter'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE200_zlwr0raf', deviceJoinName: 'Tuya 213E Ultrasonic Water Flow Meter'],      // // https://www.aliexpress.com/item/1005007308058989.html

            ],
            tuyaDPs:        [
                [dp:1,   name:'waterConsumed',           type:'number',  rw: 'ro',  scale:1,   unit:'L',     description:'Water consumed'],        // dtype number
                [dp:2,   name:'monthConsumption',        type:'number',  rw: 'ro',  scale:1,   unit:'L',     description:'Month consumption'],    // dtype 0 - raw
                [dp:3,   name:'dailyConsumption',        type:'number',  rw: 'ro',  scale:1,   unit:'L',     description:'Daily consumption'],    // dtype 0 - raw
                [dp:4,   name:'reportPeriod',            type:'enum',    rw: 'ro',  scale:1,   unit:'',      description:'Report period',  enumMap: ['1h':0, '2h':1, '3h':2, '4h':3, '6h':4, '8h':5, '12h':6, '24h':7]],  // dtype 4 - enum
                [dp:5,   name:'warning',                 type:'bitmap',  rw: 'ro',  scale:1,   unit:'',      description:'Warning'],              // bitmap
                [dp:6,   name:'monthAndDailyFrozenSet',  type:'number',  rw: 'ro',  scale:1,   unit:'',      description:'Month and daily frozen set'],   // dtype 0 - raw
                [dp:13,  name:'state',                   type:'bool',    rw: 'rw',  scale:1,   unit:'',      description:'Valve state'],          // Bool   VALVE OPEN CLOSE
                [dp:14,  name:'autoClean',               type:'bool',    rw: 'rw',  scale:1,   unit:'',      description:'Auto clean'],           // Bool   AUTOCLEAN SW -  ?
                [dp:15,  name:'UnknownDp15',             type:'number',  rw: 'ro',  scale:1,   unit:'',      description:'Unknown DP15'],         // dtype 0 - raw  - ? 
                [dp:16,  name:'meterId',                 type:'number',  rw: 'ro',  scale:1,   unit:'',      description:'Meter ID'],             //dtype 3 - raw   METER ID
                [dp:18,  name:'reverseWaterConsumption', type:'number',  rw: 'ro',  scale:1,   unit:'L',     description:'Reverse water consumption'],    // dtype 0 - raw
                [dp:21,  name:'instantaneousFlowRate',   type:'number',  rw: 'ro',  scale:1,   unit:'L/h',   description:'Instantaneous flow rate'],      // dtype 0 - raw
                [dp:22,  name:'temperature',             type:'decimal', rw: 'ro',  scale:100, unit:'C',     description:'Water Temperature'],
                [dp:26,  name:'batteryVoltage',          type:'decimal', rw: 'ro',  scale:1000,  unit:'V',     description:'Battery voltage'],
            ],
            refresh:        ['refreshQueryAllTuyaDP'],
            configuration : ['battery': false],
            deviceJoinName: 'Tuya Ultrasonic Water Flow Meter'
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

    final int interval = (settings?.pollingInterval as Integer) ?: 0
    if (interval > 0) {
        logInfo "customUpdated: scheduling polling every ${interval} seconds"
        schedulePolling(interval)
    }
    else {
        unSchedulePolling()
        logInfo 'customUpdated: polling is disabled!'
    }

    // Itterates through all settings
    cmds += updateAllPreferences()  // defined in deviceProfileLib
    sendZigbeeCommands(cmds)
}

/**
 * Schedule polling
 * @param intervalMins interval in seconds
 */
private void schedulePolling(final int intervalSecs) {
    String cron = getCron( intervalSecs )
    logDebug "cron = ${cron}"
    schedule(cron, 'autoPoll')
}

private void unSchedulePolling() {
    unschedule('autoPoll')
}

/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPoll() {
    logDebug 'autoPoll()...'
    checkDriverVersion(state)
    List<String> cmds = []
    cmds = refreshFromDeviceProfileList()
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }
}



void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null || state.deviceProfile == '' || state.deviceProfile == 'UNKNOWN') {
        setDeviceNameAndProfile('TS0601', '_TZE200_vuwtqx0t')               // for test!    //in deviceProfileiLib.groovy
    }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    if (fullInit || settings?.pollingInterval == null) { device.updateSetting('pollingInterval', [value: PollingIntervalOpts.defaultValue.toString(), type: 'enum']) }
}

void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents()"
}

void customParseZdoClusters(Map descMap) {
    if (descMap.clusterInt == 0x0013) {
        logDebug "customParseZdoClusters() - device announce"
        sendZigbeeCommands(refreshQueryAllTuyaDP())
    }
}

List<String> refreshQueryAllTuyaDP() {
    return queryAllTuyaDP()
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

