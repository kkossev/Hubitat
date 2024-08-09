/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee IASLibrary', name: 'iasLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/iasLib.groovy', documentationLink: '',
    version: '3.2.2'

)
/*
 *  Zigbee IAS Library
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
 * ver. 3.2.0  2024-05-27 kkossev  - added iasLib.groovy
 * ver. 3.2.1  2024-07-06 kkossev  - added standardParseIasMessage (debug only); zs null check
 * ver. 3.2.2  2024-08-09 kkossev  - (dev. branch) zs null check
 *
 *                                   TODO:
*/

static String iasLibVersion()   { '3.2.2' }
static String iasLibStamp() { '2024/08/09 12:03 PM' }

metadata {
    // no capabilities
    // no attributes
    // no commands
    preferences {
    // no prefrences
    }
}

@Field static final Map<Integer, String> IAS_ATTRIBUTES = [
    //  Zone Information
    0x0000: 'zone state',
    0x0001: 'zone type',
    0x0002: 'zone status',
    //  Zone Settings
    0x0010: 'CIE addr',    // EUI64
    0x0011: 'Zone Id',     // uint8
    0x0012: 'Num zone sensitivity levels supported',     // uint8
    0x0013: 'Current zone sensitivity level',            // uint8
    0xF001: 'Current zone keep time'                     // uint8
]

@Field static final Map<Integer, String> ZONE_TYPE = [
    0x0000: 'Standard CIE',
    0x000D: 'Motion Sensor',
    0x0015: 'Contact Switch',
    0x0028: 'Fire Sensor',
    0x002A: 'Water Sensor',
    0x002B: 'Carbon Monoxide Sensor',
    0x002C: 'Personal Emergency Device',
    0x002D: 'Vibration Movement Sensor',
    0x010F: 'Remote Control',
    0x0115: 'Key Fob',
    0x021D: 'Key Pad',
    0x0225: 'Standard Warning Device',
    0x0226: 'Glass Break Sensor',
    0x0229: 'Security Repeater',
    0xFFFF: 'Invalid Zone Type'
]

@Field static final Map<Integer, String> ZONE_STATE = [
    0x00: 'Not Enrolled',
    0x01: 'Enrolled'
]

public void standardParseIasMessage(final String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn
    Map zs = zigbee.parseZoneStatusChange(description)
    if (zs == null) {
        logWarn "standardParseIasMessage: zs is null!"
        return
    }
    if (zs.alarm1Set == true) {
        logDebug "standardParseIasMessage: Alarm 1 is set"
        //handleMotion(true)
    }
    else {
        logDebug "standardParseIasMessage: Alarm 1 is cleared"
        //handleMotion(false)
    }
}

public void standardParseIASCluster(final Map descMap) {
    logDebug "standardParseIASCluster: cluster=${descMap} attrInt=${descMap.attrInt} value=${descMap.value}"
    if (descMap.cluster != '0500') { return } // not IAS cluster
    if (descMap.attrInt == null) { return } // missing attribute
    //String zoneSetting = IAS_ATTRIBUTES[descMap.attrInt]
    if ( IAS_ATTRIBUTES[descMap.attrInt] == null ) {
        logWarn "standardParseIASCluster: Unknown IAS attribute ${descMap?.attrId} (value:${descMap?.value})"
        return
    } // unknown IAS attribute
    /*
    logDebug "standardParseIASCluster: Don't know how to handle IAS attribute 0x${descMap?.attrId} '${zoneSetting}' (value:${descMap?.value})!"
    return
    */

    String clusterInfo = 'standardParseIASCluster:'

    if (descMap?.cluster == '0500' && descMap?.command in ['01', '0A']) {    //IAS read attribute response
        logDebug "${standardParseIASCluster} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}"
        if (descMap?.attrId == '0000') {
            int value = Integer.parseInt(descMap?.value, 16)
            String status = "${ZONE_STATE[value]}"
            if (value == 0 ) { status = "<b>${status}</b>" ; logWarn "${clusterInfo} is NOT ENROLLED!" }
            logInfo "${clusterInfo} IAS Zone State report is '${status}' (${value})"
        }
        else if (descMap?.attrId == '0001') {
            int value = Integer.parseInt(descMap?.value, 16)
            logInfo "${clusterInfo} IAS Zone Type report is '${ZONE_TYPE[value]}' (${value})"
        }
        else if (descMap?.attrId == '0002') {
            logInfo "${clusterInfo} IAS Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
        }
        else if (descMap?.attrId == '0010') {
            logInfo "${clusterInfo} IAS Zone Address received (bitmap = ${descMap?.value})"
        }
        else if (descMap?.attrId == '0011') {
            logInfo "${clusterInfo} IAS Zone ID: ${descMap.value}"
        }
        else if (descMap?.attrId == '0012') {
            logInfo "${clusterInfo} IAS Num zone sensitivity levels supported: ${descMap.value}"
        }
        else if (descMap?.attrId == '0013') {
            int value = Integer.parseInt(descMap?.value, 16)
            //logInfo "${clusterInfo} IAS Current Zone Sensitivity Level = ${sensitivityOpts.options[value]} (${value})"
            logInfo "${clusterInfo} IAS Current Zone Sensitivity Level = (${value})"
        // device.updateSetting('settings.sensitivity', [value:value.toString(), type:'enum'])
        }
        else if (descMap?.attrId == 'F001') {    // [raw:7CC50105000801F02000, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:F001, encoding:20, command:0A, value:00, clusterInt:1280, attrInt:61441]
            int value = Integer.parseInt(descMap?.value, 16)
            //String str   = getKeepTimeOpts().options[value]
            //logInfo "${clusterInfo} Current IAS Zone Keep-Time =  ${str} (${value})"
            logInfo "${clusterInfo} Current IAS Zone Keep-Time =  (${value})"
        //device.updateSetting('keepTime', [value: value.toString(), type: 'enum'])
        }
        else {
            logDebug "${clusterInfo} Zone status attribute ${descMap?.attrId}: <b>NOT PROCESSED</b> ${descMap}"
        }
    } // if IAS read attribute response
    else if (descMap?.clusterId == '0500' && descMap?.command == '04') {    //write attribute response (IAS)
        logDebug "${clusterInfo} AS write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}"
    }
    else {
        logDebug "${clusterInfo} <b>NOT PROCESSED</b> ${descMap}"
    }
}

List<String> refreshAllIas() {
    logDebug 'refreshAllIas()'
    List<String> cmds = []
    IAS_ATTRIBUTES.each { key, value ->
        cmds += zigbee.readAttribute(0x0500, key, [:], delay = 199)
    }
    return cmds
}
