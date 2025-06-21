/**
 *  MIT License
 *  Copyright 2022 Jonathan Bradshaw (jb@nrgup.net)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 *  version 2.0.0 - 2025-06-21 kkossev
 *
 *                             TODO: 
 **/

library(
        name: 'espHomeApiHelperKKmod',
        namespace: 'esphome',
        author: 'jb@nrgup.net',
        description: 'ESPHome Native Protobuf API Library',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/ESPHome/Drivers/ESPHome/Libraries/ESPHome-API-Library-KK-mod.groovy'
)

@Field static final String API_HELPER_VERSION = '2.0.0'   // was 1.2

import groovy.transform.CompileStatic
import groovy.transform.Field
import hubitat.helper.HexUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ESPHome API Implementation
 * https://github.com/esphome/aioesphomeapi/blob/main/aioesphomeapi/api.proto
 */

@Field static final int PING_INTERVAL_SECONDS = 120 // Maximum 160 seconds
@Field static final int API_PORT_NUMBER = 6053
@Field static final int SEND_RETRY_COUNT = 5
@Field static final int SEND_RETRY_SECONDS = 5
@Field static final int MAX_RECONNECT_SECONDS = 60
@Field static final String NETWORK_ATTRIBUTE = 'networkStatus' // Device attribute

// Static objects shared between all devices using this driver library
@Field static final Map<String, ByteArrayOutputStream> espReceiveBuffer = new ConcurrentHashMap<>()
@Field static final Map<String, ConcurrentLinkedQueue> espSendQueue = new ConcurrentHashMap<>()
@Field static final Random random = new Random()

/**
 * ESPHome Native API Plaintext Socket IO Implementation
 */
void openSocket() {
    if (device.isDisabled()) {
        state.reconnectDelay = MAX_RECONNECT_SECONDS
    } else {
        try {
            setNetworkStatus('connecting', "host ${settings.ipAddress}:${API_PORT_NUMBER}")
            interfaces.rawSocket.connect(settings.ipAddress, API_PORT_NUMBER, byteInterface: true)
            runInMillis(250, 'espHomeHelloRequest')
            return
        } catch (e) {
            setNetworkStatus('offline', e.getMessage())
        }
    }
    scheduleConnect()
}

void closeSocket(String reason) {
    unschedule('healthCheck')
    unschedule('sendMessageQueue')
    espReceiveBuffer.remove(device.id)
    logInfoLib "ESPHome closing socket to ${settings.ipAddress}:${API_PORT_NUMBER}"
    if (!isOffline()) {
        sendMessage(MSG_DISCONNECT_REQUEST)
    }
    setNetworkStatus('offline', reason)
    device.updateDataValue 'Last Disconnected Time', "${new Date()} (${reason})"
    interfaces.rawSocket.close()
    pauseExecution(1000)
}

// parse received socket status - do not change this function name or driver will break
@CompileStatic
void socketStatus(String message) {
    if (message.contains('error')) {
        logWarnLib "ESPHome socket error: ${message}"
        closeSocket(message)
        scheduleConnect()
    } else {
        logWarnLib "ESPHome socket status: ${message}"
    }
}

/*
 * ESPHome Commands
 */
@Field static final int WIRETYPE_VARINT = 0
@Field static final int WIRETYPE_FIXED64 = 1
@Field static final int WIRETYPE_LENGTH_DELIMITED = 2
@Field static final int WIRETYPE_FIXED32 = 5
@Field static final int VARINT_MAX_BYTES = 10

@CompileStatic
void espHomeButtonCommand(Map<String, Object> tags) {
    sendMessage(MSG_BUTTON_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ]
    ])
}

@CompileStatic
void espHomeCameraImageRequest(Map<String, Object> tags) {
    sendMessage(MSG_CAMERA_IMAGE_REQUEST, [
            1: [ tags.single ? 1 : 0, WIRETYPE_VARINT ],
            2: [ tags.stream ? 1 : 0, WIRETYPE_VARINT ]
    ])
}

@CompileStatic
void espHomeCoverCommand(Map<String, Object> tags) {
    sendMessage(MSG_COVER_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ],
            4: [ tags.position != null ? 1 : 0, WIRETYPE_VARINT ],
            5: [ tags.position as Float, WIRETYPE_FIXED32 ],
            6: [ tags.tilt != null ? 1 : 0, WIRETYPE_VARINT ],
            7: [ tags.tilt as Float, WIRETYPE_FIXED32 ],
            8: [ tags.stop ? 1 : 0, WIRETYPE_VARINT ]
    ], MSG_COVER_STATE_RESPONSE)
}

void espHomeDisconnectRequest() {
    closeSocket('requested by device')
    state.reconnectDelay = MAX_RECONNECT_SECONDS
    scheduleConnect()
}

@CompileStatic
void espHomeFanCommand(Map<String, Object> tags) {
    sendMessage(MSG_FAN_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ],
            2: [ tags.state != null ? 1 : 0, WIRETYPE_VARINT ],
            3: [ tags.state ? 1 : 0, WIRETYPE_VARINT ],
            6: [ tags.oscillating != null ? 1 : 0, WIRETYPE_VARINT ],
            7: [ tags.oscillating ? 1 : 0, WIRETYPE_VARINT ],
            8: [ tags.direction != null ? 1 : 0, WIRETYPE_VARINT ],
            9: [ tags.direction as Integer, WIRETYPE_VARINT ],
            10: [ tags.speedLevel != null ? 1 : 0, WIRETYPE_VARINT ],
            11: [ tags.speedLevel as Integer, WIRETYPE_VARINT ]
    ], MSG_FAN_STATE_RESPONSE)
}

@CompileStatic
void espHomeLightCommand(Map<String, Object> tags) {
    sendMessage(MSG_LIGHT_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ],
            2: [ tags.state != null ? 1 : 0, WIRETYPE_VARINT ],
            3: [ tags.state ? 1 : 0, WIRETYPE_VARINT ],
            4: [ tags.masterBrightness != null ? 1 : 0, WIRETYPE_VARINT ],
            5: [ tags.masterBrightness as Float, WIRETYPE_FIXED32 ],
            6: [ (tags.red != null && tags.green != null && tags.blue != null) ? 1 : 0, WIRETYPE_VARINT ],
            7: [ tags.red as Float, WIRETYPE_FIXED32 ],
            8: [ tags.green as Float, WIRETYPE_FIXED32 ],
            9: [ tags.blue as Float, WIRETYPE_FIXED32 ],
            10: [ tags.white != null ? 1 : 0, WIRETYPE_VARINT ],
            11: [ tags.white as Float, WIRETYPE_FIXED32 ],
            12: [ tags.colorTemperature != null ? 1 : 0, WIRETYPE_VARINT ],
            13: [ tags.colorTemperature as Float, WIRETYPE_FIXED32 ],
            14: [ tags.transitionLength != null ? 1 : 0, WIRETYPE_VARINT ],
            15: [ tags.transitionLength as Integer, WIRETYPE_VARINT ],
            16: [ tags.flashLength != null ? 1 : 0, WIRETYPE_VARINT ],
            17: [ tags.flashLength as Integer, WIRETYPE_VARINT ],
            18: [ tags.effect != null ? 1 : 0, WIRETYPE_VARINT ],
            19: [ tags.effect as String, WIRETYPE_LENGTH_DELIMITED ],
            20: [ tags.colorBrightness != null ? 1 : 0, WIRETYPE_VARINT ],
            21: [ tags.colorBrightness as Float, WIRETYPE_FIXED32 ],
            22: [ tags.colorMode != null ? 1 : 0, WIRETYPE_VARINT ],
            23: [ tags.colorMode as Integer, WIRETYPE_VARINT ]
    ], MSG_LIGHT_STATE_RESPONSE)
}

