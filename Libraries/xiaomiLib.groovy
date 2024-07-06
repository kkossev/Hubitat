/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter, UnnecessaryPublicModifier */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Xiaomi Library', name: 'xiaomiLib', namespace: 'kkossev', importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', documentationLink: '',
    version: '3.3.0'
)
/*
 *  Xiaomi Library
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
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch)
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code;
 * ver. 1.1.0  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.0 alignmment
 * ver. 3.2.2  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.2 alignmment
 * ver. 3.3.0  2024-06-23 kkossev  - (dev. branch) comonLib 3.3.0 alignmment; added parseXiaomiClusterSingeTag() method
 *
 *                                   TODO: remove the DEVICE_TYPE dependencies for Bulb, Thermostat, AqaraCube, FP1, TRV_OLD
 *                                   TODO: remove the isAqaraXXX  dependencies !!
*/

static String xiaomiLibVersion()   { '3.3.0' }
static String xiaomiLibStamp() { '2024/06/23 9:36 AM' }

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] }
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] }
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] }
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] }
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] }

// no metadata for this library!

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0

// Zigbee Attributes
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144
@Field static final int MODEL_ATTR_ID = 0x05
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143
@Field static final int PRESENCE_ATTR_ID = 0x0142
@Field static final int REGION_EVENT_ATTR_ID = 0x0151
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154
@Field static final int SET_REGION_ATTR_ID = 0x0150
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ]

// Xiaomi Tags
@Field static final int DIRECTION_MODE_TAG_ID = 0x67
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66
@Field static final int SWBUILD_TAG_ID = 0x08
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66
@Field static final int PRESENCE_TAG_ID = 0x65

// called from parseXiaomiCluster() in the main code, if no customParse is defined
// TODO - refactor AqaraCube specific code
// TODO - refactor for Thermostat and Bulb specific code
void standardParseXiaomiFCC0Cluster(final Map descMap) {
    if (settings.logEnable) {
        logTrace "standardParseXiaomiFCC0Cluster: zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
    if (DEVICE_TYPE in  ['Thermostat']) {
        parseXiaomiClusterThermostatLib(descMap)
        return
    }
    if (DEVICE_TYPE in  ['Bulb']) {
        parseXiaomiClusterRgbLib(descMap)
        return
    }
    // TODO - refactor AqaraCube specific code
    // TODO - refactor FP1 specific code
    final String funcName = 'standardParseXiaomiFCC0Cluster'
    switch (descMap.attrInt as Integer) {
        case 0x0009:                      // Aqara Cube T1 Pro
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "standardParseXiaomiFCC0Cluster: AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" }
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" }
            break
        case 0x00FC:                      // FP1
            logWarn "${funcName}: unknown attribute - resetting?"
            break
        case PRESENCE_ATTR_ID:            // 0x0142 FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            parseXiaomiClusterPresence(value)
            break
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            parseXiaomiClusterPresenceAction(value)
            break
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1
            // Region events can be sent fast and furious so buffer them
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1])
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3])
            if (settings.logEnable) {
                log.debug "${funcName}: xiaomi: region ${regionId} action is ${value}"
            }
            if (device.currentValue("region${regionId}") != null) {
                RegionUpdateBuffer.get(device.id).put(regionId, value)
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions')
            }
            break
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})"
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum'])
            break
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})"
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum'])
            break
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})"
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum'])
            break
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) }
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" }
            break
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5)
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) }
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" }
            break
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
            parseXiaomiClusterTags(tags)
            if (isAqaraCube()) {
                sendZigbeeCommands(customRefresh())
            }
            break
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value)
            if (rawData.size() == 24 && settings.enableDistanceDirection) {
                final int degrees = rawData[19]
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff)
                if (settings.logEnable) {
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm"
                }
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ])
            }
            break
        default:
            log.warn "${funcName}: zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

// cluster 0xFCC0 attribute  0x00F7 is sent as a keep-alive beakon every 55 minutes
public void parseXiaomiClusterTags(final Map<Integer, Object> tags) {
    final String funcName = 'parseXiaomiClusterTags'
    tags.each { final Integer tag, final Object value ->
        parseXiaomiClusterSingeTag(tag, value)
    }
}

