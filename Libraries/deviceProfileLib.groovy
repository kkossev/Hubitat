library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Device Profile Library",
    name: "deviceProfileLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy",
    version: "1.0.0",
    documentationLink: ""
)
/*
 *  Device Profile Library
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver)
 *
 *                                   TODO: 
*/

def deviceProfileLibVersion()   {"1.0.0"}
def deviceProfileLibtamp() {"2023/11/04 10:17 PM"}

metadata {
    // no capabilities
    // no attributes
    command "sendCommand", [[name: "sendCommand", type: "STRING", constraints: ["STRING"], description: "send device commands"]]
    //
    preferences {
        if (advancedOptions == true) {
            input (name: "forcedProfile", type: "enum", title: "<b>Device Profile</b>", description: "<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>",  options: getDeviceProfilesMap())
        }
    }
}

def getDeviceGroup()     { state.deviceProfile ?: "UNKNOWN" }
def getDEVICE()          { deviceProfilesV2[getDeviceGroup()] }
def getDeviceProfiles()      { deviceProfilesV2.keySet() }
def getDeviceProfilesMap()   {deviceProfilesV2.values().description as List<String>}

/**
 * Sends a command to the device.
 * @param command The command to send. Must be one of the commands defined in the DEVICE.commands map.
 * @param val The value to send with the command.
 * @return void
 */
def sendCommand( command=null, val=null )
{
    //logDebug "sending command ${command}(${val}))"
    ArrayList<String> cmds = []
    def supportedCommandsMap = DEVICE.commands 
    if (supportedCommandsMap == null || supportedCommandsMap == []) {
        logWarn "sendCommand: no commands defined for device profile ${getDeviceGroup()} !"
        return
    }
    // TODO: compare ignoring the upper/lower case of the command.
    def supportedCommandsList =  DEVICE.commands.keySet() as List 
    // check if the command is defined in the DEVICE commands map
    if (command == null || !(command in supportedCommandsList)) {
        logWarn "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE.description}' must be one of these : ${supportedCommandsList}"
        return
    }
    def func
    try {
        func = DEVICE.commands.find { it.key == command }.value
        if (val != null) {
            cmds = "${func}"(val)
            logInfo "executed <b>$func</b>($val)"
        }
        else {
            cmds = "${func}"()
            logInfo "executed <b>$func</b>()"
        }
    }
    catch (e) {
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})"
        return
    }
    if (cmds != null && cmds != []) {
        sendZigbeeCommands( cmds )
    }
}

/**
 * Returns the device name and profile based on the device model and manufacturer.
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value.
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value.
 * @return A list containing the device name and profile.
 */
def getDeviceNameAndProfile( model=null, manufacturer=null) {
    def deviceName         = UNKNOWN
    def deviceProfile      = UNKNOWN
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN
    deviceProfilesV2.each { profileName, profileMap ->
        profileMap.fingerprints.each { fingerprint ->
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) {
                deviceProfile = profileName
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV2[deviceProfile].deviceJoinName ?: UNKNOWN
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}"
                return [deviceName, deviceProfile]
            }
        }
    }
    if (deviceProfile == UNKNOWN) {
        logWarn "<b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}"
    }
    return [deviceName, deviceProfile]
}

// called from  initializeVars( fullInit = true)
def setDeviceNameAndProfile( model=null, manufacturer=null) {
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer)
    if (deviceProfile == null || deviceProfile == UNKNOWN) {
        logWarn "unknown model ${deviceModel} manufacturer ${deviceManufacturer}"
        // don't change the device name when unknown
        state.deviceProfile = UNKNOWN
    }
    def dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN
    def dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN
    if (deviceName != NULL && deviceName != UNKNOWN  ) {
        device.setName(deviceName)
        state.deviceProfile = deviceProfile
        device.updateSetting("forcedProfile", [value:deviceProfilesV2[deviceProfile].description, type:"enum"])
        //logDebug "after : forcedProfile = ${settings.forcedProfile}"
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>"
    } else {
        logWarn "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!"
    }    
}

def refreshDeviceProfile() {
    List<String> cmds = []
    if (cmds == []) { cmds = ["delay 299"] }
    logDebug "refreshDeviceProfile() : ${cmds}"
    return cmds
}

def configureDeviceProfile() {
    List<String> cmds = []
    logDebug "configureDeviceProfile() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299"] }    // no , 
    return cmds    
}

def initializeDeviceProfile()
{
    List<String> cmds = []
    logDebug "initializeDeviceProfile() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299",] }
    return cmds        
}

void initVarsDeviceProfile(boolean fullInit=false) {
    logDebug "initVarsDeviceProfile(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()
    }    
}

void initEventsDeviceProfile(boolean fullInit=false) {
    logDebug "initEventsDeviceProfile(${fullInit})"
}

