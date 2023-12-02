library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Device Profile Library",
    name: "deviceProfileLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy",
    version: "3.0.0",
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
 * ver. 3.0.0  2023-11-27 kkossev  - (dev. branch) fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; 
 *
 *                                   TODO: refactor sendAttribute !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
*/

def deviceProfileLibVersion()   {"3.0.0"}
def deviceProfileLibtamp() {"2023/12/01 5:12 PM"}

metadata {
    // no capabilities
    // no attributes
    command "sendCommand", [
        [name:"command", type: "STRING", description: "command name", constraints: ["STRING"]],
        [name:"val",     type: "STRING", description: "command parameter value", constraints: ["STRING"]]
    ]
    command "setPar", [
            [name:"par", type: "STRING", description: "preference parameter name", constraints: ["STRING"]],
            [name:"val", type: "STRING", description: "preference parameter value", constraints: ["STRING"]]
    ]    
    //
    // itterate over DEVICE.preferences map and inputIt all!
    if (DEVICE != null && DEVICE.preferences != null && DEVICE.preferences != [:]) {
        (DEVICE.preferences).each { key, value ->
            if (inputIt(key) != null) {
                input inputIt(key)
            }
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
def getPreferencesMapByName( String param, boolean debug=false ) {
    Map foundMap = [:]
    if (!(param in DEVICE.preferences)) {
        if (debug) { logWarn "getPreferencesMapByName: preference ${param} not defined for this device!" }
        return null
    }
    def preference 
    try {
        preference = DEVICE.preferences["$param"]
        if (debug) log.debug "getPreferencesMapByName: param ${param} found. preference is ${preference}"
        if (preference in [true, false]) {      // find the preference in the tuyaDPs map
            if (debug) { logDebug "getPreferencesMapByName: preference ${param} is boolean" }
            return null     // no maps for predefined preferences !
        }
        if (safeToInt(preference, -1) >0) {             //if (preference instanceof Number) {
            int dp = safeToInt(preference)
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})"
            foundMap = DEVICE.tuyaDPs.find { it.dp == dp }
        }
        else { // cluster:attribute
            //if (debug) log.trace "getPreferencesMapByName:  ${DEVICE.attributes}"
            def dpMaps   =  DEVICE.tuyaDPs 
            foundMap = DEVICE.attributes.find { it.at == preference }
        }
        // TODO - could be also 'true' or 'false' ...
    } catch (Exception e) {
        if (debug) log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !"
        return null
    }
    if (debug) { logDebug "getPreferencesMapByName: param=${param} foundMap = ${foundMap}" }
    return foundMap     
}

def getAttributesMap( String attribName, boolean debug=false ) {
    Map foundMap = null
    def searchMap
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" }
    if (DEVICE.tuyaDPs != null) {
        searchMap =  DEVICE.tuyaDPs 
        foundMap = searchMap.find { it.name == attribName }
        if (foundMap != null) {
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" }
            return foundMap
        }
    }
    logDebug "getAttributesMap: searching for attribute ${attribName} in attributes"
    if (DEVICE.attributes != null) {
        searchMap  =  DEVICE.attributes 
        foundMap = searchMap.find { it.name == attribName }
        if (foundMap != null) {
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" }
            return foundMap
        }
    }
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" }
    return null
}


/**
 * Resets the device preferences to their default values.
 * @param debug A boolean indicating whether to output debug information.
 */
def resetPreferencesToDefaults(/*boolean*/ debug=false ) {
    logDebug "resetPreferencesToDefaults...(debug=${debug})"
    if (DEVICE == null) {
        if (debug) { logWarn "DEVICE not found!" }
        return
    }
    def preferences = DEVICE?.preferences
    log.trace "preferences = ${preferences}"    
    if (preferences == null) {
        if (debug) { logWarn "Preferences not found!" }
        return
    }
    def parMap = [:]
    preferences.each{ parName, mapValue -> 
        if (debug) log.trace "$parName $mapValue"
        // TODO - could be also 'true' or 'false' ...
        if (mapValue in [true, false]) {
            if (debug) { logDebug "Preference ${parName} is predefined -> (${mapValue})" }
            // TODO - set the predefined value
            /*
            if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}"
            device.updateSetting("${parMap.name}",[value:parMap.defaultValue, type:parMap.type])     
            */       
            return // continue
        }
        // find the individual preference map
        parMap = getPreferencesMapByName(parName, false)
        if (parMap == null) {
            if (debug) { logWarn "Preference ${parName} not found in tuyaDPs or attributes map!" }
            return // continue
        }   
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, step:1, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity]
        if (parMap.defaultValue == null) {
            if (debug) { logWarn "no default value for preference ${parName} !" }
            return // continue
        }
        if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}"
        if (debug) log.trace "parMap.name ${parMap.name} parMap.defaultValue = ${parMap.defaultValue} type=${parMap.type}"
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
 * Returns the scaled value of a preference based on its type and scale.
 * @param preference The name of the preference to retrieve.
 * @param dpMap A map containing the type and scale of the preference.
 * @return The scaled value of the preference, or null if the preference is not found or has an unsupported type.
 */
def getScaledPreferenceValue(String preference, Map dpMap) {
    def value = settings."${preference}"
    def scaledValue
    if (value == null) {
        logDebug "getScaledPreferenceValue: preference ${preference} not found!"
        return null
    }
    switch(dpMap.type) {
        case "number" :
            scaledValue = safeToInt(value)
            break
        case "decimal" :
            scaledValue = safeToDouble(value)
            if (dpMap.scale != null && dpMap.scale != 1) {
                scaledValue = Math.round(scaledValue * dpMap.scale)
            }
            break
        case "bool" :
            scaledValue = value == "true" ? 1 : 0
            break
        case "enum" :
            //log.warn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}"
            if (dpMap.map == null) {
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!"
                return null
            }
            scaledValue = value 
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) {
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale))
            }            
            break
        default :
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!"
            return null
    }
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" 
    return scaledValue
}

