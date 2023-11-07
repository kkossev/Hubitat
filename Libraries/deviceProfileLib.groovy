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
 * ver. 1.0.1  2023-11-04 kkossev  - (dev. branch)
 *
 *                                   TODO: setPar refactoring
*/

def deviceProfileLibVersion()   {"1.0.1"}
def deviceProfileLibtamp() {"2023/11/05 9:45 AM"}

metadata {
    // no capabilities
    // no attributes
    command "sendCommand", [[name: "sendCommand", type: "STRING", constraints: ["STRING"], description: "send device commands"]]
    command "setPar", [
            [name:"par", type: "STRING", description: "preference parameter name", constraints: ["STRING"]],
            [name:"val", type: "STRING", description: "preference parameter value", constraints: ["STRING"]]
    ]    
    //
    // itterate over DEVICE.preferences map and inputIt all!
    (DEVICE.preferences).each { key, value ->
        if (inputIt(key) != null) {
            input inputIt(key)
        }
    }    
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
 * Returns the profile key for a given profile description.
 * @param valueStr The profile description to search for.
 * @return The profile key if found, otherwise null.
 */
def getProfileKey(String valueStr) {
    def key = null
    deviceProfilesV2.each {  profileName, profileMap ->
        if (profileMap.description.equals(valueStr)) {
            key = profileName
        }
    }
    return key
}

/**
 * Finds the preferences map for the given parameter.
 * @param param The parameter to find the preferences map for.
 * @param debug Whether or not to output debug logs.
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found
 * @return null if param is not defined for this device.
 */
def getPreferencesMap( String param, boolean debug=false ) {
    Map foundMap = [:]
    if (!(param in DEVICE.preferences)) {
        if (debug) log.warn "getPreferencesMap: preference ${param} not defined for this device!"
        return null
    }
    def preference 
    try {
        preference = DEVICE.preferences["$param"]
        if (debug) log.debug "getPreferencesMap: preference ${param} found. value is ${preference}"
        if (preference in [true, false]) {
            // find the preference in the tuyaDPs map
            logDebug "getPreferencesMap: preference ${param} is boolean"
            return null     // no maps for predefined preferences !
        }
        if (preference.isNumber()) {
            // find the preference in the tuyaDPs map
            int dp = safeToInt(preference)
            def dpMaps   =  DEVICE.tuyaDPs 
            foundMap = dpMaps.find { it.dp == dp }
        }
        else { // cluster:attribute
            if (debug) log.trace "${DEVICE.attributes}"
            def dpMaps   =  DEVICE.tuyaDPs 
            foundMap = DEVICE.attributes.find { it.at == preference }
        }
        // TODO - could be also 'true' or 'false' ...
    } catch (Exception e) {
        if (debug) log.warn "getPreferencesMap: exception ${e} caught when getting preference ${param} !"
        return null
    }
    if (debug) log.debug "getPreferencesMap: foundMap = ${foundMap}"
    return foundMap     
}

/**
 * Resets the device preferences to their default values.
 * @param debug A boolean indicating whether to output debug information.
 */
def resetPreferencesToDefaults(boolean debug=false ) {
    Map preferences = DEVICE?.preferences
    if (preferences == null) {
        logWarn "Preferences not found!"
        return
    }
    Map parMap = [:]
    preferences.each{ parName, mapValue -> 
        if (debug) log.trace "$parName $mapValue"
        // TODO - could be also 'true' or 'false' ...
        if (mapValue in [true, false]) {
            logDebug "Preference ${parName} is predefined -> (${mapValue})"
            // TODO - set the predefined value
            /*
            if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}"
            device.updateSetting("${parMap.name}",[value:parMap.defaultValue, type:parMap.type])     
            */       
            return // continue
        }
        // find the individual preference map
        parMap = getPreferencesMap(parName, false)
        if (parMap == null) {
            logWarn "Preference ${parName} not found in tuyaDPs or attributes map!"
            return // continue
        }   
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, step:1, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity]
        if (parMap.defaultValue == null) {
            logWarn "no default value for preference ${parName} !"
            return // continue
        }
        if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}"
        device.updateSetting("${parMap.name}",[value:parMap.defaultValue, type:parMap.type])
    }
    logInfo "Preferences reset to default values"
}

/**
 * Returns a list of valid parameters per model based on the device preferences.
 *
 * @return List of valid parameters.
 */
def getValidParsPerModel() {
    List<String> validPars = []
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) {
        // use the preferences to validate the parameters
        validPars = DEVICE.preferences.keySet().toList()
    }
    return validPars
}


