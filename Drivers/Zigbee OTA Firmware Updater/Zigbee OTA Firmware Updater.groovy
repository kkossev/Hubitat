/*
  
  Experimental Zigbee OTA Firmware Updater Driver for Hubitat
  
  DOES NOT WORK !

  Hubitat keeps the OTA cluster traffic (e.g., Query Next Image Request) inside the platform, so custom drivers never see those commands; that prevents this driver from handling the full handshake

*/


import groovy.transform.Field
import java.net.URLEncoder

metadata {
    definition(name: "Zigbee OTA Firmware Updater", namespace: "kkossev", author: "ChatGPT") {
        capability "Actuator"        // Allows sending commands
        capability "Initialize"      // For optional initialization (not strictly needed)
        // No standard capability for OTA, we use custom command below.
        command "updateFirmware"     // Custom command to start the OTA update process
        attribute "otaStatus", "STRING"
        attribute "otaProgress", "NUMBER"
    }
    preferences {
        input(name: "firmwareFileName", type: "text", title: "OTA File Name in Hubitat File Manager", required: true)
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
    }
}

/* Internal state fields to hold OTA file data and metadata */
@Field static Map<Long, Map> otaSessions = [:]       // Per-device OTA session data keyed by device id

private Map getOtaSession() {
    Long devId = device?.getId() as Long
    if (devId == null) {
        return [:]
    }
    if (!otaSessions.containsKey(devId)) {
        otaSessions[devId] = [
            firmwareBytes: null,
            otaManufacturer: null,
            otaImageType: null,
            otaFileVersion: null,
            otaFileSize: null,
            otaInProgress: false,
            otaHardwareVersionRange: [:],
            zclSequence: null
        ]
    }
    return otaSessions[devId]
}

private void clearOtaSession() {
    Long devId = device?.getId() as Long
    if (devId != null) {
        otaSessions.remove(devId)
    }
}

private byte[] getFirmwareBytes() { getOtaSession().firmwareBytes as byte[] }
private void setFirmwareBytes(byte[] value) { getOtaSession().firmwareBytes = value }

private Integer getOtaManufacturer() { getOtaSession().otaManufacturer as Integer }
private void setOtaManufacturer(Integer value) { getOtaSession().otaManufacturer = value }

private Integer getOtaImageType() { getOtaSession().otaImageType as Integer }
private void setOtaImageType(Integer value) { getOtaSession().otaImageType = value }

private Long getOtaFileVersion() { getOtaSession().otaFileVersion as Long }
private void setOtaFileVersion(Long value) { getOtaSession().otaFileVersion = value }

private Long getOtaFileSize() { getOtaSession().otaFileSize as Long }
private void setOtaFileSize(Long value) { getOtaSession().otaFileSize = value }

private Boolean getOtaInProgress() { (getOtaSession().otaInProgress ?: false) as Boolean }
private void setOtaInProgress(Boolean value) { getOtaSession().otaInProgress = (value ?: false) }

private Map getOtaHardwareVersionRange() {
    Map range = getOtaSession().otaHardwareVersionRange as Map
    if (range == null) {
        range = [:]
        getOtaSession().otaHardwareVersionRange = range
    }
    return range
}

private void setOtaHardwareVersionRange(Map value) {
    getOtaSession().otaHardwareVersionRange = value ?: [:]
}

/** Initialize is called when the driver is installed or the hub reboots */
def initialize() {
    clearOtaSession()
    if (logEnable) log.debug "Initializing Zigbee OTA Update driver"
    sendEvent(name: "otaStatus", value: "idle", descriptionText: "OTA updater idle")
    sendEvent(name: "otaProgress", value: 0, unit: "%", descriptionText: "OTA progress 0%")
}

/**
 * Command to begin the firmware update process.
 * Reads the specified OTA file from local storage, parses header for compatibility,
 * and sends an Image Notify to the device to prompt a query.
 */