// called from updated() method
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!!
void updateAllPreferences() {
    logDebug "updateAllPreferences: preferences=${DEVICE.preferences}"
    if (DEVICE.preferences == null || DEVICE.preferences == [:]) {
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceGroup()}"
        return
    }
    Integer dpInt = 0
    def scaledValue    // int or String for enums
    // itterate over the preferences map and update the device settings
    (DEVICE.preferences).each { name, dp -> 
        /*
        dpInt = safeToInt(dp, -1)
        if (dpInt <= 0) {
            // this is the IAS and other non-Tuya DPs preferences .... 
            logDebug "updateAllPreferences: preference ${name} has invalid Tuya dp value ${dp}"
            return 
        }
        def dpMaps   =  DEVICE.tuyaDPs 
        */
        Map foundMap
        foundMap = getPreferencesMapByName(name, false)
        logDebug "updateAllPreferences: foundMap = ${foundMap}"

        if (foundMap != null) {
            // scaledValue = getScaledPreferenceValue(name, foundMap)
            scaledValue = settings."${name}"
            logTrace"scaledValue = ${scaledValue}"                                          // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! 
            if (scaledValue != null) {
                setPar(name, scaledValue)
            }
            else {
                logDebug "updateAllPreferences: preference ${name} is not set (scaledValue was null)"
                return 
            }
        }
        else {
            logDebug "warning: couldn't find map for preference ${name}"
            return 
        }
    }    
    return
}

