/* groovylint-disable DuplicateNumberLiteral, DuplicateStringLiteral, LineLength */
/**
 *  MIT License
 *  Copyright 2023 Jonathan Bradshaw (jb@nrgup.net)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the 'Software'), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

import groovy.transform.Field
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType

/*
 *  Known issues:
 *  * Xiaomi devices send reports based on changes, and a status report every 50-60 minutes. These settings cannot be adjusted.
 *  * The battery level / voltage is not reported at pairing. Wait for the first status report, 50-60 minutes after pairing.
 *    However, the Aqara Door/Window sensor battery level can be retrieved immediately with a short-press of the reset button.
 *  * Pairing Xiaomi devices can be difficult as they were not designed to use with a Hubitat hub.
 *    Holding the sensor's reset button until the LED blinks will start pairing mode.
 *    3 quick flashes indicates success, while one long flash means pairing has not started yet.
 *    In either case, keep the sensor "awake" by short-pressing the reset button repeatedly, until recognized by Hubitat.
 *  * The connection can be dropped without warning. To reconnect, put Hubitat in "Discover Devices" mode, and follow
 *    the same steps for pairing. As long as it has not been removed from the Hubitat device list, when the LED
 *    flashes 3 times, the Aqara Motion Sensor should be reconnected and will resume reporting as normal
 */

 metadata {
    definition(name: 'Aqara/Xiaomi Contact Sensor',       importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-public/main/Aqara/AqaraContactSensorE1.groovy',
        namespace: 'aqara', author: 'Jonathan Bradshaw') {
        capability 'Contact Sensor'
        capability 'Battery'
        capability 'Sensor'
        capability 'Signal Strength'
        capability 'Voltage Measurement'

        command    'initializeMCCGQ14LM'

        attribute 'healthStatus', 'enum', [ 'unknown', 'offline', 'online' ]

        // fingerprint for Xiaomi "Original" Door/Window Sensor
        fingerprint endpointId: '01', profileId: '0104', deviceId: '0104', inClusters: '0000,0003,FFFF,0019',
            outClusters: '0000,0004,0003,0006,0008,0005,0019', manufacturer: 'LUMI', model: 'lumi.sensor_magnet'

        // fingerprint for Xiaomi Aqara Door/Window Sensor
        fingerprint endpointId: '01', profileId: '0104', deviceId: '5F01', inClusters: '0000,0003,FFFF,0006',
            outClusters: '0000,0004,FFFF', manufacturer: 'LUMI', model: 'lumi.sensor_magnet.aq2'

        // kkossev Aqara MCCGQ14LM - NOT WORKING! 
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0500,FCC0', outClusters:'0003,0019', model:'lumi.magnet.acn001', manufacturer:'LUMI', controllerType: 'ZGB'
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description:\
            '<i>Enables event description logging.</i>'

        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description:\
            '<i>Enables debug logging for 30 minutes.</i>'
    }
}

@Field static final String VERSION = '2023-03-28'
@Field static final int HEALTHCHECK_INTERVAL_SEC = 3300 // hardcoded in device firmware

Boolean isMCCGQ14LM() {
    return device?.getDataValue('model') == 'lumi.magnet.acn001'
}

/**
 *  Scheduled update of health status attribute to offline.
 */
void healthCheck() {
    updateAttribute('healthStatus', 'offline')
}

/**
 *  Invoked when device is installed.
 */
void installed() {
    log.info 'installed'
    // populate some default values for attributes
    sendEvent(name: 'healthStatus', value: 'unknown')
    sendEvent(name: 'contact', value: 'unknown')
    if (isMCCGQ14LM()) {
        initializeMCCGQ14LM()
    }
}

void initializeMCCGQ14LM() {
    log.info 'initializing MCCGQ14LM...'
    ArrayList<String> cmds = []
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',]
        //cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0500 {${device.zigbeeId}} {}"
        //cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage
        //cmds += zigbee.enrollResponse(100) + zigbee.readAttribute(0x0500, 0x0000)
        //cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
        cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 3600, 7200, 1  /*0*/, [:], 101)   // Configure Voltage - Report once per 6hrs or if a change of 100mV detected
    log.debug "initializeMCCGQ14LM: ${cmds}"
    sendZigbeeCommands(cmds)
}

/**
 *  Scheduled disable of debug logging.
 */
void logsOff() {
    log.warn 'debug logging disabled...'
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

/**
 *  Invoked when device settings are updated.
 */
void updated() {
    log.info 'updated...'
    log.info "driver version ${VERSION}"
    unschedule()

    if (settings.logEnable) {
        log.debug settings
        runIn(1800, logsOff)
    }
}

/**
 *  Parses a Zigbee message received as a String input.
 *  Handles the Basic and On/Off clusters by invoking the appropriate cluster parsing method.
 */
void parse(final String description) {
    if (settings.logEnable) { log.debug "zigbee parse description: ${description}" }
    // Fix for 0xFF01 should be Octet String (0x41) but is sent as a Character String (0x42)
    final Map descMap = zigbee.parseDescriptionAsMap(description.replace('01FF42', '01FF41'))
    updateAttribute('healthStatus', 'online')
    runIn(HEALTHCHECK_INTERVAL_SEC, 'healthCheck')

    if (settings.logEnable) {
        final String clusterName = clusterLookup(descMap.clusterInt)
        final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : ''
        if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute }
    }

    switch (descMap.clusterInt as Integer) {
        case zigbee.BASIC_CLUSTER:
            parseBasicCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) }
            break
        case zigbee.ON_OFF_CLUSTER:
            parseOnOffCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) }
            break
        default:
            if (settings.logEnable) {
                log.debug "zigbee received unknown message cluster: ${descMap}"
            }
            break
    }
}