def updateFirmware() {
    if (!firmwareFileName) {
        log.warn "Firmware file name is not set in preferences."
        return
    }
    if (otaInProgress) {
        log.warn "OTA update already in progress."
        return
    }
    clearOtaSession()
    try {
        // Load the OTA file from Hubitat's File Manager as a byte array
        firmwareBytes = loadFirmwareFile(firmwareFileName)
    } catch(Exception e) {
        log.error "Failed to read OTA file '${firmwareFileName}': ${e.message ?: e}"
        clearOtaSession()
        return
    }
    if (!firmwareBytes) {
        log.error "Firmware file '${firmwareFileName}' is empty or could not be loaded."
        clearOtaSession()
        return
    }
    // Parse the OTA file header (first 56 bytes base, plus optional fields)
    try {
        parseOTAFileHeader()
    } catch(Exception e) {
        log.error "Error parsing OTA file header: ${e.message}"
        firmwareBytes = null
        clearOtaSession()
        return
    }
    // Log and update status
    log.info "Loaded OTA file '${firmwareFileName}' (Manufacturer ID=0x${intToHex(otaManufacturer,4)}, Image Type=0x${intToHex(otaImageType,4)}, Version=0x${intToHex(otaFileVersion,8)}, ${otaFileSize} bytes)"
    sendEvent(name: "otaStatus", value: "ready", descriptionText: "OTA file loaded, sending notify")
    otaInProgress = true
    sendEvent(name: "otaProgress", value: 0, unit: "%", descriptionText: "OTA progress 0%")

    // Send Zigbee Image Notify command to the device to initiate the OTA process.
    // Using payload type 0x03 (include manuf., image type, and new file version) and a query jitter of 100% (0x64).
    def payload = []
    payload << "03"    // Payload Type: 0x03 (query jitter + manuf + imageType + fileVersion)
    payload << "64"    // Query Jitter: 100 (0x64, encourages device to query immediately)
    /*
     appendLittleEndian(payload, otaManufacturer, 2)
     appendLittleEndian(payload, otaImageType, 2)
     appendLittleEndian(payload, otaFileVersion as Long, 4)
     */
    // Built-in Hubitat driver advertises wildcard values so clients will query details themselves.

    appendLittleEndian(payload, 0xFFFF, 2)      // Manufacturer wildcard
    appendLittleEndian(payload, 0xFFFF, 2)      // Image type wildcard
    appendLittleEndian(payload, 0xFFFFFFFFL, 4) // File version wildcard

    if (logEnable) log.debug "Sending Image Notify to device: payload=${payload}"
    sendOtaClusterCommand(0x00, payload)  // OTA command 0x00 (Image Notify)
    sendEvent(name: "otaStatus", value: "notifying", descriptionText: "Sent image notify to device")
    log.info "Image Notify sent to ${device.displayName}, awaiting Query Next Image request..."
}

// Build and emit an OTA cluster command using server-to-client frame control (0x09).
private void sendOtaClusterCommand(int commandId, List<String> payload = null) {
    Map command = buildOtaClusterCommand(commandId, payload)
    if (command?.action) {
        if (logEnable) {
            log.debug "Sending OTA 0x${intToHex(commandId,2)} frame=${command.frame}"
        }
        sendHubCommand(command.action as hubitat.device.HubAction)
    }
}

private Map buildOtaClusterCommand(int commandId, List<String> payload = null) {
    String dni = device?.deviceNetworkId
    if (!dni) {
        log.warn "Cannot send OTA command 0x${intToHex(commandId,2)}; device network id unavailable"
        return [:]
    }
    String nwk = normalizeHexString(dni, 4, dni)
    String endpoint = normalizeHexString(resolveEndpointId(), 2, "01")
    String profile = normalizeHexString(device?.getDataValue("profileId"), 4, "0104")
    List<String> frame = []
    frame << "09"  // Cluster specific, server to client, default response enabled
    frame << intToHex(nextOtaZclSequence(), 2)
    frame << intToHex(commandId, 2)
    if (payload) {
        frame.addAll(payload)
    }
    String frameData = frame.collect { String value -> (value ?: "00").toUpperCase() }.join(' ')
    endpoint = "01"

/*
    HE raw zigbee frame (for the same command)
    List cmds = ["he raw 0x${device.deviceNetworkId} 1 1 0x0501 {09 01 00 04}"]
        
    he raw 
    0x${device.deviceNetworkId} 16 bit hex address 
    1							source endpoint, always one				 
    1 							destination endpoint, device dependent
    0x0501 						zigbee cluster id
    {09 						frame control
        01 						sequence, always 01
            00 					command
                04}				command parameter(s)    
*/    

    String command = "he raw 0x${nwk} 0x01 0x${endpoint} 0x0019 { ${frameData} }"
    hubitat.device.HubAction action = new hubitat.device.HubAction(command, hubitat.device.Protocol.ZIGBEE)
    return [action: action, frame: frameData]
}