def divideBy100( val ) { return (val as int) / 100 }
def multiplyBy100( val ) { return (val as int) * 100 }
def divideBy10( val ) { 
    if (val > 10) { return (val as int) / 10 }
    else { return (val as int) }
}
def multiplyBy10( val ) { return (val as int) * 10 }
def divideBy1( val ) { return (val as int) / 1 }    //tests
def signedInt( val ) {
    if (val > 127) { return (val as int) - 256 }
    else { return (val as int) }

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
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}"
    switch (dpMap.type) {
        case "number" :
            value = safeToInt(val, -1)
            //scaledValue = value
            // scale the value - added 10/26/2023 also for integer values !
            if (dpMap.scale != null) {
                scaledValue = (value * dpMap.scale) as Integer
            }
            else {
                scaledValue = value
            }
            break
            
        case "decimal" :
            value = safeToDouble(val, -1.0)
            // scale the value
            if (dpMap.scale != null) {
                scaledValue = (value * dpMap.scale) as Integer
            }
            else {
                scaledValue = value
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
 *
 * TODO: refactor it !!!
 */
def setPar( par=null, val=null )
{
    ArrayList<String> cmds = []
    Boolean validated = false
    logDebug "setPar(${par}, ${val})"
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return }
    if (par == null /*|| !(par in getValidParsPerModel())*/) { log.warn "${device.displayName} setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return }        
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter
    if ( dpMap == null ) { log.warn "${device.displayName} setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return }
    if (val == null) { log.warn "${device.displayName} setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return }
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed
    if (scaledValue == null) { log.warn "${device.displayName} setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return }
    /*
    // update the device setting // TODO: decide whether the setting must be updated here, or after it is echeod back from the device
    try {
        device.updateSetting("$par", [value:val, type:dpMap.type])
    }
    catch (e) {
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}"
        return
    }
    */
    //logDebug "setPar: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}"
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
            logInfo "setPar: (1) successfluly executed setPar <b>$setFunction</b>(<b>$scaledValue</b>)"
            sendZigbeeCommands( cmds )
            return
        }            
        else {
            logWarn "setPar: setFunction <b>$setFunction</b>(<b>$scaledValue</b>) returned null or empty list"
            // continue with the default processing
        }
    }
    // check whether this is a tuya DP or a cluster:attribute parameter
    boolean isTuyaDP
    /*def preference = dpMap.dp
    log.warn "preference = ${preference}"*/ // TOBEDEL
    try {
        // check if dpMap.dp is a number
        isTuyaDP = dpMap.dp instanceof Number
    }
    catch (e) {
        logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}"
        isTuyaDP = false
        //return null
    }     
    if (dpMap.dp != null && isTuyaDP) {
        // Tuya DP
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) 
        if (cmds == null || cmds == []) {
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list"
            return
        }
        else {
            logInfo "setPar: (2) successfluly executed setPar <b>$setFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))"
            sendZigbeeCommands( cmds )
            return
        }
    }
    else if (dpMap.at != null) {
        // cluster:attribute
        int cluster
        int attribute
        int dt
        def mfgCode
        try {
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(":")[0])
            //log.trace "cluster = ${cluster}"
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(":")[1])
            //log.trace "attribute = ${attribute}"
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null
            //log.trace "dt = ${dt}"
            mfgCode = dpMap.mfgCode
            //log.trace "mfgCode = ${dpMap.mfgCode}"
        }
        catch (e) {
            logWarn "setPar: Exception '${e}' caught while splitting cluser and attribute <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))"
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
    logInfo "setPar: (3) successfluly executed setPar <b>$setFunction</b>(<b>$scaledValue</b>)"
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
    String dpType
    if (dpMap.dt == null) {
        dpType = dpMap.type == "bool" ? DP_TYPE_BOOL : dpMap.type == "enum" ? DP_TYPE_ENUM : (dpMap.type in ["value", "number", "decimal"]) ? DP_TYPE_VALUE: null
    }
    else {
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value
    }
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

def sendAttribute( par=null, val=null )
{
    ArrayList<String> cmds = []
    Boolean validated = false
    logDebug "sendAttribute(${par}, ${val})"
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return }

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute
    if ( dpMap == null ) { log.warn "${device.displayName} sendAttribute: map not found for parameter <b>${par}</b>"; return }
    if (val == null) { log.warn "${device.displayName} sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return }
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed
    if (scaledValue == null) { log.warn "${device.displayName} sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return }
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}"
    // if there is a dedicated set function, use it
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1]
    String setFunction = "set${capitalizedFirstChar}"
    if (this.respondsTo(setFunction) && (setFunction != "setHeatingSetpoint" && setFunction != "setCoolingSetpoint")) {
        logDebug "sendAttribute: found setFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})"
        // execute the setFunction
        try {
            cmds = "$setFunction"(scaledValue)
        }
        catch (e) {
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))"
            return
        }
        logDebug "setFunction result is ${cmds}"       
        if (cmds != null && cmds != []) {
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$setFunction</b>(<b>$scaledValue</b>)"
            sendZigbeeCommands( cmds )
            return
        }            
        else {
            logWarn "sendAttribute: setFunction <b>$setFunction</b>(<b>$scaledValue</b>) returned null or empty list"
            // continue with the default processing
        }
    }
    // check whether this is a tuya DP or a cluster:attribute parameter
    boolean isTuyaDP
    def preference = dpMap.dp
    try {
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number
    }
    catch (e) {
        if (debug) log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}"
        return null
    }     
    if (dpMap.dp != null && isTuyaDP) {
        // Tuya DP
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) 
        if (cmds == null || cmds == []) {
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list"
            return
        }
        else {
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$setFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))"
            sendZigbeeCommands( cmds )
            return
        }
    }
    else if (dpMap.at != null) {
        // cluster:attribute
        int cluster
        int attribute
        int dt
       // int mfgCode
        //log.trace "dpMap.at = ${dpMap.at}"
   //     try {
            cluster = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(":")[0])
            //log.trace "cluster = ${cluster}"
            attribute = hubitat.helper.HexUtils.hexStringToInt((dpMap.at).split(":")[1])
            //log.trace "attribute = ${attribute}"
            dt = dpMap.dt != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) : null
            //log.trace "dt = ${dt}"
            //log.trace "mfgCode = ${dpMap.mfgCode}"
          //  mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null
          //  log.trace "mfgCode = ${mfgCode}"
  //      }
  /*      catch (e) {
            logWarn "sendAttribute: Exception '${e}'caught while splitting cluster and attribute <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))"
            return
        }*/
       
        logDebug "sendAttribute: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})"
        if (dpMap.mfgCode != null) {
            Map mapMfCode = ["mfgCode":dpMap.mfgCode]
            //log.trace "mapMfCode = ${mapMfCode}"
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay=200)
        }
        else {
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay=200)
        }
    }
    else {
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>"
        return
    }
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$setFunction</b>(<b>$scaledValue</b>)"
    sendZigbeeCommands( cmds )
    return
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
        logInfo "sendCommand: no commands defined for device profile ${getDeviceGroup()} !"
        return
    }
    // TODO: compare ignoring the upper/lower case of the command.
    def supportedCommandsList =  DEVICE.commands.keySet() as List 
    // check if the command is defined in the DEVICE commands map
    if (command == null || !(command in supportedCommandsList)) {
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE.description}' must be one of these : ${supportedCommandsList}"
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
        isTuyaDP = preference instanceof Number
    }
    catch (e) {
        if (debug) log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}"
        return null
    } 

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}"
    foundMap = getPreferencesMapByName(param)
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