@CompileStatic
void espHomeLockCommand(Map<String, Object> tags) {
    sendMessage(MSG_LOCK_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ],
            2: [ tags.lockCommand as Integer, WIRETYPE_VARINT ],
            3: [ tags.code != null ? 1 : 0, WIRETYPE_VARINT ],
            4: [ tags.code as String, WIRETYPE_LENGTH_DELIMITED ]
    ], MSG_LOCK_STATE_RESPONSE)
}

@CompileStatic
void espHomeMediaPlayerCommand(Map<String, Object> tags) {
    sendMessage(MSG_MEDIA_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ],
            2: [ tags.mediaPlayerCommand != null ? 1 : 0, WIRETYPE_VARINT ],
            3: [ tags.mediaPlayerCommand as Integer, WIRETYPE_VARINT ],
            4: [ tags.volume != null ? 1 : 0, WIRETYPE_VARINT ],
            5: [ tags.volume as Float, WIRETYPE_FIXED32 ],
            6: [ tags.mediaUrl != null ? 1 : 0, WIRETYPE_VARINT ],
            7: [ tags.mediaUrl as String, WIRETYPE_LENGTH_DELIMITED ]
    ], MSG_MEDIA_STATE_RESPONSE)
}

@CompileStatic
void espHomeNumberCommand(Map<String, Object> tags) {
    sendMessage(MSG_NUMBER_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ],
            2: [ tags.state as Float, WIRETYPE_FIXED32 ]
    ])
}

@CompileStatic
void espHomeSelectCommand(Map<String, Object> tags) {
    sendMessage(MSG_SELECT_COMMAND_REQUEST, [
            1: [tags.key as Integer, WIRETYPE_FIXED32 ],
            2: [tags.state as String, WIRETYPE_LENGTH_DELIMITED ]
    ], MSG_SELECT_STATE_RESPONSE)
}

@CompileStatic
void espHomeSirenCommand(Map<String, Object> tags) {
    sendMessage(MSG_SIREN_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ],
            2: [ tags.state != null ? 1 : 0, WIRETYPE_VARINT ],
            3: [ tags.state ? 1 : 0, WIRETYPE_VARINT ],
            4: [ tags.tone != null ? 1 : 0, WIRETYPE_VARINT ],
            5: [ tags.tone as String, WIRETYPE_LENGTH_DELIMITED ],
            6: [ tags.duration != null ? 1 : 0, WIRETYPE_VARINT ],
            7: [ tags.duration as Integer, WIRETYPE_VARINT ],
            8: [ tags.volume != null ? 1 : 0, WIRETYPE_VARINT ],
            9: [ tags.volume as Float, WIRETYPE_FIXED32 ]
    ], MSG_SIREN_STATE_RESPONSE)
}

void espHomeSubscribe() {
    logInfoLib 'Subscribing to ESPHome HA services'
    espHomeSubscribeHaServicesRequest()

    logInfoLib "Subscribing to ESPHome ${settings.logEnable ? 'DEBUG' : 'INFO'} logging"
    espHomeSubscribeLogs(settings.logEnable ? LOG_LEVEL_DEBUG : LOG_LEVEL_INFO)

    logInfoLib 'Subscribing to ESPHome device states'
    espHomeSubscribeStatesRequest()

    sendMessageQueue()
}

void espHomeCallService(String serviceName) {
    Map service = state.services.find { service -> service.objectId == serviceName }
    if (service) {
        if (settings.logEnable) { logTraceLib "Calling ESPHome Service: ${serviceName}" }
        espHomeExecuteServiceRequest(service)
    } else {
        if (settings.logEnable) { logErrorLib "No ESPHome Service found: ${serviceName}" }
    }
}

@CompileStatic
void espHomeSubscribeBtleRequest() {
    sendMessage(MSG_SUBSCRIBE_BTLE_REQUEST)
}

@CompileStatic
void espHomeSwitchCommand(Map<String, Object> tags) {
    sendMessage(MSG_SWITCH_COMMAND_REQUEST, [
            1: [ tags.key as Integer, WIRETYPE_FIXED32 ],
            2: [ tags.state ? 1 : 0, WIRETYPE_VARINT ],
    ], MSG_SWITCH_STATE_RESPONSE)
}

/*
 * ESPHome Message Parsing
 */
// parse received protobuf messages - do not change this function name or driver will break
@CompileStatic
void parse(String hexString) {
    ByteArrayInputStream stream = hexDecode(hexString)
    int b
    while ((b = stream.read()) != -1) {
        if (b == 0x00) {
            stream.mark(0)
            long length = readVarInt(stream, true)
            int available = stream.available()
            if (length > available) {
                stream.reset()
                stashBuffer(stream)
                return
            }
            parseMessage(stream, length)
        } else if (b == 0x01) {
            logWarnLib 'Driver does not support ESPHome native API encryption'
            return
        } else {
            logWarnLib "ESPHome expecting delimiter 0x00 but got 0x${Integer.toHexString(b)} instead"
            return
        }
    }
}

@CompileStatic
private static Boolean getBooleanTag(Map<Integer, List> tags, int index, boolean invert = false) {
    return tags && tags[index] && tags[index][0] ? !invert : invert
}

@CompileStatic
private static BigDecimal getFloatTag(Map<Integer, List> tags, int index, BigDecimal defaultValue = 0) {
    try {
        return tags && tags[index] ? Float.intBitsToFloat(tags[index][0] as int) : defaultValue
    } catch (NumberFormatException) {
        return defaultValue
    }
}

@CompileStatic
private static Integer getIntTag(Map<Integer, List> tags, int index, int defaultValue = 0) {
    try {
        return tags && tags[index] ? tags[index][0] as int : defaultValue
    } catch (NumberFormatException) {
        return defaultValue
    }
}

@CompileStatic
private static List<Integer> getIntTagList(Map<Integer, List> tags, int index) {
    /* groovylint-disable-next-line ExplicitArrayListInstantiation */
    return tags && tags[index] ? tags[index] as List<Integer> : new ArrayList<Integer>()
}

@CompileStatic
private static Long getLongTag(Map<Integer, List> tags, int index, long defaultValue = 0) {
    try {
        return tags && tags[index] ? tags[index][0] as long : defaultValue
    } catch (NumberFormatException) {
        return defaultValue
    }
}

@CompileStatic
private static String getStringTag(Map<Integer, List> tags, int index, String defaultValue = '') {
    return tags && tags[index] ? new String(tags[index][0] as byte[], 'UTF-8') : defaultValue
}

@CompileStatic
private static List<String> getStringTagList(Map<Integer, List> tags, int index) {
    /* groovylint-disable-next-line ExplicitArrayListInstantiation */
    return tags && tags[index] ? tags[index].collect { s -> new String(s as byte[], 'UTF-8') } : new ArrayList<String>()
}

@CompileStatic
private static int getVarIntSize(long i) {
    if (i < 0) {
        return VARINT_MAX_BYTES
    }
    int size = 1
    while (i >= 128) {
        size++
        i >>= 7
    }
    return size
}

@CompileStatic
private static long readVarInt(ByteArrayInputStream stream, boolean permitEOF) {
    long result = 0
    int shift = 0
    // max 10 byte wire format for 64 bit integer (7 bit data per byte)
    for (int i = 0; i < VARINT_MAX_BYTES; i++) {
        int b = stream.read()
        if (b == -1) {
            return (i == 0 && permitEOF) ? -1 : 0
        }
        result |= ((long) (b & 0x07f)) << shift
        boolean flag = (b & 0x80) == 0
        if (flag) {
            break // get out early
        }
        shift += 7
    }
    return result
}

@CompileStatic
private static int writeVarInt(ByteArrayOutputStream stream, long value) {
    int count = 0
    for (int i = 0; i < VARINT_MAX_BYTES; i++) {
        int toWrite = (int) (value & 0x7f)
        value >>>= 7
        count++
        if (value == 0) {
            stream.write(toWrite)
            break
        } else {
            stream.write(toWrite | 0x080)
        }
    }
    return count
}

// @CompileStatic
// private static long zigZagDecode(long v) {
//     return (v >>> 1) ^ -(v & 1)
// }