private int nextOtaZclSequence() {
    Map session = getOtaSession()
    Integer current = session.zclSequence as Integer
    current = ((current != null ? current : -1) + 1) & 0xFF
    session.zclSequence = current
    return current
}

private String normalizeHexString(String value, int width, String defaultValue) {
    String raw = value?.trim()
    if (!raw) {
        raw = defaultValue
    }
    if (raw.startsWith("0x") || raw.startsWith("0X")) {
        raw = raw.substring(2)
    }
    return raw.padLeft(width, '0').toUpperCase()
}

private String resolveEndpointId() {
    String endpoint = null
    try {
        endpoint = device?.getDataValue("endpointId")
    } catch (Exception ignored) {
        // Hubitat throws if data value unavailable; handled by fallback
    }
    if (!endpoint) {
        try {
            endpoint = device?.endpointId
        } catch (Exception ignored) {
            // no-op, default handled by caller
        }
    }
    return "01"
    //return endpoint
}

private byte[] loadFirmwareFile(String filename) {
    if (!filename?.trim()) {
        throw new IllegalArgumentException("Firmware file name is required")
    }
    def hub = location?.hubs?.find { it?.localIP }
    if (!hub?.localIP) {
        throw new IllegalStateException("Unable to determine hub local IP address")
    }
    String encodedName = filename.trim().split("/").collect { String segment ->
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }.join("/")
    String uri = "http://${hub.localIP}:8080/local/${encodedName}"
    byte[] result = null
    httpGet([uri: uri, contentType: "application/octet-stream", timeout: 120]) { resp ->
        if (resp.status != 200) {
            throw new IllegalStateException("Hub returned HTTP ${resp.status} for ${filename}")
        }
        def data = resp.data
        if (data instanceof byte[]) {
            result = data
        } else if (data?.metaClass?.respondsTo(data, "bytes")) {
            try {
                result = data.bytes
            } finally {
                if (data?.metaClass?.respondsTo(data, "close")) {
                    try {
                        data.close()
                    } catch (Exception ignore) {
                        // no-op
                    }
                }
            }
        } else if (data?.metaClass?.respondsTo(data, "read")) {
            List<Integer> collected = []
            byte[] buffer = new byte[512]
            int readCount = 0
            try {
                while (true) {
                    readCount = data.read(buffer)
                    if (readCount == -1) {
                        break
                    }
                    for (int i = 0; i < readCount; i++) {
                        collected << (buffer[i] & 0xFF)
                    }
                }
            } finally {
                if (data?.metaClass?.respondsTo(data, "close")) {
                    try {
                        data.close()
                    } catch (Exception ignore) {
                        // no-op
                    }
                }
            }
            result = new byte[collected.size()]
            for (int i = 0; i < collected.size(); i++) {
                result[i] = (byte)(collected[i] & 0xFF)
            }
        } else if (data instanceof String) {
            result = (data as String).getBytes("UTF-8")
        } else if (data != null) {
            def typeName = data?.metaClass?.theClass?.name ?: data.toString()
            throw new IllegalStateException("Unexpected response type ${typeName} for firmware download")
        }
    }
    if (!result) {
        throw new IllegalStateException("Firmware file '${filename}' is empty or inaccessible")
    }
    return result
}

/**
 * Parse incoming Zigbee messages (cluster commands).
 * We intercept OTA Upgrade cluster (0x0019) commands from the device and respond accordingly.
 */