public void parseXiaomiClusterSingeTag(final Integer tag, final Object value) {
    final String funcName = 'parseXiaomiClusterSingeTag'
    switch (tag) {
        case 0x01:    // battery voltage
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})"
            break
        case 0x03:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;"
            break
        case 0x05:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} RSSI is ${value}"
            break
        case 0x06:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} LQI is ${value}"
            break
        case 0x08:            // SWBUILD_TAG_ID:
            final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0')
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})"
            device.updateDataValue('aqaraVersion', swBuild)
            break
        case 0x0a:
            String nwk = intToHexStr(value as Integer, 2)
            if (state.health == null) { state.health = [:] }
            String oldNWK = state.health['parentNWK'] ?: 'n/a'
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>"
            if (oldNWK != nwk ) {
                logWarn "parentNWK changed from ${oldNWK} to ${nwk}"
                state.health['parentNWK']  = nwk
                state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1
            }
            break
        case 0x0b:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} light level is ${value}"
            break
        case 0x64:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC
            // TODO - also smoke gas/density if UINT !
            break
        case 0x65:
            if (isAqaraFP1()) { logDebug "${funcName} PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }
            else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC
            break
        case 0x66:
            if (isAqaraFP1()) { logDebug "${funcName} SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }
            else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb)
            else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" }
            break
        case 0x67:
            if (isAqaraFP1()) { logDebug "${funcName} DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC:
            // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1]
            break
        case 0x69:
            if (isAqaraFP1()) { logDebug "${funcName} TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }
            break
        case 0x6a:
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }
            else              { logDebug "${funcName} MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" }
            break
        case 0x6b:
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }
            else              { logDebug "${funcName} MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" }
            break
        case 0x95:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} energy is ${value}"
            break
        case 0x96:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} voltage is ${value}"
            break
        case 0x97:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} current is ${value}"
            break
        case 0x98:
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} power is ${value}"
            break
        case 0x9b:
            if (isAqaraCube()) {
                logDebug "${funcName} Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})"
                sendAqaraCubeOperationModeEvent(value as int)
            }
            else { logDebug "${funcName} CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" }
            break
        default:
            logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
    }
}

/**
 *  Reads a specified number of little-endian bytes from a given
 *  ByteArrayInputStream and returns a BigInteger.
 */
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) {
    final byte[] byteArr = new byte[length]
    stream.read(byteArr, 0, length)
    BigInteger bigInt = BigInteger.ZERO
    for (int i = byteArr.length - 1; i >= 0; i--) {
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i)))
    }
    return bigInt
}

/**
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and
 *  returns a map of decoded tag number and value pairs where the value is either a
 *  BigInteger for fixed values or a String for variable length.
 */
private Map<Integer, Object> decodeXiaomiTags(final String hexString) {
    try {
        final Map<Integer, Object> results = [:]
        final byte[] bytes = HexUtils.hexStringToByteArray(hexString)
        new ByteArrayInputStream(bytes).withCloseable { final stream ->
            while (stream.available() > 2) {
                int tag = stream.read()
                int dataType = stream.read()
                Object value
                if (DataType.isDiscrete(dataType)) {
                    int length = stream.read()
                    byte[] byteArr = new byte[length]
                    stream.read(byteArr, 0, length)
                    value = new String(byteArr)
                } else {
                    int length = DataType.getLength(dataType)
                    value = readBigIntegerBytes(stream, length)
                }
                results[tag] = value
            }
        }
        return results
    }
    catch (e) {
        if (settings.logEnable) { "${device.displayName} decodeXiaomiTags: ${e}" }
        return [:]
    }
}

List<String> refreshXiaomi() {
    List<String> cmds = []
    if (cmds == []) { cmds = ['delay 299'] }
    return cmds
}

List<String> configureXiaomi() {
    List<String> cmds = []
    logDebug "configureXiaomi() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299'] }    // no ,
    return cmds
}

List<String> initializeXiaomi() {
    List<String> cmds = []
    logDebug "initializeXiaomi() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299',] }
    return cmds
}

void initVarsXiaomi(boolean fullInit=false) {
    logDebug "initVarsXiaomi(${fullInit})"
}

void initEventsXiaomi(boolean fullInit=false) {
    logDebug "initEventsXiaomi(${fullInit})"
}

List<String> standardAqaraBlackMagic() {
    return []
    /////////////////////////////////////////
    List<String> cmds = []
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) {
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',]
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage
        if (isAqaraTVOC_OLD()) {
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only
        }
        logDebug 'standardAqaraBlackMagic()'
    }
    return cmds
}