// @CompileStatic
// private static long zigZagEncode(long v) {
//     return ((v << 1) ^ -(v >>> 63))
// }

@CompileStatic
private static Map espHomeBinarySensorState(Map<Integer, List> tags, boolean isDigital) {
    return [
            type: 'state',
            platform: 'binary',
            isDigital: isDigital,
            key: getLongTag(tags, 1),
            state: getBooleanTag(tags, 2),
            hasState: getBooleanTag(tags, 3, true)
    ]
}

@CompileStatic
private static Map espHomeBluetoothLeResponse(Map<Integer, List> tags) {
    Map message = [
            type: 'state',
            platform: 'bluetoothle',
            isDigital: true,
            address: formatMacAddress(getLongTag(tags, 1)),
            name: getStringTag(tags, 2),
            rssi: getIntTag(tags, 3),
            services: [],
            serviceData: [:],
            manufacturerData: [:]
    ]

    if (tags[4]) { // services
        List<String> services = getStringTagList(tags, 4)
        message.services = services*.toLowerCase()
    }

    if (tags[5]) { // service data
        Map<String, List> payload = [:]
        for (int i = 0; i < tags[5].size(); i++) {
            byte[] buffer = tags[5][i]
            Map<Integer, List> subtags = protobufDecode(new ByteArrayInputStream(buffer), buffer.size())
            String uuid = getStringTag(subtags, 1).toLowerCase()
            List data = getIntTagList(subtags, 2)
            payload[uuid] = data
        }
        message.serviceData = payload
    }

    if (tags[6]) { // manufacturer data
        Map<String, List> payload = [:]
        for (int i = 0; i < tags[6].size(); i++) {
            byte[] buffer = tags[6][i]
            Map<Integer, List> subtags = protobufDecode(new ByteArrayInputStream(buffer), buffer.size())
            String uuid = getStringTag(subtags, 1).toLowerCase()
            List data = getIntTagList(subtags, 2)
            payload[uuid] = data
        }
        message.manufacturerData = payload
    }

    return message
}

@CompileStatic
private static Map espHomeCameraImageResponse(Map<Integer, List> tags) {
    return [
            type: 'state',
            platform: 'camera',
            key: getLongTag(tags, 1),
            image: tags[2][0],
            done: getBooleanTag(tags, 3)
    ]
}

@CompileStatic
private static Map espHomeCoverState(Map<Integer, List> tags, boolean isDigital) {
    return [
            type: 'state',
            platform: 'cover',
            isDigital: isDigital,
            key: getLongTag(tags, 1),
            legacyState: getIntTag(tags, 2), // legacy: state has been removed in 1.13
            position: getFloatTag(tags, 3),
            tilt: getFloatTag(tags, 4),
            currentOperation: getIntTag(tags, 5)
    ]
}

@CompileStatic
private static Map espHomeFanState(Map<Integer, List> tags, boolean isDigital) {
    return [
            type: 'state',
            platform: 'fan',
            isDigital: isDigital,
            key: getLongTag(tags, 1),
            state: getBooleanTag(tags, 2),
            oscillating: getBooleanTag(tags, 3),
            speed: getIntTag(tags, 4), // deprecated
            direction: getIntTag(tags, 5),
            speedLevel: getIntTag(tags, 6)
    ]
}

@CompileStatic
private static Map espHomeHaServiceResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'service',
            service: getStringTag(tags, 1),
            data: getStringTagList(tags, 2), // FIXME
            data_template: getStringTagList(tags, 3), // FIXME
            variables: getStringTagList(tags, 4), // FIXME
            isEvent: getBooleanTag(tags, 5)
    ]
}

@CompileStatic
private static Map espHomeLightState(Map<Integer, List> tags, boolean isDigital) {
    return [
            type: 'state',
            platform: 'light',
            isDigital: isDigital,
            key: getLongTag(tags, 1),
            state: getBooleanTag(tags, 2),
            masterBrightness: getFloatTag(tags, 3),
            colorMode: getIntTag(tags, 11),
            colorModeCapabilities: toCapabilities(getIntTag(tags, 11)),
            colorBrightness: getFloatTag(tags, 10),
            red: getFloatTag(tags, 4),
            green: getFloatTag(tags, 5),
            blue: getFloatTag(tags, 6),
            white: getFloatTag(tags, 7),
            colorTemperature: getFloatTag(tags, 8),
            coldWhite: getFloatTag(tags, 12),
            warmWhite: getFloatTag(tags, 13),
            effect: getStringTag(tags, 9)
    ]
}

@CompileStatic
private static Map espHomeListEntitiesBinarySensorResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'binary',
            deviceClass: getStringTag(tags, 5),
            isStatusBinarySensor: getBooleanTag(tags, 6),
            disabledByDefault: getBooleanTag(tags, 7),
            icon: getStringTag(tags, 8),
            entityCategory: toEntityCategory(getIntTag(tags, 9))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesButtonResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'button',
            icon: getStringTag(tags, 5),
            disabledByDefault: getBooleanTag(tags, 6),
            entityCategory: toEntityCategory(getIntTag(tags, 7)),
            deviceClass: getStringTag(tags, 8)
    ]
}

@CompileStatic
private static Map espHomeListEntitiesCameraResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'camera',
            disabledByDefault: getBooleanTag(tags, 5),
            icon: getStringTag(tags, 6),
            entityCategory: toEntityCategory(getIntTag(tags, 7))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesCoverResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'cover',
            assumedState: getBooleanTag(tags, 5),
            supportsPosition: getBooleanTag(tags, 6),
            supportsTilt: getBooleanTag(tags, 7),
            deviceClass: getStringTag(tags, 8),
            disabledByDefault: getBooleanTag(tags, 9),
            icon: getStringTag(tags, 10),
            entityCategory: toEntityCategory(getIntTag(tags, 11))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesLockResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'lock',
            icon: getStringTag(tags, 5),
            disabledByDefault: getBooleanTag(tags, 6),
            entityCategory: toEntityCategory(getIntTag(tags, 7)),
            assumedState: getBooleanTag(tags, 8),
            supportsOpen: getBooleanTag(tags, 9),
            requiresCode: getBooleanTag(tags, 10),
            codeFormat: getStringTag(tags, 11)
    ]
}

@CompileStatic
private static Map espHomeListEntitiesFanResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'fan',
            supportsOscillation: getBooleanTag(tags, 5),
            supportsSpeed: getBooleanTag(tags, 6),
            supportsDirection: getBooleanTag(tags, 7),
            supportedSpeedLevels: getIntTag(tags, 8),
            disabledByDefault: getBooleanTag(tags, 9),
            icon: getStringTag(tags, 10),
            entityCategory: toEntityCategory(getIntTag(tags, 11))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesLightResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'light',
            minMireds: getFloatTag(tags, 9),
            maxMireds: getFloatTag(tags, 10),
            effects: getStringTagList(tags, 11),
            supportedColorModes: getIntTagList(tags, 12).collectEntries { e -> [ e, toCapabilities(e) ] },
            disabledByDefault: getBooleanTag(tags, 13),
            icon: getStringTag(tags, 14),
            entityCategory: toEntityCategory(getIntTag(tags, 15))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesMediaPlayerResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'media_player',
            icon: getStringTag(tags, 5),
            disabledByDefault: getBooleanTag(tags, 6),
            entityCategory: toEntityCategory(getIntTag(tags, 7))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesNumberResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'number',
            icon: getStringTag(tags, 5),
            minValue: getFloatTag(tags, 6),
            maxValue: getFloatTag(tags, 7),
            step: getFloatTag(tags, 8),
            disabledByDefault: getBooleanTag(tags, 9),
            entityCategory: toEntityCategory(getIntTag(tags, 10)),
            unitOfMeasurement: getStringTag(tags, 11),
            numberMode: getIntTag(tags, 12)
    ]
}