def parse(String description) {
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (descMap?.clusterId == "0019") {
        // Handle OTA cluster commands
        def cmd = descMap.command as String
        if (logEnable) log.debug "OTA cluster message received: command=0x${cmd}, data=${descMap.data}"
        switch(cmd) {
            case "01": // Query Next Image Request (client -> server)
                processQueryNextImage(descMap)
                break
            case "03": // Image Block Request (client -> server)
                processImageBlockRequest(descMap)
                break
            case "04": // Image Page Request (client -> server) – rarely used
                processImagePageRequest(descMap)
                break
            case "06": // Upgrade End Request (client -> server)
                processUpgradeEndRequest(descMap)
                break
            default:
                if (logEnable) log.debug "Unhandled OTA command 0x${cmd}"
                break
        }
    } 
    // Optionally handle other clusters if needed (not used in this driver)
    else {
        // For non-OTA messages, just return description for default handling if any
        if (logEnable) log.debug "Non-OTA message received: ${description}"
        if (descMap.profileId == '0000') { //zdo
            parseZDOcommand(descMap)
        }
        return description
    }
}

/** Process a Query Next Image Request (Zigbee command 0x01 from client). */
private void processQueryNextImage(Map descMap) {
    // Parse Query Next Image Request fields (per Zigbee spec):
    // Frame: [FieldControl (1 byte), ManufacturerCode (2), ImageType (2), CurrentFileVersion (4), HardwareVersion (2 optional if field control indicates)]
    List<String> data = descMap.data as List<String>
    if (!data) return
    int fieldCtrl = Integer.parseInt(data[0], 16)
    int reqManufacturer = Integer.parseInt(data[2] + data[1], 16)  // bytes 1-2: little-endian
    int reqImageType   = Integer.parseInt(data[4] + data[3], 16)  // bytes 3-4
    long reqFileVersion= Long.parseLong(data[8] + data[7] + data[6] + data[5], 16)  // bytes 5-8
    Integer reqHardwareVersion = null
    if ((fieldCtrl & 0x01) == 0x01 && data.size() >= 11) {
        // If Hardware Version Present bit is set, parse bytes 9-10
        reqHardwareVersion = Integer.parseInt(data[10] + data[9], 16)
    }
    if (logEnable) {
        log.debug "QueryNextImage from device: manuf=0x${intToHex(reqManufacturer,4)}, imageType=0x${intToHex(reqImageType,4)}, currentVersion=0x${intToHex(reqFileVersion,8)}" + 
                  (reqHardwareVersion != null ? ", hardwareVersion=0x${intToHex(reqHardwareVersion,4)}" : "")
    }
    // Check if we have an image loaded and matching the request
    if (!firmwareBytes || otaManufacturer == null || otaImageType == null || otaFileVersion == null || otaFileSize == null) {
    // No firmware data loaded – respond with NO_IMAGE_AVAILABLE status (0x98)
        sendQueryNextImageResponse(status: 0x98)
        log.warn "No OTA image available for device; sent NO_IMAGE_AVAILABLE"
        otaInProgress = false
        return
    }
    // Verify manufacturer and image type match
    if (reqManufacturer != otaManufacturer || reqImageType != otaImageType) {
        // The loaded firmware is not intended for this device
        sendQueryNextImageResponse(status: 0x98)
        log.warn "OTA image not compatible (mismatch manufacturer or image type); sent NO_IMAGE_AVAILABLE"
        otaInProgress = false
        return
    }
    // (Optional) Check hardware version compatibility, if the OTA file specifies a range
    if (otaHardwareVersionRange?.min != null) {
        // If file has hardware version limits and device provided a version, ensure compatibility
        if (reqHardwareVersion != null) {
            if (reqHardwareVersion < otaHardwareVersionRange.min || reqHardwareVersion > otaHardwareVersionRange.max) {
                sendQueryNextImageResponse(status: 0x98)
                log.warn "Device hardware version 0x${intToHex(reqHardwareVersion,4)} not in OTA file supported range; sent NO_IMAGE_AVAILABLE"
                otaInProgress = false
                return
            }
        }
        // If device did not provide hardware version but file expects specific hardware, we assume incompatibility
        else {
            sendQueryNextImageResponse(status: 0x98)
            log.warn "Device hardware version unknown, OTA file requires specific hardware version; sent NO_IMAGE_AVAILABLE"
            otaInProgress = false
            return
        }
    }
    // Check firmware version - only offer update if file's version is newer than current
    if (otaFileVersion == null || otaFileVersion <= reqFileVersion) {
        sendQueryNextImageResponse(status: 0x98)
        log.info "Device firmware version 0x${intToHex(reqFileVersion,8)} is up-to-date or newer than OTA file; no update"
        otaInProgress = false
        return
    }
    // All checks passed: send Query Next Image Response with image info (status=0x00)
    sendQueryNextImageResponse(
        status: 0x00, 
        manufacturer: otaManufacturer, 
        imageType: otaImageType, 
        fileVersion: otaFileVersion, 
        imageSize: otaFileSize
    )
    sendEvent(name: "otaStatus", value: "available", descriptionText: "Update available, device will begin download")
    log.info "Informed device of available update (version 0x${intToHex(otaFileVersion,8)}, size ${otaFileSize} bytes)"
}