/**
 *  This function parses the Basic cluster, looking for attributes that are
 *  relevant to the contact sensor, and updating the appropriate attributes.
 *
 *  @param descMap The map of attribute data from the ZigBee device
 */
void parseBasicCluster(final Map descMap) {
    switch (descMap.attrInt as Integer) {
        case MODEL_ID:
            final String model = descMap.value ?: 'unknown'
            log.info "device model is ${model}"
            updateDataValue('manufacturer', 'LUMI')
            updateDataValue('model', model)
            break
        case VERSION_ID:
            try {
                final String version = descMap.value ?: 'unknown'
                log.info "device version is ${version}"
                updateDataValue('firmwareVersion', version)
            } catch (Exception e) {
                log.warn "zigbee failed to parse firmware version: ${descMap.value}"
            }
            break
        case XIAOMI_SPECIAL_REPORT_ID:
            final Map<Integer, Object> tags = decodeXiaomiTags(descMap.value as String)
            parseXiaomiSpecialTags(tags)
            break
        default:
            log.warn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

/**
 *  This function parses the On/Off cluster, looking for attributes that are
 *  relevant to the contact sensor, and updating the appropriate attributes.
 *
 *  @param descMap The map of attribute data from the ZigBee device
 */
void parseOnOffCluster(final Map descMap) {
    switch (descMap.attrInt as Integer) {
        case 0x00:
            final String state = descMap.value == '00' ? 'closed' : 'open'
            updateAttribute('contact', state)
            break
        default:
            log.warn "zigbee received unknown On/Off cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

/**
 * Parses the Xiaomi special tags and updates the device attributes accordingly.
 *
 * @param tags The Xiaomi tags to be parsed.
 */
void parseXiaomiSpecialTags(final Map<Integer, Object> tags) {
    tags.each { final Integer tag, final Object value ->
        switch (tag) {
            case 0x01: // Battery voltage (mV)
                final BigDecimal minVolts = 2.5
                final BigDecimal maxVolts = 3.0
                BigDecimal rawVolts = (value as BigInteger) / 1000f
                updateAttribute('voltage', rawVolts.toDouble().round(2), 'V')
                rawVolts = rawVolts.max(minVolts).min(maxVolts)
                final int pct = (int) (Math.round((rawVolts - minVolts) / (maxVolts - minVolts)) * 100)
                updateAttribute('battery', pct, '%')
                break
            case 0x03: // Internal Temperature
                if (settings.logEnable) {
                    log.debug "device temperature ${value}&deg;"
                }
                break
            case 0x05: // RSSI
                updateAttribute('rssi', 0 - (value as BigInteger), 'dBm')
                break
            default:
                if (settings.logEnable) {
                    log.debug "xiaomi tag #${tag}: ${value}"
                }
        }
    }
}

/**
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and
 *  returns a map of decoded tag number and value pairs where the value is either a
 *  BigInteger for fixed values or a String for variable length.
 *
 *  @param hexString The data value from the Xiaomi special attribute.
 *  @return A map of the tag and value.
 */
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) {
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
 *  This code converts a cluster ID to a cluster name.
 *  The cluster ID can be either a hex string or an integer.
 *  If the cluster ID is not recognized, the hex string is returned.
 */
private String clusterLookup(final Object cluster) {
    final int clusterInt = cluster in String ? hexStrToUnsignedInt(cluster) : cluster.toInteger()
    final String label = zigbee.clusterLookup(clusterInt)?.clusterLabel
    final String hex = "0x${intToHexStr(clusterInt, 2)}"
    return label ? "${label} (${hex}) cluster" : "cluster ${hex}"
}

/**
 *  Updates an attribute of a device with the given value and optional unit and type.
 *  It also logs the update if the new value is different from the current value and if the
 *  txtEnable setting is enabled. It then sends an event with the updated attribute information.
 */
private void updateAttribute(final String attribute, final Object value, final String unit = null, final String type = null) {
    final String descriptionText = "${attribute} was set to ${value}${unit ?: ''}"
    if (device.currentValue(attribute) != value && settings.txtEnable) {
        log.info descriptionText
    }
    sendEvent(name: attribute, value: value, unit: unit, type: type, descriptionText: descriptionText)
}

void sendZigbeeCommands(List<String> cmd) {
    if (settings?.logEnable) {
        log.trace "${device.displayName} sendZigbeeCommands(cmd=$cmd)"
    }
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    sendHubCommand(allActions)
}


// Zigbee Attribute IDs
@Field static final int MODEL_ID = 0x0005
@Field static final int VERSION_ID = 0x0001
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0xFF01