/**
 * Called from setPar() method only!
 * Validates the parameter value based on the given dpMap type and scales it if needed.
 * 
 * @param dpMap The map containing the parameter type, minimum and maximum values.
 * @param val The value to be validated and scaled.
 * @return The validated and scaled value if it is within the specified range, null otherwise.
 */
def validateAndScaleParameterValue(Map dpMap, String val) {
    def value = null    // validated value - integer, floar
    def scaledValue = null
    logDebug "validateAndScaleParameterValue dpMap=${dpMap} val=${val}"
    switch (dpMap.type) {
        case "number" :
            value = safeToInt(val, -1)
            scaledValue = value
            // scale the value - added 10/26/2023 also for integer values !
            if (dpMap.scale != null) {
                scaledValue = (value * dpMap.scale) as Integer
            }            
            break
        case "decimal" :
             value = safeToDouble(val, -1.0)
            // scale the value
            if (dpMap.scale != null) {
                scaledValue = (value * dpMap.scale) as Integer
            }
            break
        case "bool" :
            if (val == '0' || val == 'false')     { value = scaledValue = 0 }
            else if (val == '1' || val == 'true') { value = scaledValue = 1 }
            else {
                log.warn "${device.displayName} sevalidateAndScaleParameterValue: bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>"
                return null
            }
            break
        case "enum" :
            // val could be both integer or float value ... check if the scaling is different than 1 in dpMap 

            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) {
                // we have a float parameter input - convert it to int
                value = safeToDouble(val, -1.0)
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer
            }
            else {
                value = scaledValue = safeToInt(val, -1)
            }
            if (scaledValue == null || scaledValue < 0) {
                // get the keys of dpMap.map as a List
                List<String> keys = dpMap.map.keySet().toList()
                log.warn "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>"
                return null
            }
            break
        default :
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>"
            return null
    }
    //log.warn "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}"
    // check if the value is within the specified range
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) {
        log.warn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}"
        return null
    }
    //log.warn "validateAndScaleParameterValue returning scaledValue=${scaledValue}"
    return scaledValue
}

/**
 * Sets the parameter value for the device.
 * @param par The name of the parameter to set.
 * @param val The value to set the parameter to.
 * @return Nothing.
 */