/** Construct and send a Query Next Image Response (server -> client, Zigbee OTA command 0x02). */
private void sendQueryNextImageResponse(Map params) {
    int status = params.status as Integer
    def payload = []
    payload << intToHex(status, 2)  // Status, 1 byte (in hex string of length 2)
    if (status == 0x00) {
        // Only include these fields if status is Success (0x00) indicating an image is available.
        appendLittleEndian(payload, params.manufacturer as Integer, 2)
        appendLittleEndian(payload, params.imageType as Integer, 2)
        appendLittleEndian(payload, params.fileVersion as Long, 4)
        appendLittleEndian(payload, params.imageSize as Long, 4)
    }
    if (logEnable) log.debug "Sending QueryNextImageResponse: status=0x${intToHex(status,2)}, payload=${payload}"
    sendOtaClusterCommand(0x02, payload)
}

/** Process an Image Block Request (Zigbee command 0x03 from client). */
private void processImageBlockRequest(Map descMap) {
    if (!otaInProgress || !firmwareBytes) {
        if (logEnable) log.debug "Received block request but no OTA in progress; ignoring."
        return
    }
    List<String> data = descMap.data as List<String>
    if (!data || data.size() < 14) {
        if (logEnable) log.debug "Malformed ImageBlockRequest data: ${data}"
        return
    }
    // Parse the Image Block Request fields:
    int fieldCtrl = Integer.parseInt(data[0], 16)
    int reqManufacturer = Integer.parseInt(data[2] + data[1], 16)
    int reqImageType   = Integer.parseInt(data[4] + data[3], 16)
    long reqFileVersion= Long.parseLong(data[8] + data[7] + data[6] + data[5], 16)
    long fileOffset    = Long.parseLong(data[12] + data[11] + data[10] + data[9], 16)
    short maxDataSize  = Integer.parseInt(data[13], 16) as Short  // Max data bytes the client wants per block
    // (Optional) If fieldCtrl bit0 is set, the request includes the requester IEEE (8 bytes) - not used here
    // (Optional) If fieldCtrl bit1 is set, includes MinimumBlockPeriod (2 bytes) at end - we ignore for now.
    if (logEnable) log.debug "ImageBlockRequest: offset=${fileOffset}, maxSize=${maxDataSize}"
    // Verify the request matches the image we are sending
    if (reqManufacturer != otaManufacturer || reqImageType != otaImageType || reqFileVersion != otaFileVersion) {
        log.warn "Block Request image ID does not match current OTA file (device may have aborted)."
        otaInProgress = false
        return
    }
    // Calculate how many bytes to send in this chunk
    int totalLength = firmwareBytes.length
    int remaining = (fileOffset >= totalLength) ? 0 : totalLength - (int) fileOffset
    int blockSize = Math.min(maxDataSize & 0xFF, remaining)
    // Extract the chunk from firmwareBytes at the given offset
    byte[] blockData = new byte[0]
    if (blockSize > 0) {
        blockData = new byte[blockSize]
        int start = (int) fileOffset
        for (int i = 0; i < blockSize; i++) {
            blockData[i] = firmwareBytes[start + i]
        }
    }
    // Determine status for the response
    int status = (blockSize > 0) ? 0x00 : 0x98  // 0x00 = Success (data follows), 0x98 = NO_IMAGE_AVAILABLE (if something went wrong)
    // Build Image Block Response payload:
    def payload = []
    payload << intToHex(status, 2)  // Status
    if (status == 0x00) {
        appendLittleEndian(payload, otaManufacturer, 2)
        appendLittleEndian(payload, otaImageType, 2)
        appendLittleEndian(payload, otaFileVersion, 4)
        appendLittleEndian(payload, fileOffset as Long, 4)
        payload << intToHex(blockSize, 2)
        payload.addAll(bytesToHexList(blockData))
    }
    if (logEnable) log.debug "Sending ImageBlockResponse: offset=${fileOffset}, ${blockSize} bytes"
    sendOtaClusterCommand(0x05, payload)
    if (status != 0x00) {
        otaInProgress = false
    }
    // Update progress attribute/event
    if (otaFileSize > 0) {
        int percent = Math.min(100, ((fileOffset + blockSize) * 100 / otaFileSize) as int)
        sendEvent(name: "otaProgress", value: percent, unit: "%", descriptionText: "OTA progress ${percent}%")
    }
    if ((fileOffset + blockSize) >= otaFileSize) {
        log.info "All blocks sent (last offset ${fileOffset}); awaiting device to confirm upgrade completion."
    }
}