///////////////////////////// Tuya DPs /////////////////////////////////


//
// called from parse()
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule
//          false - the processing can continue
//
boolean isSpammyDPsToIgnore(descMap) {
    if (!(descMap?.clusterId == "EF00" && (descMap?.command in ["01", "02"]))) { return false }
    if (descMap?.data?.size <= 2) { return false }
    Integer dp =  zigbee.convertHexToInt(descMap.data[2])
    def spammyList = deviceProfilesV2[getDeviceGroup()].spammyDPsToIgnore
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true))
}

//
// called from processTuyaDP(), processTuyaDPfromDeviceProfile()
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule
//          false - debug logs can be generated
//
boolean isSpammyDPsToNotTrace(descMap) {
    if (!(descMap?.clusterId == "EF00" && (descMap?.command in ["01", "02"]))) { return false }
    if (descMap?.data?.size <= 2) { return false }
    Integer dp = zigbee.convertHexToInt(descMap.data[2]) 
    def spammyList = deviceProfilesV2[getDeviceGroup()].spammyDPsToNotTrace
    return (spammyList != null && (dp in spammyList))
}

def compareAndConvertStrings(foundItem, tuyaValue, hubitatValue) {
    String convertedValue = tuyaValue
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings
    return [isEqual, convertedValue]
}

def compareAndConvertNumbers(foundItem, tuyaValue, hubitatValue) {
    Integer convertedValue
    if (foundItem.scale == null || foundItem.scale == 0 || foundItem.scale == 1) {    // compare as integer
        convertedValue = tuyaValue as int                
    }
    else {
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int
    }
    boolean isEqual = ((convertedValue as int) == (hubitatValue as int))
    return [isEqual, convertedValue]
}

