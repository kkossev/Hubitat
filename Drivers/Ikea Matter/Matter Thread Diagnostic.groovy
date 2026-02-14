/*
 * Matter Thread Diagnostic Driver
 *
 * Reads Matter Diagnostic Clusters and logs diagnostic information:
 * - Thread Network Diagnostics (0x0035)
 * - General Diagnostics (0x0033)
 * - Software Diagnostics (0x0034)
 *
 * Based on Matter 1.5 Specification
 * Last edited: 2026/02/14
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(
        name: "Matter Thread Diagnostic", 
        namespace: "community", 
        author: "kkossev + Claude Sonnet 4.5 :)",
        importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Ikea%20Matter/Matter%20Thread%20Diagnostic.groovy"
    ) {
        capability "Sensor"
        capability "Refresh"
        capability "Initialize"
        capability "MotionSensor"
        capability "IlluminanceMeasurement"
        capability "Battery"
        
        // Thread Network Diagnostics Attributes
        attribute "channel", "number"
        attribute "routingRole", "string"
        attribute "networkName", "string"
        attribute "panId", "string"
        attribute "extendedPanId", "string"
        attribute "meshLocalPrefix", "string"
        attribute "partitionId", "number"
        attribute "neighborCount", "number"
        attribute "routeTableSize", "number"
        
        // General Diagnostics Attributes
        attribute "rebootCount", "number"
        attribute "upTime", "number"
        attribute "bootReason", "string"
        attribute "networkInterfaces", "string"
        
        // Software Diagnostics Attributes
        attribute "heapFree", "number"
        attribute "heapUsed", "number"
        
        command "readThreadDiagnostics"
        command "readGeneralDiagnostics"
        command "readSoftwareDiagnostics"
    }
    
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "autoRefreshInterval", type: "enum", title: "Auto-refresh interval", 
              options: ["Disabled", "1 minute", "5 minutes", "15 minutes", "30 minutes", "1 hour"], 
              defaultValue: "15 minutes"
    }
}

void installed() { 
    logInfo "installed()"
    initialize() 
}

void updated() {
    logInfo "updated()"
    if (logEnable) runIn(7200, "logsOff")
    unschedule()
    initialize()
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    logWarn "Debug logging disabled"
}

void initialize() {
    logInfo "initialize()"
    device.updateDataValue('newParse', "true")  // Enable new MAP parse format
    subscribeToAttributes()
    refresh()
    scheduleAutoRefresh()
}

void scheduleAutoRefresh() {
    def interval = settings.autoRefreshInterval ?: "15 minutes"
    switch (interval) {
        case "1 minute":
            runEvery1Minute(refresh)
            break
        case "5 minutes":
            runEvery5Minutes(refresh)
            break
        case "15 minutes":
            runEvery15Minutes(refresh)
            break
        case "30 minutes":
            runEvery30Minutes(refresh)
            break
        case "1 hour":
            runEvery1Hour(refresh)
            break
        default:
            // Disabled
            break
    }
}

void refresh() {
    logDebug "refresh() - reading all diagnostic clusters"
    readThreadDiagnostics()
    runIn(2, "readGeneralDiagnostics")
    runIn(4, "readSoftwareDiagnostics")
}

void readThreadDiagnostics() {
    logInfo "Reading Thread Network Diagnostics (0x0035)..."
    List<Map<String,String>> paths = []
    
    // Core Thread Network attributes
    paths.add(matter.attributePath(0x00, 0x0035, 0x0000)) // Channel
    paths.add(matter.attributePath(0x00, 0x0035, 0x0001)) // RoutingRole
    paths.add(matter.attributePath(0x00, 0x0035, 0x0002)) // NetworkName
    paths.add(matter.attributePath(0x00, 0x0035, 0x0003)) // PanId
    paths.add(matter.attributePath(0x00, 0x0035, 0x0004)) // ExtendedPanId
    paths.add(matter.attributePath(0x00, 0x0035, 0x0005)) // MeshLocalPrefix
    paths.add(matter.attributePath(0x00, 0x0035, 0x0007)) // NeighborTable
    paths.add(matter.attributePath(0x00, 0x0035, 0x0008)) // RouteTable
    paths.add(matter.attributePath(0x00, 0x0035, 0x0009)) // PartitionId
    paths.add(matter.attributePath(0x00, 0x0035, 0x000A)) // Weighting
    paths.add(matter.attributePath(0x00, 0x0035, 0x000B)) // DataVersion
    paths.add(matter.attributePath(0x00, 0x0035, 0x000D)) // LeaderRouterId
    
    // Sensor data (if available)
    paths.add(matter.attributePath(0x01, 0x0400, 0x0000)) // Illuminance
    paths.add(matter.attributePath(0x02, 0x0406, 0x0000)) // Occupancy (Motion)
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C)) // Battery
    
    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

void readGeneralDiagnostics() {
    logInfo "Reading General Diagnostics (0x0033)..."
    List<Map<String,String>> paths = []
    
    paths.add(matter.attributePath(0x00, 0x0033, 0x0000)) // NetworkInterfaces
    paths.add(matter.attributePath(0x00, 0x0033, 0x0001)) // RebootCount
    paths.add(matter.attributePath(0x00, 0x0033, 0x0002)) // UpTime
    paths.add(matter.attributePath(0x00, 0x0033, 0x0003)) // TotalOperationalHours
    paths.add(matter.attributePath(0x00, 0x0033, 0x0004)) // BootReason
    paths.add(matter.attributePath(0x00, 0x0033, 0x0005)) // ActiveHardwareFaults
    paths.add(matter.attributePath(0x00, 0x0033, 0x0006)) // ActiveRadioFaults
    paths.add(matter.attributePath(0x00, 0x0033, 0x0007)) // ActiveNetworkFaults
    
    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

void readSoftwareDiagnostics() {
    logInfo "Reading Software Diagnostics (0x0034)..."
    List<Map<String,String>> paths = []
    
    paths.add(matter.attributePath(0x00, 0x0034, 0x0000)) // ThreadMetrics
    paths.add(matter.attributePath(0x00, 0x0034, 0x0001)) // CurrentHeapFree
    paths.add(matter.attributePath(0x00, 0x0034, 0x0002)) // CurrentHeapUsed
    paths.add(matter.attributePath(0x00, 0x0034, 0x0003)) // CurrentHeapHighWatermark
    
    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToAttributes() {
    logDebug "subscribeToAttributes()"
    List<Map<String,String>> paths = []
    
    // Subscribe to key Thread Network attributes
    paths.add(matter.attributePath(0x00, 0x0035, 0x0000)) // Channel
    paths.add(matter.attributePath(0x00, 0x0035, 0x0001)) // RoutingRole
    paths.add(matter.attributePath(0x00, 0x0035, 0x0002)) // NetworkName
    paths.add(matter.attributePath(0x00, 0x0035, 0x0007)) // NeighborTable
    
    // Subscribe to General Diagnostics
    paths.add(matter.attributePath(0x00, 0x0033, 0x0001)) // RebootCount
    paths.add(matter.attributePath(0x00, 0x0033, 0x0002)) // UpTime
    
    // Subscribe to sensor attributes (if available)
    paths.add(matter.attributePath(0x01, 0x0400, 0x0000)) // Illuminance
    paths.add(matter.attributePath(0x02, 0x0406, 0x0000)) // Occupancy
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C)) // Battery
    
    String cmd = matter.cleanSubscribe(1, 0xFFFF, paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
    
    logInfo "Subscribed to Matter diagnostic + sensor attributes"
}

// New format parse(Map) - used when newParse is enabled
def parse(Map msg) {
    logDebug "parse(Map): ${msg}"
    if (!msg) return

    // Handle subscription results
    if (msg.callbackType == "SubscriptionResult") {
        logDebug "Subscription result: ${msg}"
        return
    }

    // New format provides integers directly
    Integer ep     = msg.endpointInt
    Integer clus   = msg.clusterInt
    Integer attrId = msg.attrInt

    if (ep == null || clus == null || attrId == null) return
    
    // Route to appropriate handler
    switch (clus) {
        case 53:  // Thread Network Diagnostics (0x0035)
            handleThreadNetworkDiagnostics(ep, attrId, msg)
            break
        case 51:  // General Diagnostics (0x0033)
            handleGeneralDiagnostics(ep, attrId, msg)
            break
        case 52:  // Software Diagnostics (0x0034)
            handleSoftwareDiagnostics(ep, attrId, msg)
            break
        case 47:  // Power Source (0x002F) - Battery
            handlePowerSource(ep, attrId, msg)
            break
        case 1024: // Illuminance Measurement (0x0400)
            handleIlluminance(ep, attrId, msg)
            break
        case 1030: // Occupancy Sensing (0x0406)
            handleOccupancy(ep, attrId, msg)
            break
        default:
            logDebug "Unhandled cluster: ${clus} (0x${Integer.toHexString(clus).toUpperCase()})"
            break
    }
}

// Legacy format parse(String) - kept for backward compatibility
def parse(String description) {
    Map msg = matter.parseDescriptionAsMap(description)
    logDebug "parse(String): ${description}"
    if (!msg) return
    
    // Convert to new format and delegate
    parse(msg)
}

private void handleThreadNetworkDiagnostics(Integer ep, Integer attrId, Map msg) {
    def value = msg.value  // Can be Integer or other type depending on attribute
    
    switch (attrId) {
        case 0: // Channel (0x0000)
            Integer channel = value as Integer
            if (channel != null) {
                sendEvent(name: "channel", value: channel)
                logInfo "Thread Channel: ${channel}"
            }
            break
            
        case 1: // RoutingRole (0x0001)
            Integer roleVal = value as Integer
            String role = decodeRoutingRole(roleVal)
            sendEvent(name: "routingRole", value: role)
            logInfo "Routing Role: ${role} (${roleVal})"
            break
            
        case 2: // NetworkName (0x0002)
            String netName = value?.toString()
            if (netName) {
                sendEvent(name: "networkName", value: netName)
                logInfo "Network Name: ${netName}"
            }
            break
            
        case 3: // PanId (0x0003)
            Integer panId = value as Integer
            if (panId != null) {
                String panIdStr = "0x${Integer.toHexString(panId).toUpperCase().padLeft(4, '0')}"
                sendEvent(name: "panId", value: panIdStr)
                logInfo "PAN ID: ${panIdStr}"
            }
            break
            
        case 4: // ExtendedPanId (0x0004)
            if (value) {
                String extPanId = value.toString()
                if (!extPanId.startsWith("0x")) extPanId = "0x${extPanId}"
                sendEvent(name: "extendedPanId", value: extPanId.toUpperCase())
                logInfo "Extended PAN ID: ${extPanId}"
            }
            break
            
        case 5: // MeshLocalPrefix (0x0005)
            if (value) {
                sendEvent(name: "meshLocalPrefix", value: value.toString())
                logInfo "Mesh Local Prefix: ${value}"
            }
            break
            
        case 7: // NeighborTable (0x0007)
            handleNeighborTable(msg)
            break
            
        case 8: // RouteTable (0x0008)
            handleRouteTable(msg)
            break
            
        case 9: // PartitionId (0x0009)
            Integer partId = value as Integer
            if (partId != null) {
                sendEvent(name: "partitionId", value: partId)
                logInfo "Partition ID: ${partId}"
            }
            break
            
        case 10: // Weighting (0x000A)
            Integer weight = value as Integer
            if (weight != null) {
                logInfo "Weighting: ${weight}"
            }
            break
            
        case 11: // DataVersion (0x000B)
            Integer dataVer = value as Integer
            if (dataVer != null) {
                logInfo "Data Version: ${dataVer}"
            }
            break
            
        case 13: // LeaderRouterId (0x000D)
            Integer leaderId = value as Integer
            if (leaderId != null) {
                logInfo "Leader Router ID: ${leaderId}"
            }
            break
            
        default:
            logDebug "Thread Network Diagnostics attr ${attrId}: ${value}"
            break
    }
}

private void handleGeneralDiagnostics(Integer ep, Integer attrId, Map msg) {
    def value = msg.value
    
    switch (attrId) {
        case 0: // NetworkInterfaces (0x0000)
            handleNetworkInterfaces(msg)
            break
            
        case 1: // RebootCount (0x0001)
            Integer count = value as Integer
            if (count != null) {
                sendEvent(name: "rebootCount", value: count)
                logInfo "Reboot Count: ${count}"
            }
            break
            
        case 2: // UpTime (0x0002)
            Long uptime = value as Long
            if (uptime != null) {
                sendEvent(name: "upTime", value: uptime)
                String uptimeStr = formatUptime(uptime)
                logInfo "Uptime: ${uptimeStr} (${uptime} seconds)"
            }
            break
            
        case 3: // TotalOperationalHours (0x0003)
            Integer hours = value as Integer
            if (hours != null) {
                logInfo "Total Operational Hours: ${hours}"
            }
            break
            
        case 4: // BootReason (0x0004)
            Integer reason = value as Integer
            String bootReason = decodeBootReason(reason)
            sendEvent(name: "bootReason", value: bootReason)
            logInfo "Boot Reason: ${bootReason}"
            break
            
        case 5: // ActiveHardwareFaults (0x0005)
            handleFaultList("Hardware", msg)
            break
            
        case 6: // ActiveRadioFaults (0x0006)
            handleFaultList("Radio", msg)
            break
            
        case 7: // ActiveNetworkFaults (0x0007)
            handleFaultList("Network", msg)
            break
            
        default:
            logDebug "General Diagnostics attr ${attrId}: ${value}"
            break
    }
}

private void handleSoftwareDiagnostics(Integer ep, Integer attrId, Map msg) {
    def value = msg.value
    
    switch (attrId) {
        case 0: // ThreadMetrics (0x0000)
            handleThreadMetrics(msg)
            break
            
        case 1: // CurrentHeapFree (0x0001)
            Long heapFree = value as Long
            if (heapFree != null) {
                sendEvent(name: "heapFree", value: heapFree)
                logInfo "Current Heap Free: ${formatBytes(heapFree)}"
            }
            break
            
        case 2: // CurrentHeapUsed (0x0002)
            Long heapUsed = value as Long
            if (heapUsed != null) {
                sendEvent(name: "heapUsed", value: heapUsed)
                logInfo "Current Heap Used: ${formatBytes(heapUsed)}"
            }
            break
            
        case 3: // CurrentHeapHighWatermark (0x0003)
            Long watermark = value as Long
            if (watermark != null) {
                logInfo "Heap High Watermark: ${formatBytes(watermark)}"
            }
            break
            
        default:
            logDebug "Software Diagnostics attr ${attrId}: ${value}"
            break
    }
}

// Helper methods for decoding complex attributes

private void handleNeighborTable(Map msg) {
    // NeighborTable is an array of NeighborTableStruct
    def value = msg.value
    if (value instanceof List) {
        Integer count = value.size()
        sendEvent(name: "neighborCount", value: count)
        logInfo "Neighbor Table: ${count} neighbors"
        if (logEnable && count > 0) {
            value.eachWithIndex { neighbor, idx ->
                logDebug "  Neighbor ${idx + 1}: ${neighbor}"
            }
        }
    }
}

private void handleRouteTable(Map msg) {
    def value = msg.value
    if (value instanceof List) {
        Integer count = value.size()
        sendEvent(name: "routeTableSize", value: count)
        logInfo "Route Table: ${count} routes"
        if (logEnable && count > 0) {
            value.eachWithIndex { route, idx ->
                logDebug "  Route ${idx + 1}: ${route}"
            }
        }
    }
}

private void handleNetworkInterfaces(Map msg) {
    def value = msg.value
    if (value instanceof List) {
        logInfo "Network Interfaces: ${value.size()} interface(s)"
        StringBuilder sb = new StringBuilder()
        value.eachWithIndex { ifaceData, idx ->
            if (idx > 0) sb.append("; ")
            
            // Parse tag-value pairs: [[tag:0, value:Name], [tag:1, value:IsOperational], ...]
            Map iface = [:]
            if (ifaceData instanceof List) {
                ifaceData.each { item ->
                    if (item instanceof Map && item.containsKey('tag') && item.containsKey('value')) {
                        switch (item.tag) {
                            case 0: iface.Name = item.value; break
                            case 1: iface.IsOperational = item.value; break
                            case 7: iface.Type = item.value; break
                        }
                    }
                }
            }
            
            String name = iface.Name ?: "Unknown"
            Boolean operational = iface.IsOperational ?: false
            String type = decodeInterfaceType(iface.Type)
            sb.append("${name} (${type}, ${operational ? 'Up' : 'Down'})")
            logInfo "  Interface ${idx + 1}: ${name}, Type: ${type}, Operational: ${operational}"
        }
        sendEvent(name: "networkInterfaces", value: sb.toString())
    }
}

private void handleThreadMetrics(Map msg) {
    def value = msg.value
    if (value instanceof List) {
        logInfo "Thread Metrics: ${value.size()} thread(s)"
        value.eachWithIndex { threadData, idx ->
            // Parse tag-value pairs
            Map thread = [:]
            if (threadData instanceof List) {
                threadData.each { item ->
                    if (item instanceof Map && item.containsKey('tag') && item.containsKey('value')) {
                        switch (item.tag) {
                            case 0: thread.ID = item.value; break
                            case 1: thread.Name = item.value; break
                            case 2: thread.StackFreeCurrent = item.value; break
                            case 4: thread.StackSize = item.value; break
                        }
                    }
                }
            }
            
            String name = thread.Name ?: "Thread-${thread.ID ?: idx}"
            Integer stackFree = thread.StackFreeCurrent ?: 0
            Integer stackSize = thread.StackSize ?: 0
            logInfo "  ${name}: Stack ${formatBytes(stackFree)} free / ${formatBytes(stackSize)} total"
        }
    }
}

private void handleFaultList(String type, Map msg) {
    def value = msg.value
    if (value instanceof List) {
        if (value.isEmpty()) {
            logInfo "Active ${type} Faults: None"
        } else {
            logInfo "Active ${type} Faults: ${value}"
        }
    }
}

private void handlePowerSource(Integer ep, Integer attrId, Map msg) {
    // Power Source cluster (0x002F)
    if (attrId == 12 || attrId == 0x000C) { // BatteryPercentRemaining
        Integer raw = msg.value as Integer
        if (raw != null) {
            Integer pct = Math.max(0, Math.min(100, raw / 2))
            sendEvent(name: "battery", value: pct, unit: "%")
            logInfo "Battery: ${pct}%"
        }
    }
}

private void handleIlluminance(Integer ep, Integer attrId, Map msg) {
    // Illuminance Measurement cluster (0x0400)
    if (attrId == 0) { // MeasuredValue
        Integer raw = msg.value as Integer
        if (raw != null) {
            Integer lux = Math.max(0, raw / 100)
            sendEvent(name: "illuminance", value: lux, unit: "lx")
            logInfo "Illuminance: ${lux} lx"
        }
    }
}

private void handleOccupancy(Integer ep, Integer attrId, Map msg) {
    // Occupancy Sensing cluster (0x0406)
    if (attrId == 0) { // Occupancy
        Integer raw = msg.value as Integer
        if (raw != null) {
            String motion = ((raw & 0x01) != 0) ? "active" : "inactive"
            sendEvent(name: "motion", value: motion)
            logInfo "Motion: ${motion}"
        }
    }
}

// Decoding helpers

private String decodeRoutingRole(Integer roleVal) {
    if (roleVal == null) return "Unknown"
    switch (roleVal) {
        case 0: return "Unspecified"
        case 1: return "Unassigned"
        case 2: return "SleepyEndDevice"
        case 3: return "EndDevice"
        case 4: return "REED"
        case 5: return "Router"
        case 6: return "Leader"
        default: return "Unknown (${roleVal})"
    }
}

private String decodeBootReason(Integer reason) {
    if (reason == null) return "Unknown"
    switch (reason) {
        case 0: return "Unspecified"
        case 1: return "PowerOnReboot"
        case 2: return "BrownOutReset"
        case 3: return "SoftwareWatchdogReset"
        case 4: return "HardwareWatchdogReset"
        case 5: return "SoftwareUpdateCompleted"
        case 6: return "SoftwareReset"
        default: return "Unknown (${reason})"
    }
}

private String decodeInterfaceType(Integer type) {
    if (type == null) return "Unknown"
    switch (type) {
        case 0: return "Unspecified"
        case 1: return "WiFi"
        case 2: return "Ethernet"
        case 3: return "Cellular"
        case 4: return "Thread"
        default: return "Unknown (${type})"
    }
}

private String decodeString(def value) {
    // Matter strings might come as hex or already decoded
    if (value == null) return null
    if (value instanceof String) {
        // If it looks like hex, try to decode
        if (value.matches(/^[0-9A-Fa-f]+$/)) {
            try {
                byte[] bytes = value.decodeHex()
                return new String(bytes, "UTF-8")
            } catch (Exception e) {
                return value
            }
        }
        return value
    }
    return value.toString()
}

private String formatUptime(Long seconds) {
    if (seconds == null) return "Unknown"
    Long days = seconds / 86400
    Long hours = (seconds % 86400) / 3600
    Long mins = (seconds % 3600) / 60
    Long secs = seconds % 60
    return "${days}d ${hours}h ${mins}m ${secs}s"
}

private String formatBytes(Long bytes) {
    if (bytes == null) return "0 B"
    if (bytes < 1024) return "${bytes} B"
    if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024))
    return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

// Safe conversion helpers

private Integer safeHexToInt(Object hex) {
    if (hex == null) return null
    String s = hex.toString().trim()
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
    if (s == "") return null
    try { 
        return Integer.parseUnsignedInt(s, 16) 
    } catch (Exception e) { 
        logDebug "Failed to parse hex to int: ${hex} - ${e.message}"
        return null 
    }
}

private Long safeLongHexToInt(Object hex) {
    if (hex == null) return null
    String s = hex.toString().trim()
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
    if (s == "") return null
    try { 
        return Long.parseUnsignedLong(s, 16) 
    } catch (Exception e) { 
        logDebug "Failed to parse hex to long: ${hex} - ${e.message}"
        return null 
    }
}

// Logging helpers

private void logDebug(String msg) {
    if (logEnable) log.debug "${device.displayName} ${msg}"
}

private void logInfo(String msg) {
    if (txtEnable) log.info "${device.displayName} ${msg}"
}

private void logWarn(String msg) {
    log.warn "${device.displayName} ${msg}"
}