/** Process an Image Page Request (Zigbee command 0x04 from client, optional).
 *  The Image Page Request is an alternative to requesting one block at a time.
 *  For simplicity, this implementation will respond by treating it as multiple block requests. */
private void processImagePageRequest(Map descMap) {
    log.warn "Image Page Request received - treating as sequential block requests."
    // A simplistic handling: just respond to the first block similarly to processImageBlockRequest.
    // (Proper implementation would send blocks in succession without further requests.)
    processImageBlockRequest(descMap)
    // Note: In a real implementation, we might stream blocks up to the page size here.
}

/** Process an Upgrade End Request (Zigbee command 0x06 from client). */
private void processUpgradeEndRequest(Map descMap) {
    List<String> data = descMap.data as List<String>
    if (!data || data.size() < 1) {
        if (logEnable) log.debug "Malformed UpgradeEndRequest data: ${data}"
        return
    }
    int status = Integer.parseInt(data[0], 16)  // UpgradeStatus from the device (0x00 = success)
    // The request may include manufacturer, imageType, fileVersion as well (bytes 1-?); parse if needed:
    if (logEnable) log.debug "UpgradeEndRequest status=0x${intToHex(status,2)}"
    if (status == 0x00) {
        // Device reports successful download and verification of the image
        // Send Upgrade End Response (command 0x07) to instruct device to upgrade now
        sendUpgradeEndResponse()
        sendEvent(name: "otaStatus", value: "complete", descriptionText: "OTA update completed")
        log.info "Device signaled successful image receipt; sent Upgrade End Response (upgrade now)."
    } else {
        // Device reported an error or aborted (e.g., 0x95 = ABORT, 0x96 = INVALID_IMAGE, etc.)
        log.warn "Device reported OTA upgrade failure (status 0x${intToHex(status,2)})."
        sendEvent(name: "otaStatus", value: "failed", descriptionText: "OTA update failed (device status 0x${intToHex(status,2)})")
    }
    // OTA process finished (success or fail) – clean up
    otaInProgress = false
    firmwareBytes = null  // free memory
    clearOtaSession()
}

/** Construct and send an Upgrade End Response (server -> client, Zigbee OTA command 0x07).
 *  This tells the device when to apply the new firmware. We set upgrade time to immediate. */
private void sendUpgradeEndResponse() {
    if (otaManufacturer == null || otaImageType == null || otaFileVersion == null) {
        log.warn "Cannot send Upgrade End Response; OTA metadata missing"
        return
    }
    // Per Zigbee spec, Upgrade End Response payload: [ManufacturerCode (2), ImageType (2), FileVersion (4), CurrentTime (4), UpgradeTime (4)]
    def payload = []
    appendLittleEndian(payload, otaManufacturer, 2)
    appendLittleEndian(payload, otaImageType, 2)
    appendLittleEndian(payload, otaFileVersion, 4)
    // CurrentTime and UpgradeTime: set both to 0x00000000 for immediate upgrade
    appendLittleEndian(payload, 0L, 4)
    appendLittleEndian(payload, 0L, 4)
    sendOtaClusterCommand(0x07, payload)
    if (logEnable) log.debug "UpgradeEndResponse sent (immediate upgrade)"
}