def compareAndConvertDecimals(foundItem, tuyaValue, hubitatValue) {
    Double convertedValue
    if (foundItem.scale == null || foundItem.scale == 0 || foundItem.scale == 1) {
        convertedValue = tuyaValue as double
    }
    else {
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) 
    }
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 
    return [isEqual, convertedValue]
}


def compareAndConvertTuyaToHubitatPreferenceValue(foundItem, fncmd, preference) {
    if (foundItem == null || fncmd == null || preference == null) { return [true, "none"] }
    if (foundItem.type == null) { return [true, "none"] }
    boolean isEqual
    def tuyaValueScaled     // could be integer or float
    switch (foundItem.type) {
        case "bool" :       // [0:"OFF", 1:"ON"] 
        case "enum" :       // [0:"inactive", 1:"active"]
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference))
            //logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}"
            break
        case "value" :      // depends on foundItem.scale
        case "number" :
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference))
            //log.warn "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}"
            break 
       case "decimal" :
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) 
            //logDebug "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}"
            break
        default :
            logDebug "compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}"
            return [true, "none"]   // fallback - assume equal
    }
    if (isEqual == false) {
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}"
    }
    //
    return [isEqual, tuyaValueScaled]
}

//
// called from processTuyaDPfromDeviceProfile()
// compares the value of the DP foundItem against a Preference with the same name
// returns: (two results!)
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference)
//            : true  - if a preference with the same name does not exist (no preference value to update)
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!)
// 
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value
//
//  TODO: refactor!
//
def compareAndConvertTuyaToHubitatEventValue(foundItem, fncmd, doNotTrace=false) {
    if (foundItem == null) { return [true, "none"] }
    if (foundItem.type == null) { return [true, "none"] }
    def hubitatEventValue   // could be integer or float or string
    boolean isEqual
    switch (foundItem.type) {
        case "bool" :       // [0:"OFF", 1:"ON"] 
        case "enum" :       // [0:"inactive", 1:"active"]
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: "unknown", device.currentValue(foundItem.name) ?: "unknown")
            break
        case "value" :      // depends on foundItem.scale
        case "number" :
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name)))
            break        
        case "decimal" :
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name)))            
            break
        default :
            logDebug "compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}"
            return [true, "none"]   // fallback - assume equal
    }
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} "
    return [isEqual, hubitatEventValue]
}


def preProc(foundItem, fncmd_orig) {
    def fncmd = fncmd_orig
    if (foundItem == null) { return fncmd }
    if (foundItem.preProc == null) { return fncmd }
    String preProcFunction = foundItem.preProc
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}"
    // check if preProc method exists
    if (!this.respondsTo(preProcFunction)) {
        logDebug "preProc: function <b>${preProcFunction}</b> not found"
        return fncmd_orig
    }
    // execute the preProc function
    try {
        fncmd = "$preProcFunction"(fncmd_orig)
    }
    catch (e) {
        logWarn "preProc: Exception '${e}'caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))"
        return fncmd_orig
    }
    //logDebug "setFunction result is ${fncmd}"
    return fncmd
}


/**
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs.
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute.
 * If no preference exists for the DP, it logs the DP value as an info message.
 * If the DP is spammy (not needed for anything), it does not perform any further processing.
 * 
 * @return true if the DP was processed successfully, false otherwise.
 */
boolean processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd_orig, dp_len=0) {
    def fncmd = fncmd_orig
    if (state.deviceProfile == null)  { return false }
    //if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) 

    def tuyaDPsMap = deviceProfilesV2[state.deviceProfile].tuyaDPs
    if (tuyaDPsMap == null || tuyaDPsMap == []) { return false }    // no any Tuya DPs defined in the Device Profile
    
    def foundItem = null
    tuyaDPsMap.each { item ->
         if (item['dp'] == (dp as int)) {
            foundItem = item
            return
        }
    }
    if (foundItem == null) { 
        // DP was not found into the tuyaDPs list for this particular deviceProfile
        //updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)
        // continue processing the DP report in the old code ...
        return false 
    }

    return processFoundItem(foundItem, fncmd_orig)     
}