def setPar( par=null, val=null )
{
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) {
        return null
    }
    // new method
    logDebug "setPar new method: setting parameter ${par} to ${val}"
    ArrayList<String> cmds = []
    Boolean validated = false
    if (par == null) {
        log.warn "${device.displayName} setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"
        return
    }        
    if (!(par in getValidParsPerModel())) {
        log.warn "${device.displayName} setPar: parameter '${par}' must be one of these : ${getValidParsPerModel()}"
        return
    }
    // find the tuayDPs map for the par
    Map dpMap = getPreferencesMap(par, false)
    if ( dpMap == null ) {
        log.warn "${device.displayName} setPar: tuyaDPs map not found for parameter <b>${par}</b>"
        return
    }
    if (val == null) {
        log.warn "${device.displayName} setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"
        return
    }
    // convert the val to the correct type and scale it if needed
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)
    if (scaledValue == null) {
        log.warn "${device.displayName} setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"
        return
    }
    // update the device setting
    // TODO: decide whether the setting must be updated here, or after it is echeod back from the device
    try {
        device.updateSetting("$par", [value:val, type:dpMap.type])
    }
    catch (e) {
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}"
        return
    }
    logDebug "parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}"
    // if there is a dedicated set function, use it
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1]
    String setFunction = "set${capitalizedFirstChar}"
    if (this.respondsTo(setFunction)) {
        logDebug "setPar: found setFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})"
        // execute the setFunction
        try {
            cmds = "$setFunction"(scaledValue)
        }
        catch (e) {
            logWarn "setPar: Exception '${e}'caught while processing <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))"
            return
        }
        logDebug "setFunction result is ${cmds}"       
        if (cmds != null && cmds != []) {
            logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$scaledValue</b>)"
            sendZigbeeCommands( cmds )
            return
        }            
        else {
            logWarn "setPar: setFunction <b>$setFunction</b>(<b>$scaledValue</b>) returned null or empty list"
            // continue with the default processing
        }
    }
    // check whether this is a tuya DP or a cluster:attribute parameter
    if (dpMap.dp != null && dpMap.dp.isNumber()) {
        // Tuya DP
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) 
        if (cmds == null || cmds == []) {
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list"
            return
        }
        else {
            logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))"
            sendZigbeeCommands( cmds )
            return
        }
    }
    else if (dpMap.at != null) {
        // cluster:attribute
        int cluster
        int attribute
        int dt
        int mfgCode
        try {
            cluster = hubitat.helper.HexUtils.hexStringToInt(dpMap.at.split(":")[0])
            attribute = hubitat.helper.HexUtils.hexStringToInt(dpMap.at.split(":")[1])
            dt = hubitat.helper.HexUtils.hexStringToInt(dpMap.dt)
            mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null
        }
        catch (e) {
            logWarn "setPar: Exception '${e}'caught while splitting cluser and attribute <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))"
            return
        }
        Map mapMfCode = ["mfgCode":mfgCode]
        logDebug "setPar: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})"
        if (mfgCode != null) {
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay=200)
        }
        else {
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay=200)
        }
    }
    else {
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>"
        return
    }


    logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$scaledValue</b>)"
    sendZigbeeCommands( cmds )
    return
}

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap
// TODO - reuse it !!!
def sendTuyaParameter( Map dpMap, String par, tuyaValue) {
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}"
    ArrayList<String> cmds = []
    if (dpMap == null) {
        log.warn "${device.displayName} sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>"
        return null
    }
    String dp = zigbee.convertToHexString(dpMap.dp, 2)
    if (dpMap.dp <= 0 || dpMap.dp >= 256) {
        log.warn "${device.displayName} sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>"
        return null 
    }
    String dpType = dpMap.type == "bool" ? DP_TYPE_BOOL : dpMap.type == "enum" ? DP_TYPE_ENUM : (dpMap.type in ["value", "number", "decimal"]) ? DP_TYPE_VALUE: null
    //log.debug "dpType = ${dpType}"
    if (dpType == null) {
        log.warn "${device.displayName} sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>"
        return null 
    }
    // sendTuyaCommand
    def dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) 
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} "
    cmds = sendTuyaCommand( dp, dpType, dpValHex)
    return cmds
}

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
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details.
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not.
 * It then checks if the input parameter is a boolean value and skips it if it is.
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly.
 * If the input parameter is read-only, the method returns null.
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter.
 * If the input parameter type is not supported, the method returns null.
 * @param param The input parameter to be checked.
 * @param debug A boolean flag indicating whether to log debug messages or not.
 * @return A map containing the input details.
 */
def inputIt( String param, boolean debug=false ) {
    Map input = [:]
    Map foundMap = [:]
    if (!(param in DEVICE.preferences)) {
        if (debug) log.warn "inputIt: preference ${param} not defined for this device!"
        return null
    }
    def preference
    boolean isTuyaDP 
    try {
        preference = DEVICE.preferences["$param"]
    }
    catch (e) {
        if (debug) log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}"
        return null
    }   
    //  check for boolean values
    try {
        if (preference in [true, false]) {
            if (debug) log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!"
            return null
        }
    }
    catch (e) {
        if (debug) log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}"
        return null
    } 

    try {
        isTuyaDP = preference.isNumber()
    }
    catch (e) {
        if (debug) log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}"
        return null
    } 

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}"
    foundMap = getPreferencesMap(param)
    //if (debug) log.debug "foundMap = ${foundMap}"
    if (foundMap == null) {
        if (debug) log.warn "inputIt: map not found for param '${param}'!"
        return null
    }
    if (foundMap.rw != "rw") {
        if (debug) log.warn "inputIt: param '${param}' is read only!"
        return null
    }        
    input.name = foundMap.name
    input.type = foundMap.type    // bool, enum, number, decimal
    input.title = foundMap.title
    input.description = foundMap.description
    if (input.type in ["number", "decimal"]) {
        if (foundMap.min != null && foundMap.max != null) {
            input.range = "${foundMap.min}..${foundMap.max}"
        }
        if (input.range != null && input.description !=null) {
            input.description += "<br><i>Range: ${input.range}</i>"
            if (foundMap.unit != null && foundMap.unit != "") {
                input.description += " <i>(${foundMap.unit})</i>"
            }
        }
    }
    else if (input.type == "enum") {
        input.options = foundMap.map
    }/*
    else if (input.type == "bool") {
        input.options = ["true", "false"]
    }*/
    else {
        if (debug) log.warn "inputIt: unsupported type ${input.type} for param '${param}'!"
        return null
    }   
    if (input.defaultValue != null) {
        input.defaultValue = foundMap.defaultValue
    }
    return input
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