/** Helper: Parse the header of the loaded OTA file to extract metadata (manufacturer, image type, version, size, etc.). */
private void parseOTAFileHeader() {
    if (!firmwareBytes || firmwareBytes.size() < 56) {
        throw new IllegalArgumentException("File too short to be a valid Zigbee OTA image")
    }
    // Verify magic number (first 4 bytes should be 0x0B 0xEF 0xF1 0x1E in little endian for a Zigbee OTA file)
    long magic = toUint32LE(firmwareBytes, 0)
    if (magic != 0x0BEEF11EL) {
        throw new IllegalArgumentException("Not a valid Zigbee OTA file (magic header mismatch)")
    }
    // Bytes 4-5: header version (not used here), 6-7: header length, 8-9: field control
    int headerLength = toUint16LE(firmwareBytes, 6)
    int fieldControl = toUint16LE(firmwareBytes, 8)
    // Bytes 10-11: Manufacturer Code, 12-13: Image Type
    otaManufacturer = toUint16LE(firmwareBytes, 10)
    otaImageType    = toUint16LE(firmwareBytes, 12)
    // Bytes 14-17: File Version (32-bit), 18-19: Zigbee Stack Version (not used)
    otaFileVersion  = toUint32LE(firmwareBytes, 14)
    // Bytes 52-55: Total Image Size
    otaFileSize     = toUint32LE(firmwareBytes, 52)
    long actualSize = firmwareBytes.length
    if (actualSize < otaFileSize) {
        throw new IllegalArgumentException("Firmware file truncated: header expects ${otaFileSize} bytes but only ${actualSize} bytes available")
    }
    // Optional fields after the standard header (if any):
    otaHardwareVersionRange = [:]
    int pos = 56  // position after base header
    if ((fieldControl & 0x0001) != 0) {
        // Security Credential Version present (1 byte) – skip
        pos += 1
    }
    if ((fieldControl & 0x0002) != 0) {
        // Device Specific File (destination IEEE present) – skip 8 bytes
        pos += 8
    }
    if ((fieldControl & 0x0004) != 0) {
        // Hardware Versions present – read 2 bytes min, 2 bytes max
        otaHardwareVersionRange.min = toUint16LE(firmwareBytes, pos)
        otaHardwareVersionRange.max = toUint16LE(firmwareBytes, pos+2)
        pos += 4
    }
    // (We ignore any further sub-element parsing; not needed for transferring bytes)
}

/** Utility: convert two bytes at offset to unsigned 16-bit integer (little-endian) */
private int toUint16LE(byte[] data, int offset) {
    return ((data[offset+1] & 0xFF) << 8) | (data[offset] & 0xFF)
}
/** Utility: convert four bytes at offset to unsigned 32-bit integer (little-endian), returned as Long */
private long toUint32LE(byte[] data, int offset) {
    long result = 0
    for (int i = 0; i < 4; i++) {
        result |= ((long)(data[offset + i] & 0xFF)) << (8 * i)
    }
    return result & 0xFFFFFFFFL
}
/** Utility: format an integer to hex string with given width (number of hex digits). */
private String intToHex(Number value, int width) {
    if (width <= 0) {
        throw new IllegalArgumentException("Width must be positive")
    }
    int bitWidth = width * 4
    long mask = (bitWidth >= 64) ? -1L : ((1L << bitWidth) - 1L)
    long masked = (value as long) & mask
    return String.format("%0${width}X", masked)
}
/** Utility: format an integer to little-endian hex string of given byte length. For example, intToHexLE(0x1234,2) -> "3412". */
private String intToHexLE(Number value, int byteCount) {
    long val = value as Long
    String hex = ""
    for (int i = 0; i < byteCount; i++) {
        int byteVal = (val >> (8 * i)) & 0xFF
        hex += String.format("%02X", byteVal)
    }
    return hex
}