boolean processClusterAttributeFromDeviceProfile(descMap) {
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}"
    if (state.deviceProfile == null)  { return false }

    def attribMap = deviceProfilesV2[state.deviceProfile].attributes
    if (attribMap == null || attribMap == []) { return false }    // no any attributes are defined in the Device Profile
    
    def foundItem = null
    def clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}"
    def value = hexStrToUnsignedInt(descMap.value)
    //logTrace "clusterAttribute = ${clusterAttribute}"
    attribMap.each { item ->
         if (item['at'] == clusterAttribute) {
            foundItem = item
            return
        }
    }
    if (foundItem == null) { 
        // clusterAttribute was not found into the attributes list for this particular deviceProfile
        // updateStateUnknownclusterAttribute(descMap)
        // continue processing the descMap report in the old code ...
        return false 
    }

    return processFoundItem(foundItem, value) 
}



def processFoundItem (foundItem, value) {
    if (foundItem == null) { return false }
    // added 10/31/2023 - preProc the attribute value if needed
    if (foundItem.preProc != null) {
        value = preProc(foundItem, value)
        logDebug "<b>preProc</b> changed ${foundItem.name} value to ${value}"
    }
    else {
        logTrace "no preProc for ${foundItem.name} : ${foundItem}"
    }

    def name = foundItem.name                                    // preference name as in the attributes map
    def existingPrefValue = settings[name]                        // preference name as in Hubitat settings (preferences), if already created.
    def perfValue = null   // preference value
    boolean preferenceExists = existingPrefValue != null          // check if there is an existing preference for this clusterAttribute  
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute
    boolean isEqual = false
    boolean wasChanged = false
    boolean doNotTrace = false  // isSpammyDPsToNotTrace(descMap)          // do not log/trace the spammy clusterAttribute's TODO!
    if (!doNotTrace) {
        logTrace "processClusterAttributeFromDeviceProfile clusterAttribute=${clusterAttribute} ${foundItem.name} (type ${foundItem.type}, rw=${foundItem.rw} isAttribute=${isAttribute}, preferenceExists=${preferenceExists}) value is ${value} - ${foundItem.description}"
    }
    // check if the clusterAttribute has the same value as the last one, or the value has changed
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ...
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : ""
    def valueScaled    // can be number or decimal or string
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events
    
    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare !
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list
            (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace)
            descText  = "${name} is ${valueScaled} ${unitText}"        
            if (settings.logEnable) { logInfo "${descText}"}
        }
        // no more processing is needed, as this clusterAttribute is not a preference and not an attribute
        return true
    }
    
    // first, check if there is a preference defined to be updated
    if (preferenceExists) {
        // preference exists and its's value is extracted
        def oldPerfValue = device.getSetting(name)
        (isEqual, perfValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue)    
        if (isEqual == true) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference
            logDebug "no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${perfValue} (clusterAttribute raw value ${value})"
        }
        else {
            logDebug "preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${perfValue} (clusterAttribute raw value ${value})"
            if (debug) log.info "updating par ${name} from ${existingPrefValue} to ${perfValue} type ${foundItem.type}" 
            try {
                device.updateSetting("${name}",[value:perfValue, type:foundItem.type])
                wasChanged = true
            }
            catch (e) {
                logWarn "exception ${e} caught while updating preference ${name} to ${value}, type ${foundItem.type}" 
            }
        }
    }
    else {    // no preference exists for this clusterAttribute
        // if not in the spammy list - log it!
        unitText = foundItem.unit != null ? "$foundItem.unit" : ""
        //logInfo "${name} is ${value} ${unitText}"
    }    
    
    // second, send an event if this is declared as an attribute!
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace)
        descText  = "${name} is ${valueScaled} ${unitText}"
        if (settings?.logEnable == true) { descText += " (raw:${value})" }
        
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along!
            if (!doNotTrace) {
                if (settings.logEnable) { logInfo "${descText} (no change)"}
            }
            // patch for inverted motion sensor 2-in-1
            if (name == "motion" && is2in1()) {
                logDebug "patch for inverted motion sensor 2-in-1"
                // continue ... 
            }
            else {
                if (state.states != null && state.states["isRefresh"] == true) {
                    logTrace "isRefresh = true - continue and send an event, although there was no change..."
                }
                else {
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value)
                }
            }
        }
        
        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an event!

        def divider = safeToInt(foundItem.scale ?: 1) ?: 1
        def valueCorrected = value / divider
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" }
        // process the events in the device specific driver..
        if (DEVICE_TYPE in ["Thermostat"])  { processDeviceEventThermostat(name, valueScaled, unitText, descText) }
        else {
            switch (name) {
                case "motion" :
                    handleMotion(motionActive = value)  // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                    break
                case "temperature" :
                    //temperatureEvent(value / getTemperatureDiv())
                    handleTemperatureEvent(valueScaled as Float)
                    break
                case "humidity" :
                    handleHumidityEvent(valueScaled)
                    break
                case "illuminance" :
                case "illuminance_lux" :
                    handleIlluminanceEvent(valueCorrected)       
                    break
                case "pushed" :
                    logDebug "button event received value=${value} valueScaled=${valueScaled} valueCorrected=${valueCorrected}"
                    buttonEvent(valueScaled)
                    break
                default :
                    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: "physical", isStateChange: true)    // attribute value is changed - send an event !
                    if (!doNotTrace) {
                        logDebug "event ${name} sent w/ value ${valueScaled}"
                        logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy clusterAttribute ?                               
                    }
                    break
            }
            //log.trace "attrValue=${attrValue} valueScaled=${valueScaled} equal=${isEqual}"
        }
    }
    // all processing was done here!
    return true    
}