@CompileStatic
private static Map espHomeListEntitiesSensorResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'sensor',
            icon: getStringTag(tags, 5),
            unitOfMeasurement: getStringTag(tags, 6),
            accuracyDecimals: getIntTag(tags, 7),
            forceUpdate: getBooleanTag(tags, 8),
            deviceClass: getStringTag(tags, 9),
            sensorStateClass: getIntTag(tags, 10),
            lastResetType: getIntTag(tags, 11),
            disabledByDefault: getBooleanTag(tags, 12),
            entityCategory: toEntityCategory(getIntTag(tags, 13))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesSelectResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'select',
            icon: getStringTag(tags, 5),
            options: getStringTagList(tags, 6),
            disabledByDefault: getBooleanTag(tags, 7),
            entityCategory: toEntityCategory(getIntTag(tags, 8))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesSirenResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'siren',
            icon: getStringTag(tags, 5),
            disabledByDefault: getBooleanTag(tags, 6),
            tones: getStringTagList(tags, 7),
            supportsDuration: getBooleanTag(tags, 8),
            supportsVolume: getBooleanTag(tags, 9),
            entityCategory: toEntityCategory(getIntTag(tags, 10))
    ]
}

@CompileStatic
private static Map espHomeListEntitiesSwitchResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'switch',
            icon: getStringTag(tags, 5),
            assumedState: getBooleanTag(tags, 6),
            disabledByDefault: getBooleanTag(tags, 7),
            entityCategory: toEntityCategory(getIntTag(tags, 8)),
            deviceClass: getStringTag(tags, 9)
    ]
}

@CompileStatic
private static Map espHomeListEntitiesTextSensorResponse(Map<Integer, List> tags) {
    return parseEntity(tags) + [
            type: 'entity',
            platform: 'text',
            icon: getStringTag(tags, 5),
            disabledByDefault: getBooleanTag(tags, 6),
            entityCategory: toEntityCategory(getIntTag(tags, 7))
    ]
}

@CompileStatic
private static Map espHomeLockState(Map<Integer, List> tags) {
    return [
            type: 'state',
            platform: 'lock',
            key: getLongTag(tags, 1),
            state: getIntTag(tags, 2),
    ]
}

@CompileStatic
private static Map espHomeSelectState(Map<Integer, List> tags) {
    return [
            type: 'state',
            platform: 'select',
            key: getLongTag(tags, 1),
            state: getStringTag(tags, 2),
            hasState: getBooleanTag(tags, 3, true)
    ]
}

@CompileStatic
private static Map espHomeMediaPlayerState(Map<Integer, List> tags) {
    return [
            type: 'state',
            platform: 'media_player',
            key: getLongTag(tags, 1),
            state: getIntTag(tags, 2),
            volume: getFloatTag(tags, 3),
            muted: getBooleanTag(tags, 4)
    ]
}

@CompileStatic
private static Map espHomeNumberState(Map<Integer, List> tags) {
    return [
            type: 'state',
            platform: 'number',
            key: getLongTag(tags, 1),
            state: getFloatTag(tags, 2),
            hasState: getBooleanTag(tags, 3, true)
    ]
}

@CompileStatic
private static Map espHomeSensorState(Map<Integer, List> tags) {
    return [
            type: 'state',
            platform: 'sensor',
            key: getLongTag(tags, 1),
            state: getFloatTag(tags, 2),
            hasState: getBooleanTag(tags, 3, true)
    ]
}

@CompileStatic
private static Map espHomeSirenState(Map<Integer, List> tags) {
    return [
            type: 'state',
            platform: 'siren',
            key: getLongTag(tags, 1),
            state: getIntTag(tags, 2)
    ]
}

@CompileStatic
private static Map espHomeSwitchState(Map<Integer, List> tags, boolean isDigital) {
    return [
            type: 'state',
            platform: 'switch',
            isDigital: isDigital,
            key: getLongTag(tags, 1),
            state: getBooleanTag(tags, 2)
    ]
}

@CompileStatic
private static Map espHomeTextSensorState(Map<Integer, List> tags) {
    return [
            type: 'state',
            platform: 'text',
            key: getLongTag(tags, 1),
            state: getStringTag(tags, 2),
            hasState: getBooleanTag(tags, 3, true)
    ]
}

@CompileStatic
private static boolean hasCapability(int capabilities, int capability) {
    return capabilities & capability
}

@CompileStatic
private static Map parseEntity(Map<Integer, List> tags) {
    return [
            objectId: getStringTag(tags, 1),
            key: getLongTag(tags, 2),
            name: getStringTag(tags, 3),
            uniqueId: getStringTag(tags, 4)
    ]
}

/**
 * Minimal Protobuf Implementation for use with ESPHome
 */
@CompileStatic
private static Map<Integer, List> protobufDecode(ByteArrayInputStream stream, long available) {
    Map<Integer, List> tags = [:]
    while (available > 0) {
        long tagAndType = readVarInt(stream, true)
        if (tagAndType == -1) {
            throw new IOException('ESPHome unexpected EOF decoding protobuf message')
        }
        available -= getVarIntSize(tagAndType)
        int wireType = ((int) tagAndType) & 0x07
        Integer tag = (int) (tagAndType >>> 3)
        switch (wireType) {
            case WIRETYPE_VARINT:
                Long val = readVarInt(stream, false)
                available -= getVarIntSize(val)
                tags.computeIfAbsent(tag) { k -> [] }.add(val)
                break
            case WIRETYPE_FIXED32:
            case WIRETYPE_FIXED64:
                Long val = 0
                int shift = 0
                int count = (wireType == WIRETYPE_FIXED32) ? 4 : 8
                available -= count
                while (count-- > 0) {
                    long l = stream.read()
                    val |= l << shift
                    shift += 8
                }
                tags.computeIfAbsent(tag) { k -> [] }.add(val)
                break
            case WIRETYPE_LENGTH_DELIMITED:
                int total = (int) readVarInt(stream, false)
                available -= getVarIntSize(total)
                available -= total
                byte[] val = new byte[total]
                int pos = 0
                while (pos < total) {
                    int count = stream.read(val, pos, total - pos)
                    if (count < (total - pos)) {
                        throw new IOException('ESPHome unexpected EOF decoding protobuf message')
                    }
                    pos += count
                }
                tags.computeIfAbsent(tag) { k -> [] }.add(val)
                break
        }
    }
    return tags
}

@CompileStatic
private static int protobufEncode(ByteArrayOutputStream stream, Map<Integer, List> tags) {
    int bytes = 0
    for (entry in tags.findAll { k, v -> v && v[0] }.sort()) {
        int fieldNumber = entry.key as int
        int wireType = entry.value[1] as int
        switch (entry.value[0]) {
            case Float:
                entry.value[0] = Float.floatToRawIntBits(entry.value[0] as Float)
                break
            case Double:
                entry.value[0] = Double.doubleToRawLongBits(entry.value[0] as Double)
                break
        }
        int tag = (fieldNumber << 3) | wireType
        bytes += writeVarInt(stream, tag)
        switch (wireType) {
            case WIRETYPE_VARINT:
                long v = entry.value[0] as long
                bytes += writeVarInt(stream, v)
                break
            case WIRETYPE_LENGTH_DELIMITED:
                byte[] v = entry.value[0] as byte[]
                bytes += writeVarInt(stream, v.size())
                stream.write(v)
                bytes += v.size()
                break
            case WIRETYPE_FIXED32:
                int v = entry.value[0] as int
                /* groovylint-disable-next-line NestedForLoop */
                for (int b = 0; b < 4; b++) {
                    stream.write((int) (v & 0x0ff))
                    bytes++
                    v >>= 8
                }
                break
            case WIRETYPE_FIXED64:
                long v = entry.value[0] as long
                /* groovylint-disable-next-line NestedForLoop */
                for (int b = 0; b < 8; b++) {
                    stream.write((int) (v & 0x0ff))
                    bytes++
                    v >>= 8
                }
                break
        }
    }
    return bytes
}