private void appendLittleEndian(List<String> payload, Number value, int byteCount) {
    if (payload == null) {
        throw new IllegalArgumentException("Payload list cannot be null")
    }
    long val = value as long
    for (int i = 0; i < byteCount; i++) {
        payload << String.format("%02X", ((val >> (8 * i)) & 0xFF))
    }
}

private List<String> bytesToHexList(byte[] arr) {
    if (!arr) {
        return []
    }
    arr.collect { byte b -> String.format("%02X", b & 0xFF) }
}
/** Utility: convert a byte array to hex string (each byte as two hex chars). */
private static String byteArrayToHexString(byte[] arr) {
    arr?.collect { byte b -> String.format("%02X", b & 0xFF) }?.join() ?: ""
}

void parseZDOcommand(Map descMap) {
    switch (descMap.clusterId) {
        case '0006' :
            if (logEnable) { log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" }
            break
        case '0013' : // device announcement
            logInfo "Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            //state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1
            break
        case '8001' :  // Device and Service Discovery - IEEE_addr_rsp
            if (logEnable) { log.info "${device.displayName} Received Device and Service Discovery - IEEE_addr_rsp, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" }
            break
            break
        case '8004' : // simple descriptor response
            if (logEnable) { log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" }
            parseSimpleDescriptorResponse(descMap)
            break
        case '8005' : // endpoint response
            if (logEnable) { log.info "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${ descMap.data[4]}  endpointList = ${descMap.data[5]}" }
            break
        case '8021' : // bind response
            if (logEnable) { log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case '8038' : // Management Network Update Notify
            if (logEnable) { log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}" }
            break
        default :
            if (logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
    }
}

void parseSimpleDescriptorResponse(Map descMap) {
    logDebug "Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
    if (logEnable == true) { log.info "${device.displayName} Endpoint: ${descMap.data[5]} Application Device:${descMap.data[9]}${descMap.data[8]}, Application Version:${descMap.data[10]}" }
    int inputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[11])
    String inputClusterList = ''
    if (inputClusterCount != 0) {
        for (int i in 1..inputClusterCount) {
            inputClusterList += descMap.data[13 + (i - 1) * 2] + descMap.data[12 + (i - 1) * 2] + ','
        }
        inputClusterList = inputClusterList.substring(0, inputClusterList.length() - 1)
        if (logEnable == true) { log.info "${device.displayName} endpoint ${descMap.data[5]} Input Cluster Count: ${inputClusterCount} Input Cluster List : ${inputClusterList}" }
        if (descMap.data[5] == device.endpointId) {
            if (getDataValue('inClusters') != inputClusterList)  {
                if (logEnable == true) { log.warn "${device.displayName} inClusters=${getDataValue('inClusters')} differs from inputClusterList:${inputClusterList} - will be updated!" }
                updateDataValue('inClusters', inputClusterList)
            }
        }
    }

    int outputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[12 + inputClusterCount * 2])
    String outputClusterList = ''
    if (outputClusterCount != 0) {
        for (int i in 1..outputClusterCount) {
            outputClusterList += descMap.data[14 + inputClusterCount * 2 + (i - 1) * 2] + descMap.data[13 + inputClusterCount * 2 + (i - 1) * 2] + ','
        }
        outputClusterList = outputClusterList.substring(0, outputClusterList.length() - 1)
        if (logEnable == true) { log.info "${device.displayName} endpoint ${descMap.data[5]} Output Cluster Count: ${outputClusterCount} Output Cluster List : ${outputClusterList}" }
        if (descMap.data[5] == device.endpointId) {
            if (getDataValue('outClusters') != outputClusterList)  {
                if (logEnable == true) { log.warn "${device.displayName} outClusters=${getDataValue('outClusters')} differs from outputClusterList:${outputClusterList} -  will be updated!" }
                updateDataValue('outClusters', outputClusterList)
            }
            else { log.warn "device.outClusters = ${device.outClusters } outputClusterList = ${outputClusterList }" }
        }
    }
}

void logDebug(final String msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

void logInfo(final String msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

void logWarn(final String msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

