library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Common helpers",
    name: "common1",
    namespace: "drozovyk"   
)

import groovy.transform.CompileStatic
import groovy.transform.Field

@Field static Map logLevels = [
   (0): "Errors/warnings only",
   (1): "Info",
   (2): "Debug"
]
@Field static int logLevelMinimal = 0
@Field static int logLevelInfo    = 1
@Field static int logLevelDebug   = 2

private inputLogLevel() {
    input(name: "logLevel",
          type: "enum",
          title:"Logging level",
          options: logLevels,
          defaultValue: logLevelMinimal,
          required: false,
          displayDuringSetup: false)
}

private logError(String text) {
    log.error(text)
}

private logWarn(String text) {
    log.warn(text)
}

private logInfo(String text) {
    if(logLevel && (logLevel as int) > logLevelMinimal) {
        log.info(text)
    }
}

private logDebug(String text) {
    if(logLevel && (logLevel as int) == logLevelDebug) {
        log.debug(text)
    }
}

// Type casting child device to driver
private def getDriver(device) {
    if(null != device) {
        return getChildDevice(device.getDeviceNetworkId())
    }
    else {
        return null
    }
}

// Internal event propagation (mainly for compatibility with hubitat component* devices)
// It is highly recommended to keep it unmodified (to keep consistency with other component devices)
void parse(List events) {
    events.each {
        Map event = it as Map
        if (event.descriptionText) {
            logInfo(event.descriptionText as String)            
        }
        
        sendEvent(event)
    }
}

void parse(List events, com.hubitat.app.DeviceWrapper endPointDevice) {
    endPointDevice?.parse(events)
    endPointDevice?.getParent()?.componentParse(endPointDevice, events)
}

void parse(List events, endPointDevice) {
    endPointDevice?.parse(events)
    endPointDevice?.getParent()?.componentParse(endPointDevice, events)
}

void parse(List events, Short ep) {
    def endPointDevice = getEndpointDevice(ep)
    
    if(endPointDevice != null) {
        parse(events, endPointDevice)
    }
    else {
        logWarn("Unexpected endpoint (${ep}): ${events}")
    }
}

// to workaround 'schedule' CC parser mainly
private def getProperties(String description) {
    def commandInfo = description.split(',').collectEntries { entry -> 
        def pair = entry.split(':')
        [(pair.first().trim()) : pair.last().trim()]
    }

    commandInfo.command = hubitat.helper.HexUtils.hexStringToByteArray(commandInfo.command)
    commandInfo.payload = hubitat.helper.HexUtils.hexStringToIntArray(commandInfo.payload)
   
    return commandInfo
}

@CompileStatic
static String styleText(String text, String style = "") {
    return "<span style='${style}'>${text}</span>"
}

// Undefined (not implemented)
@CompileStatic
void zwaveEvent(hubitat.zwave.Command cmd, Short ep = 0) {
    logWarn("skip: endpoint ${ep} - command ${cmd}")
}