@CompileStatic
private static String formatMacAddress(Long macAddress) {
    String value = String.format('%012x', macAddress)
    char[] chars = value.toCharArray()
    final StringBuilder stringBuilder = new StringBuilder()
    stringBuilder.append(chars[0]).append(chars[1])
    for (int pos = 2; pos < value.length(); pos += 2) {
        stringBuilder.append(':').append(chars[pos]).append(chars[pos + 1])
    }
    return stringBuilder.toString()
}

@CompileStatic
private static String toEntityCategory(int value) {
    switch (value) {
        case ENTITY_CATEGORY_NONE: return 'none'
        case ENTITY_CATEGORY_CONFIG: return 'config'
        case ENTITY_CATEGORY_DIAGNOSTIC: return 'diagnostic'
        default: return value
    }
}

@CompileStatic
private static List<String> toCapabilities(int capability) {
    List<String> capabilities = []
    if (hasCapability(capability, COLOR_CAP_ON_OFF)) { capabilities.add('ON/OFF') }
    if (hasCapability(capability, COLOR_CAP_BRIGHTNESS)) { capabilities.add('BRIGHTNESS') }
    if (hasCapability(capability, COLOR_CAP_RGB)) { capabilities.add('RGB') }
    if (hasCapability(capability, COLOR_CAP_WHITE)) { capabilities.add('WHITE') }
    if (hasCapability(capability, COLOR_CAP_COLD_WARM_WHITE)) { capabilities.add('COLD WARM WHITE') }
    if (hasCapability(capability, COLOR_CAP_COLOR_TEMPERATURE)) { capabilities.add('COLOR TEMPERATURE') }
    return capabilities
}

/* groovylint-disable-next-line MethodSize */
@CompileStatic
private void parseMessage(ByteArrayInputStream stream, long length) {
    int msgType = (int) readVarInt(stream, true)
    if (msgType < 1) {
        logWarnLib "ESPHome message type ${msgType} out of range, skipping"
        return
    }

    Map<Integer, List> tags = protobufDecode(stream, length)
    boolean handled = supervisionCheck(msgType, tags)

    switch (msgType) {
        case MSG_DISCONNECT_REQUEST:
            espHomeDisconnectRequest()
            break
        case MSG_PING_REQUEST:
            sendMessage(MSG_PING_RESPONSE)
            break
        case MSG_LIST_BINARYSENSOR_RESPONSE:
            parse espHomeListEntitiesBinarySensorResponse(tags)
            break
        case MSG_LIST_COVER_RESPONSE:
            parse espHomeListEntitiesCoverResponse(tags)
            break
        case MSG_LIST_FAN_RESPONSE:
            parse espHomeListEntitiesFanResponse(tags)
            break
        case MSG_LIST_LIGHT_RESPONSE:
            parse espHomeListEntitiesLightResponse(tags)
            break
        case MSG_LIST_SENSOR_RESPONSE:
            parse espHomeListEntitiesSensorResponse(tags)
            break
        case MSG_LIST_SWITCH_RESPONSE:
            parse espHomeListEntitiesSwitchResponse(tags)
            break
        case MSG_LIST_TEXT_SENSOR_RESPONSE:
            parse espHomeListEntitiesTextSensorResponse(tags)
            break
        case MSG_LIST_ENTITIES_RESPONSE:
            parse espHomeListEntitiesDoneResponse()
            break
        case MSG_BINARY_SENSOR_STATE_RESPONSE:
            parse espHomeBinarySensorState(tags, handled)
            break
        case MSG_COVER_STATE_RESPONSE:
            parse espHomeCoverState(tags, handled)
            break
        case MSG_FAN_STATE_RESPONSE:
            parse espHomeFanState(tags, handled)
            break
        case MSG_HA_SERVICE_RESPONSE:
            parse espHomeHaServiceResponse(tags)
            break
        case MSG_LIGHT_STATE_RESPONSE:
            parse espHomeLightState(tags, handled)
            break
        case MSG_SENSOR_STATE_RESPONSE:
            parse espHomeSensorState(tags)
            break
        case MSG_SWITCH_STATE_RESPONSE:
            parse espHomeSwitchState(tags, handled)
            break
        case MSG_TEXT_SENSOR_STATE_RESPONSE:
            parse espHomeTextSensorState(tags)
            break
        case MSG_SUBSCRIBE_LOGS_RESPONSE:
            espHomeSubscribeLogsResponse(tags)
            break
        case MSG_GET_TIME_REQUEST:
            espHomeGetTimeRequest()
            break
        case MSG_LIST_SERVICES_RESPONSE:
            espHomeListEntitiesServicesResponse(tags)
            break
        case MSG_LIST_NUMBER_RESPONSE:
            parse espHomeListEntitiesNumberResponse(tags)
            break
        case MSG_LIST_CAMERA_RESPONSE:
            parse espHomeListEntitiesCameraResponse(tags)
            break
        case MSG_CAMERA_IMAGE_RESPONSE:
            parse espHomeCameraImageResponse(tags)
            break
        case MSG_NUMBER_STATE_RESPONSE:
            parse espHomeNumberState(tags)
            break
        case MSG_LIST_SELECT_RESPONSE:
            parse espHomeListEntitiesSelectResponse(tags)
            break
        case MSG_LIST_SIREN_RESPONSE:
            parse espHomeListEntitiesSirenResponse(tags)
            break
        case MSG_SIREN_STATE_RESPONSE:
            parse espHomeSirenState(tags)
            break
        case MSG_LIST_LOCK_RESPONSE:
            parse espHomeListEntitiesLockResponse(tags)
            break
        case MSG_LOCK_STATE_RESPONSE:
            parse espHomeLockState(tags)
            break
        case MSG_LIST_BUTTON_RESPONSE:
            parse espHomeListEntitiesButtonResponse(tags)
            break
        case MSG_LIST_MEDIA_RESPONSE:
            parse espHomeListEntitiesMediaPlayerResponse(tags)
            break
        case MSG_MEDIA_STATE_RESPONSE:
            parse espHomeMediaPlayerState(tags)
            break
        case MSG_BLUETOOTH_LE_RESPONSE:
            parse espHomeBluetoothLeResponse(tags)
            break
        case MSG_SELECT_STATE_RESPONSE:
            parse espHomeSelectState(tags)
            break
        default:
            if (!handled) {
                logWarnLib "ESPHome received unhandled message type ${msgType} with ${tags}"
            }
    }

    espHomeSchedulePing()
}