def validateAndFixPreferences(debug=false) {
    if (debug) logTrace "validateAndFixPreferences: preferences=${DEVICE.preferences}"
    if (DEVICE.preferences == null || DEVICE.preferences == [:]) {
        logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceGroup()}"
        return null
    }
    def validationFailures = 0
    def validationFixes = 0
    def total = 0
    def oldSettingValue 
    def newValue
    String settingType 
    DEVICE.preferences.each {
        Map foundMap = getPreferencesMapByName(it.key)
        if (foundMap == null) {
            logDebug "validateAndFixPreferences: map not found for preference ${it.key}"    // 10/21/2023 - sevirity lowered to debug
            return null
        }
        settingType = device.getSettingType(it.key)
        oldSettingValue = device.getSetting(it.key)
        if (settingType == null) {
            logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}"
            return null
        }
        if (debug) logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}"
        if (foundMap.type != settingType) {
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) "
            validationFailures ++
            // remove the setting and create a new one using the foundMap.type
            try {
                device.removeSetting(it.key)
                logDebug "validateAndFixPreferences: removing setting ${it.key}"
            }
            catch (e) {
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}"
                return null
            }
            // first, try to use the old setting value
            try {
                // correct the oldSettingValue type
                if (foundMap.type == "decimal")     { newValue = oldSettingValue.toDouble() }
                else if (foundMap.type == "number") { newValue = oldSettingValue.toInteger() }
                else if (foundMap.type == "bool")   { newValue = oldSettingValue == "true" ? 1 : 0 }
                else if (foundMap.type == "enum") {
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0
                    if (oldSettingValue == "true" || oldSettingValue == "false" || oldSettingValue == true || oldSettingValue == false) {
                        newValue = (oldSettingValue == "true" || oldSettingValue == true) ? "1" : "0"
                    }
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals
                    else if (foundMap.map.keySet().toString().any { it.contains(".") }) {
                        newValue = String.format("%.2f", oldSettingValue)
                    }
                    else {
                        // format the settingValue as a string of the integer value
                        newValue = String.format("%d", oldSettingValue)
                    }
                }
                //
                device.updateSetting(it.key, [value:newValue, type:foundMap.type])
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}"
                validationFixes ++
            }
            catch (e) {
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}"
                // change the settingValue to the foundMap default value
                try {
                    settingValue = foundMap.defaultValue
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type])
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} "
                    validationFixes ++
                }
                catch (e2) {
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>"
                    return null
                }            
            }
        }
        total ++
    }
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}"
}