private void espHomeConnectRequest(String password = null) {
    // Message sent after the hello response to authenticate the client
    // Can only be sent by the client and only at the beginning of the connection
    logInfoLib "ESPHome sending connect request (${password ? 'using' : 'no'} password)"
    sendMessage(MSG_CONNECT_REQUEST, [
            1: [ password as String, WIRETYPE_LENGTH_DELIMITED ]
    ], MSG_CONNECT_RESPONSE, 'espHomeConnectResponse')
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void espHomeConnectResponse(Map<Integer, List> tags) {
    Boolean invalidPassword = getBooleanTag(tags, 1)
    if (invalidPassword) {
        logErrorLib 'ESPHome invalid password (update configuration setting)'
        closeSocket('invalid password')
        return
    }

    setNetworkStatus('online', 'connection completed')
    device.updateDataValue 'Last Connected Time', new Date().toString()
    state.remove('reconnectDelay')
    espHomeSchedulePing()
    espHomeDeviceInfoRequest()
}

@CompileStatic
private void espHomeDeviceInfoRequest() {
    sendMessage(
            MSG_DEVICEINFO_REQUEST, [:],
            MSG_DEVICEINFO_RESPONSE, 'espHomeDeviceInfoResponse'
    )
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void espHomeDeviceInfoResponse(Map<Integer, List> tags) {
    Map deviceInfo = [
            type: 'device',
            name: getStringTag(tags, 2),
            macAddress: getStringTag(tags, 3),
            espHomeVersion: getStringTag(tags, 4),
            compileTime: getStringTag(tags, 5),
            boardModel:  getStringTag(tags, 6),
            hasDeepSleep: getBooleanTag(tags, 7),
            projectName: getStringTag(tags, 8),
            projectVersion: getStringTag(tags, 9),
            portNumber: getIntTag(tags, 10),
            webServer: "http://${settings.ipAddress}:${getIntTag(tags, 10)}",
            btProxyVersion: getIntTag(tags, 11),
            manufacturer: getStringTag(tags, 12)
    ]

    boolean requireRefresh = (device.getDataValue('Compile Time') != deviceInfo.compileTime) ||
        (device.getDataValue('MAC Address') != deviceInfo.macAddress)

    device.with {
        updateDataValue 'Board Model', deviceInfo.boardModel
        updateDataValue 'Board Model', deviceInfo.boardModel
        updateDataValue 'Compile Time', deviceInfo.compileTime
        updateDataValue 'ESPHome Version', deviceInfo.espHomeVersion
        updateDataValue 'Has Deep Sleep', deviceInfo.hasDeepSleep ? 'yes' : 'no'
        updateDataValue 'MAC Address', deviceInfo.macAddress
        updateDataValue 'Project Name', deviceInfo.projectName
        updateDataValue 'Project Version', deviceInfo.projectVersion
        updateDataValue 'Web Server', deviceInfo.webServer
        updateDataValue 'Bluetooth Proxy Version', deviceInfo.btProxyVersion as String
        updateDataValue 'Manufacturer', deviceInfo.manufacturer
    }

    if (deviceInfo.macAddress) {
        device.deviceNetworkId = deviceInfo.macAddress.replaceAll(':', '').toUpperCase()
    }

    parse(deviceInfo)

    if (requireRefresh || state.requireRefresh) {
        espHomeListEntitiesRequest()
        state.remove('requireRefresh')
    } else {
        espHomeSubscribe()
    }
}

private void espHomeGetTimeRequest() {
    long value = new Date().getTime().intdiv(1000)
    logInfoLib 'ESPHome sending device current time'
    sendMessage(MSG_GET_TIME_RESPONSE, [
            1: [ value as Long, WIRETYPE_VARINT ]
    ])
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void espHomeHelloRequest() {
    // Can only be sent by the client and only at the beginning of the connection
    String client = "Hubitat ${location.hub.name}"
    logInfoLib 'ESPHome requesting API version'
    sendMessage(MSG_HELLO_REQUEST, [
            1: [ client as String, WIRETYPE_LENGTH_DELIMITED ]
    ], MSG_HELLO_RESPONSE, 'espHomeHelloResponse')
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void espHomeHelloResponse(Map<Integer, List> tags) {
    // Confirmation of successful connection request.
    // Can only be sent by the server and only at the beginning of the connection
    String version = getIntTag(tags, 1) + '.' + getIntTag(tags, 2)
    logInfoLib "ESPHome API version: ${version}"
    device.updateDataValue 'API Version', version
    if (getIntTag(tags, 1) > 1) {
        logErrorLib 'ESPHome API version > 1 not supported - disconnecting'
        closeSocket('API version not supported')
        return
    }

    String info = getStringTag(tags, 3)
    if (info) {
        logInfoLib "ESPHome server info: ${info}"
        device.updateDataValue 'Server Info', info
    }

    String name = getStringTag(tags, 4)
    if (name) {
        logInfoLib "ESPHome device name: ${name}"
        if (device.getDataValue('Device Name') != name) {
            device.updateDataValue 'Device Name', name
            device.name = name
            state.requireRefresh = true
        }
    }

    // Step 2: Send the ConnectRequest message
    espHomeConnectRequest(settings.password as String)
}

private void espHomeListEntitiesRequest() {
    if (logEnable) { logTraceLib 'ESPHome requesting entities list' }
    sendMessage(MSG_LIST_ENTITIES_REQUEST)
}

@CompileStatic
private Map espHomeListEntitiesDoneResponse() {
    espHomeSubscribe()
    return [
        type: 'complete'
    ]
}

@CompileStatic
private void espHomePingRequest() {
    sendMessage(
            MSG_PING_REQUEST, [:],
            MSG_PING_RESPONSE, 'espHomePingResponse'
    )
}

/* groovylint-disable-next-line UnusedPrivateMethod, UnusedPrivateMethodParameter */
private void espHomePingResponse(Map<Integer, List> tags) {
    setNetworkStatus('online', 'ping response')
    if (logEnable) { logTraceLib 'ESPHome ping response received from device' }
    espHomeSchedulePing()
}

private void espHomeSchedulePing() {
    if (PING_INTERVAL_SECONDS > 0) {
        int jitter = (int) Math.ceil(PING_INTERVAL_SECONDS * 0.5)
        int interval = PING_INTERVAL_SECONDS + random.nextInt(jitter)
        runIn(interval, 'healthCheck')
    }
}

@CompileStatic
private void espHomeSubscribeLogs(Integer logLevel, boolean dumpConfig = true) {
    sendMessage(MSG_SUBSCRIBE_LOGS_REQUEST, [
            1: [ logLevel as Integer, WIRETYPE_VARINT ],
            2: [ dumpConfig ? 1 : 0, WIRETYPE_VARINT ]
    ])
}

private void espHomeSubscribeLogsResponse(Map<Integer, List> tags) {
    String message = getStringTag(tags, 3).replaceAll(/\x1b\[[0-9;]*m/, '')
    switch (getIntTag(tags, 1)) {
        case LOG_LEVEL_ERROR:
            logErrorLib message
            break
        case LOG_LEVEL_WARN:
            logWarnLib message
            break
        case LOG_LEVEL_INFO:
            logInfoLib message
            break
        case LOG_LEVEL_VERY_VERBOSE:
            logTraceLib message
            break
        default:
            logDebugLib message
            break
    }
}

@CompileStatic
private void espHomeSubscribeStatesRequest() {
    sendMessage(MSG_SUBSCRIBE_STATES_REQUEST)
}

@CompileStatic
private void espHomeSubscribeHaServicesRequest() {
    sendMessage(MSG_SUBSCRIBE_HA_SERVICES_REQUEST)
}

private void espHomeListEntitiesServicesResponse(Map<Integer, List> tags) {
    Map service = [
        objectId: getStringTag(tags, 1),
        key: getLongTag(tags, 2),
        args: getStringTagList(tags, 3)
    ]
    if (settings.logEnable) { logTraceLib "ESPHome Service discovered: ${service}" }
    state.services = (state.services ?: []) << service
}

@CompileStatic
private void espHomeExecuteServiceRequest(Map<String, Object> tags) {
    sendMessage(MSG_EXECUTE_SERVICE_REQUEST, [
        1: [ tags.key as Integer, WIRETYPE_FIXED32 ]
    ])
}

@CompileStatic
private String encodeMessage(int type, Map<Integer, List> tags = [:]) {
    // creates hex string payload from message type and tags
    ByteArrayOutputStream payload = new ByteArrayOutputStream()
    int length = tags ? protobufEncode(payload, tags) : 0
    ByteArrayOutputStream stream = new ByteArrayOutputStream()
    stream.write(0x00)
    writeVarInt(stream, length)
    writeVarInt(stream, type)
    payload.writeTo(stream)
    return HexUtils.byteArrayToHexString(stream.toByteArray())
}

private Collection<Map> getSendQueue() {
    return espSendQueue.computeIfAbsent(device.id) { k -> new ConcurrentLinkedQueue<Map>() }
}

private ByteArrayOutputStream getReceiveBuffer() {
    return espReceiveBuffer.computeIfAbsent(device.id) { k -> new ByteArrayOutputStream() }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void healthCheck() {
    if (device.isDisabled()) {
        state.reconnectDelay = MAX_RECONNECT_SECONDS
        closeSocket('device is disabled')
        scheduleConnect()
        return
    }

    // send ping request when online and send queue is empty
    if (!isOffline() && getSendQueue().isEmpty()) {
        if (logEnable) { logTraceLib 'ESPHome sending ping to device' }
        espHomePingRequest()
    }
}

@CompileStatic
private ByteArrayInputStream hexDecode(String hexString) {
    // We need to be able to retrieve partial packets previously stashed
    // This would be faster if we had access to System.Arraycopy but we don't
    ByteArrayOutputStream buffer = getReceiveBuffer()
    byte[] payload = HexUtils.hexStringToByteArray(hexString)
    buffer.write(payload, 0, payload.size())
    ByteArrayInputStream result = new ByteArrayInputStream(buffer.toByteArray())
    buffer.reset()
    return result
}

private boolean isOffline() {
    return device.currentValue(NETWORK_ATTRIBUTE) == 'offline'
}

private void scheduleConnect() {
    int reconnectDelay = (state.reconnectDelay ?: 1)
    if (reconnectDelay > MAX_RECONNECT_SECONDS) { reconnectDelay = MAX_RECONNECT_SECONDS }
    int jitter = (int) Math.ceil(reconnectDelay * 0.25)
    reconnectDelay += random.nextInt(jitter)
    logInfoLib "ESPHome reconnecting in ${reconnectDelay} seconds"
    state.reconnectDelay = reconnectDelay * 2
    runIn(reconnectDelay, 'openSocket')
}

private void sendMessage(int msgType, Map<Integer, List> tags = [:]) {
    if (logEnable) { logDebugLib "ESPHome send msg type #${msgType} with ${tags}" }
    try {
        interfaces.rawSocket.sendMessage(encodeMessage(msgType, tags))
    } catch (IOException e) {
        logErrorLib "sendMessage: ${e}"
    }
}

private void sendMessage(int msgType, Map<Integer, List> tags, int expectedMsgType, String onSuccess = '') {
    Collection<Map> queue = getSendQueue()
    queue.removeIf { e -> e.msgType == msgType } // remove any duplicate commands
    queue.add([
            msgType: msgType,
            tags: tags,
            expectedMsgType: expectedMsgType,
            onSuccess: onSuccess,
            retries: SEND_RETRY_COUNT
    ])
    if (!isOffline()) {
        runIn(SEND_RETRY_SECONDS, 'sendMessageQueue')
        sendMessage(msgType, tags)
    }
}

private void sendMessageQueue() {
    if (!isOffline()) {
        Collection<Map> queue = getSendQueue()
        // send outstanding messages and decrement retry counter
        queue.removeIf { entry ->
            if (entry.retries > 0) {
                entry.retries--
                logInfoLib "ESPHome sending message type #${entry.msgType} (${entry.retries} retries left)"
                sendMessage(entry.msgType, entry.tags)
                return false
            }
            logInfoLib "ESPHome message type #${entry.msgType} retry count exceeded"
            // maybe a broken connection
            closeSocket('message retry count exceeded')
            scheduleConnect()
            return true
        }

        // reschedule if there are outstanding messages
        if (!queue.isEmpty()) {
            runIn(SEND_RETRY_SECONDS, 'sendMessageQueue')
        }
    }
}

private void setNetworkStatus(String state, String reason = '') {
    String descriptionText = "${device} is ${state}"
    if (reason) { descriptionText += ": ${reason}" }
    sendEvent([ name: NETWORK_ATTRIBUTE, value: state, descriptionText: descriptionText ])
    log.info descriptionText
    parse([ 'platform': 'network', 'type': 'state', 'state': state, 'reason': reason ])
}

@CompileStatic
private void stashBuffer(ByteArrayInputStream stream) {
    // We need to be able to stash partial packets to process later
    ByteArrayOutputStream buffer = getReceiveBuffer()
    byte[] payload = new byte[stream.available()]
    stream.read(payload, 0, payload.size())
    buffer.write(0x00) // start delimiter
    buffer.write(payload, 0, payload.size())
}

private boolean supervisionCheck(int msgType, Map<Integer, List> tags) {
    List<String> onSuccess = []
    Collection<Map> queue = getSendQueue()

    // check for successful responses and remove from queue
    boolean result = queue.removeIf { entry ->
        if (entry.expectedMsgType == msgType) {
            if (entry.onSuccess) {
                onSuccess.add(entry.onSuccess)
            }
            return true
        }
        return false
    }

    // execute all onsuccess after queue has been processed
    if (queue.isEmpty()) {
        unschedule('sendMessageQueue')
    }

    onSuccess.each { e ->
        if (logEnable) { logTraceLib "ESPHome executing ${e}" }
        "${e}"(tags)
    }
    return result
}

private void logWarnLib(String s) {
    if (logWarnEnable) {
        log.warn s
    }
}

private void logInfoLib(String s) {
    if (logWarnEnable) {
        log.info s
    }
}

private void logTraceLib(String s) {
    if (logTraceEnable) {
        log.trace s
    }
}

private void logErrorLib(String s) {
    if (logDebugEnable) {
        log.error s
    }
}

private void logDebugLib(String s) {
    if (logDebugEnable) {
        log.debug s
    }
}

/**
 * ESPHome Protobuf Enumerations
 * https://github.com/esphome/aioesphomeapi/blob/main/aioesphomeapi/api.proto
 */
@Field static final int MSG_HELLO_REQUEST = 1
@Field static final int MSG_HELLO_RESPONSE = 2
@Field static final int MSG_CONNECT_REQUEST = 3
@Field static final int MSG_CONNECT_RESPONSE = 4
@Field static final int MSG_DISCONNECT_REQUEST = 5
@Field static final int MSG_DISCONNECT_RESPONSE = 6
@Field static final int MSG_PING_REQUEST = 7
@Field static final int MSG_PING_RESPONSE = 8
@Field static final int MSG_DEVICEINFO_REQUEST = 9
@Field static final int MSG_DEVICEINFO_RESPONSE = 10
@Field static final int MSG_LIST_ENTITIES_REQUEST = 11
@Field static final int MSG_LIST_BINARYSENSOR_RESPONSE = 12
@Field static final int MSG_LIST_COVER_RESPONSE = 13
@Field static final int MSG_LIST_FAN_RESPONSE = 14
@Field static final int MSG_LIST_LIGHT_RESPONSE = 15
@Field static final int MSG_LIST_SENSOR_RESPONSE = 16
@Field static final int MSG_LIST_SWITCH_RESPONSE = 17
@Field static final int MSG_LIST_TEXT_SENSOR_RESPONSE = 18
@Field static final int MSG_LIST_ENTITIES_RESPONSE = 19
@Field static final int MSG_SUBSCRIBE_STATES_REQUEST = 20
@Field static final int MSG_BINARY_SENSOR_STATE_RESPONSE = 21
@Field static final int MSG_COVER_STATE_RESPONSE = 22
@Field static final int MSG_FAN_STATE_RESPONSE = 23
@Field static final int MSG_LIGHT_STATE_RESPONSE = 24
@Field static final int MSG_SENSOR_STATE_RESPONSE = 25
@Field static final int MSG_SWITCH_STATE_RESPONSE = 26
@Field static final int MSG_TEXT_SENSOR_STATE_RESPONSE = 27
@Field static final int MSG_SUBSCRIBE_LOGS_REQUEST = 28
@Field static final int MSG_SUBSCRIBE_LOGS_RESPONSE = 29
@Field static final int MSG_COVER_COMMAND_REQUEST = 30
@Field static final int MSG_FAN_COMMAND_REQUEST = 31
@Field static final int MSG_LIGHT_COMMAND_REQUEST = 32
@Field static final int MSG_SWITCH_COMMAND_REQUEST = 33
@Field static final int MSG_SUBSCRIBE_HA_SERVICES_REQUEST = 34
@Field static final int MSG_HA_SERVICE_RESPONSE = 35
@Field static final int MSG_GET_TIME_REQUEST = 36
@Field static final int MSG_GET_TIME_RESPONSE = 37
@Field static final int MSG_SUBSCRIBE_HA_STATES_REQUEST = 38 // TODO
@Field static final int MSG_SUBSCRIBE_HA_STATE_RESPONSE = 39 // TODO
@Field static final int MSG_HA_STATE_RESPONSE = 40 // TODO
@Field static final int MSG_LIST_SERVICES_RESPONSE = 41 // TODO
@Field static final int MSG_EXECUTE_SERVICE_REQUEST = 42 // TODO
@Field static final int MSG_LIST_CAMERA_RESPONSE = 43
@Field static final int MSG_CAMERA_IMAGE_RESPONSE = 44
@Field static final int MSG_CAMERA_IMAGE_REQUEST = 45
@Field static final int MSG_LIST_CLIMATE_RESPONSE = 46 // TODO
@Field static final int MSG_CLIMATE_STATE_RESPONSE = 47 // TODO
@Field static final int MSG_CLIMATE_COMMAND_REQUEST = 48 // TODO
@Field static final int MSG_LIST_NUMBER_RESPONSE = 49
@Field static final int MSG_NUMBER_STATE_RESPONSE = 50
@Field static final int MSG_NUMBER_COMMAND_REQUEST = 51
@Field static final int MSG_LIST_SELECT_RESPONSE = 52
@Field static final int MSG_SELECT_STATE_RESPONSE = 53
@Field static final int MSG_SELECT_COMMAND_REQUEST = 54
@Field static final int MSG_LIST_SIREN_RESPONSE = 55
@Field static final int MSG_SIREN_STATE_RESPONSE = 56
@Field static final int MSG_SIREN_COMMAND_REQUEST = 57
@Field static final int MSG_LIST_LOCK_RESPONSE = 58
@Field static final int MSG_LOCK_STATE_RESPONSE = 59
@Field static final int MSG_LOCK_COMMAND_REQUEST = 60
@Field static final int MSG_LIST_BUTTON_RESPONSE = 61
@Field static final int MSG_BUTTON_COMMAND_REQUEST = 62
@Field static final int MSG_LIST_MEDIA_RESPONSE = 63
@Field static final int MSG_MEDIA_STATE_RESPONSE = 64
@Field static final int MSG_MEDIA_COMMAND_REQUEST = 65
@Field static final int MSG_SUBSCRIBE_BTLE_REQUEST = 66
@Field static final int MSG_BLUETOOTH_LE_RESPONSE = 67

@Field static final int ENTITY_CATEGORY_NONE = 0
@Field static final int ENTITY_CATEGORY_CONFIG = 1
@Field static final int ENTITY_CATEGORY_DIAGNOSTIC = 2

@Field static final int COVER_OPERATION_IDLE = 0
@Field static final int COVER_OPERATION_IS_OPENING = 1
@Field static final int COVER_OPERATION_IS_CLOSING = 2

@Field static final int FAN_SPEED_LOW = 0
@Field static final int FAN_SPEED_MEDIUM = 1
@Field static final int FAN_SPEED_HIGH = 2

@Field static final int FAN_DIRECTION_FORWARD = 0
@Field static final int FAN_DIRECTION_REVERSE = 1

@Field static final int STATE_CLASS_NONE = 0
@Field static final int STATE_CLASS_MEASUREMENT = 1
@Field static final int STATE_CLASS_TOTAL_INCREASING = 2
@Field static final int STATE_CLASS_TOTAL = 3

@Field static final int LAST_RESET_NONE = 0
@Field static final int LAST_RESET_NEVER = 1
@Field static final int LAST_RESET_AUTO = 2

@Field static final int CLIMATE_MODE_OFF = 0
@Field static final int CLIMATE_MODE_HEAT_COOL = 1
@Field static final int CLIMATE_MODE_COOL = 2
@Field static final int CLIMATE_MODE_HEAT = 3
@Field static final int CLIMATE_MODE_FAN_ONLY = 4
@Field static final int CLIMATE_MODE_DRY = 5
@Field static final int CLIMATE_MODE_AUTO = 6

@Field static final int CLIMATE_FAN_ON = 0
@Field static final int CLIMATE_FAN_OFF = 1
@Field static final int CLIMATE_FAN_AUTO = 2
@Field static final int CLIMATE_FAN_LOW = 3
@Field static final int CLIMATE_FAN_MEDIUM = 4
@Field static final int CLIMATE_FAN_HIGH = 5
@Field static final int CLIMATE_FAN_MIDDLE = 6
@Field static final int CLIMATE_FAN_FOCUS = 7
@Field static final int CLIMATE_FAN_DIFFUSE = 8

@Field static final int CLIMATE_SWING_OFF = 0
@Field static final int CLIMATE_SWING_BOTH = 1
@Field static final int CLIMATE_SWING_VERTICAL = 2
@Field static final int CLIMATE_SWING_HORIZONTAL = 3

@Field static final int CLIMATE_ACTION_OFF = 0
@Field static final int CLIMATE_ACTION_COOLING = 2
@Field static final int CLIMATE_ACTION_HEATING = 3
@Field static final int CLIMATE_ACTION_IDLE = 4
@Field static final int CLIMATE_ACTION_DRYING = 5
@Field static final int CLIMATE_ACTION_FAN = 6

@Field static final int CLIMATE_PRESET_NONE = 0
@Field static final int CLIMATE_PRESET_HOME = 1
@Field static final int CLIMATE_PRESET_AWAY = 2
@Field static final int CLIMATE_PRESET_BOOST = 3
@Field static final int CLIMATE_PRESET_COMFORT = 4
@Field static final int CLIMATE_PRESET_ECO = 5
@Field static final int CLIMATE_PRESET_SLEEP = 6
@Field static final int CLIMATE_PRESET_ACTIVITY = 7

@Field static final int LOCK_STATE_NONE = 0
@Field static final int LOCK_STATE_LOCKED = 1
@Field static final int LOCK_STATE_UNLOCKED = 2
@Field static final int LOCK_STATE_JAMMED = 3
@Field static final int LOCK_STATE_LOCKING = 4
@Field static final int LOCK_STATE_UNLOCKING = 5

@Field static final int LOCK_UNLOCK = 0
@Field static final int LOCK_LOCK = 1
@Field static final int LOCK_OPEN = 2

@Field static final int MEDIA_PLAYER_STATE_NONE = 0
@Field static final int MEDIA_PLAYER_STATE_IDLE = 1
@Field static final int MEDIA_PLAYER_STATE_PLAYING = 2
@Field static final int MEDIA_PLAYER_STATE_PAUSED = 3

@Field static final int MEDIA_PLAYER_COMMAND_PLAY = 0
@Field static final int MEDIA_PLAYER_COMMAND_PAUSE = 1
@Field static final int MEDIA_PLAYER_COMMAND_STOP = 2
@Field static final int MEDIA_PLAYER_COMMAND_MUTE = 3
@Field static final int MEDIA_PLAYER_COMMAND_UNMUTE = 4

@Field static final int LOG_LEVEL_NONE = 0
@Field static final int LOG_LEVEL_ERROR = 1
@Field static final int LOG_LEVEL_WARN = 2
@Field static final int LOG_LEVEL_INFO = 3
@Field static final int LOG_LEVEL_CONFIG = 4
@Field static final int LOG_LEVEL_DEBUG = 5
@Field static final int LOG_LEVEL_VERBOSE = 6
@Field static final int LOG_LEVEL_VERY_VERBOSE = 7

@Field static final int COLOR_CAP_ON_OFF = 1
@Field static final int COLOR_CAP_BRIGHTNESS = 2
@Field static final int COLOR_CAP_WHITE = 4
@Field static final int COLOR_CAP_COLOR_TEMPERATURE = 8
@Field static final int COLOR_CAP_COLD_WARM_WHITE = 16
@Field static final int COLOR_CAP_RGB = 32
