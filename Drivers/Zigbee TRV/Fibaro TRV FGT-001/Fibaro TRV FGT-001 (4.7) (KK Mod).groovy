/*
    https://manuals.fibaro.com/content/manuals/en/FGT-001/FGT-001-EN-T-v1.0.pdf

    Endpoint 1: 
                Description: represents thermostatic head, allows to set temperature, schedules and check its battery level.

                Generic Device Class: GENERIC_TYPE_THERMOSTAT
                Specific Device Class: SPECIFIC_TYPE_THERMOSTAT_GENERAL_V2

    Endpoint 2:
                Description: represents dedicated temperature sensor, allows to check temperature and its battery level (if 
                not paired reported temperature and battery level are always 0).

                Generic Device Class: GENERIC_TYPE_SENSOR_MULTILEVEL
                Specific Device Class: SPECIFIC_TYPE_ROUTING_SENSOR_MULTILEVEL

    Response to Basic Command Class:
                Value         Action
                0             Set OFF mode (unfreeze function)
                99            Set HEAT mode (last set temperature)
                255           Set MANUFACTURER SPECIFIC mode (valve fully opened)

    Association Command Class:
                The device supports only “Lifeline” association group that reports the device status and allows for assigning 
                single device only (main controller by default).


Command Class                         Version Secure

Supported Command Classes:
    APPLICATION_STATUS         [0x22] V1     -
    ASSOCIATION                [0x85] V2 YES -
    ASSOCIATION_GRP_INFO       [0x59] V2 YES -
    BASIC                      [0x20] V1 YES *
    BATTERY                    [0x80] V1 YES *
    CLOCK                      [0x81] V1 YES *
    CONFIGURATION              [0x70] V1 YES *
    CRC_16_ENCAP               [0x56] V1
    DEVICE_RESET_LOCALLY       [0x5A] V1 YES -
    FIRMWARE_UPDATE_MD         [0x7A] V4     -
    MANUFACTURER_SPECIFIC      [0x72] V2 YES -
    MULTI_CHANNEL              [0x60] V4 YES *
    MULTI_CHANNEL_ASSOCIATION  [0x8E] V3 YES
    NOTIFICATION               [0x71] V8 YES *
    POWERLEVEL                 [0x73] V1 YES -
    PROTECTION                 [0x75] V1 YES -
    SCHEDULE                   [0x53] V1 YES
    SECURITY                   [0x98] V1     *
    SECURITY_2                 [0x9F] V1     -
    SENSOR_MULTILEVEL          [0x31] V5 YES
    SUPERVISION                [0x6C] V1 YES *
    THERMOSTAT_MODE            [0x40] V3 YES *
    THERMOSTAT_SETPOINT        [0x43] V3 YES *
    TRANSPORT_SERVICE          [0x55] V2     -
    VERSION                    [0x86] V2 YES -
    ZWAVEPLUS_INFO             [0x5E] V2     -

Multichannel Command Class:
Endpoint 1
    ASSOCIATION                [0x85] V2 YES -
    ASSOCIATION_GRP_INFO       [0x59] V2 YES -
    BASIC                      [0x20] V1 YES *
    BATTERY                    [0x80] V1 YES *
    CLOCK                      [0x81] V1 YES *
    MULTI_CHANNEL_ASSOCIATION  [0x8E] V3 YES
    NOTIFICATION               [0x71] V8 YES *
    PROTECTION                 [0x75] V1 YES -
    SCHEDULE                   [0x53] V1 YES
    SECURITY                   [0x98] V1     *
    SECURITY_2                 [0x9F] V1     -
    SUPERVISION                [0x6C] V1 YES *
    THERMOSTAT_MODE            [0x40] V3 YES *
    THERMOSTAT_SETPOINT        [0x43] V3 YES *    
    ZWAVEPLUS_INFO             [0x5E] V2     -
Endpoint 2
    ASSOCIATION                [0x85] V2 YES -
    ASSOCIATION_GRP_INFO       [0x59] V2 YES -
    BATTERY                    [0x80] V1 YES *
    MULTI_CHANNEL_ASSOCIATION  [0x8E] V3 YES
    NOTIFICATION               [0x71] V8 YES *
    SECURITY                   [0x98] V1     *
    SECURITY_2                 [0x9F] V1     -
    SENSOR_MULTILEVEL          [0x31] V5 YES *  
    SUPERVISION                [0x6C] V1 YES *    
    ZWAVEPLUS_INFO             [0x5E] V2     -

Notification Command Class: 
    The device uses Notification Command Class to report different events to the controller (“Lifeline” group).
Endpoint 1:
Notification Type             Event                                 Event Parameters
Power Management [0x08]       Charge battery soon [0x0E]
                              Charge battery now! [0x0F]
                              Battery is charging [0x0C]
                              Battery is fully charged [0x0D]
System [0x09]                 System Hardware Failure [0x03]        External sensor remove [0x02]
                                                                    Motor error [0x03]
                                                                    Calibration error [0x04]
Endpoint 2:
Notification Type             Event                                 Event Parameters
Power Management [0x08]       Replace battery soon [0x0A]
                              Replace battery now! [0x0B]

Parameter 1: Override Schedule duration
        This parameter determines duration of Override Schedule after turning the knob while normal schedule is active (set by Schedule CC).
        Size: 4 Byte, Default Value: 240 (4h)

        Setting	Description
            10 - 10000	(in minutes)

Parameter 2: Additional functions
        This parameter allows to enable different additional functions of thedevice.
        Size: 4 Byte, Default Value: 1

        Setting	Description
            1   open window detection (normal)
            2   open window detection (rapid)
            4	increase receiver sensitivity (shortens battery life)
            8	enabled LED indications when controlling remotely
            16	protect from setting Full ON and Full OFF mode by turning the knob manually
            32	device mounted in vertical position
            64	change regulator behaviour from Rapid to Moderate
            128 inverted knob operation
            256 heating medium demand reports
            512 detecting heating system failures

Parameter 3: Additional functions status (READ-ONLY)
        This parameter allows to check statuses of different additional functions.
        Size: 4 Byte, Default Value: 0

        Setting	Description
            1	optional temperature sensor connected and operational
            2	open window detected
            4   provide heat in order to maintain set temperature
            8   malfunctioning heating system (cannot reach set temperature)
*/
/*
	ver. 4.0.7 	05/11/25 drozovyk - original version by Dmytro Rozovyk
	ver. 4.1.0  10/20/25 kkossev  - (dev. branch) added HealthCheck capability and healthStatus attribute and functionality; added ping() command and ''rtt'' attribute to report round trip time;
                                    fixed setThermostatMode(); refresh() and timedPoll() force update events of heatingSetpoint, thermostatMode, and thermostatOperatingState;
                                    initialize supportedThermostatModes and supportedThermostatFanModes as proper JSON objects using JsonOutput.toJson()
                                    added capability TemperatureMeasurement and coolingSetpoint (needed for HomeKit integration - NOT working yet)

*/

#include drozovyk.association1
#include drozovyk.clock1
#include drozovyk.common1
#include drozovyk.encapsulation1
#include drozovyk.notification1
#include drozovyk.protection1
//#include drozovyk.sensor1
#include drozovyk.sensor1_KK_Mod
#include drozovyk.version1
#include drozovyk.zwave1

import groovy.transform.Field
import groovy.json.JsonOutput

@Field static Map          commandClassVersions =     [0x53: 1, 0x75: 1] // to fix 'protection' incorrect report version
@Field static List<String> param2windowOptions =      ["Disabled","Normal","Rapid"]
@Field static List<String> param2behaviorOptions =    ["Rapid","Moderate"]
@Field static List<String> paramPollIntervalOptions = ["10 minutes",    "15 minutes",    "20 minutes",    "30 minutes",    "1 hour",     "2 hours",      "3 hours",      "4 hours",      "6 hours",      "8 hours",      "12 hours",      "1 day"]
@Field static List<String> paramPollIntervalCronExp = ["0 0/10 * * * ?","0 0/15 * * * ?","0 0/20 * * * ?","0 0/30 * * * ?","0 0 * * * ?","0 0 0/2 * * ?","0 0 0/3 * * ?","0 0 0/4 * * ?","0 0 0/6 * * ?","0 0 0/8 * * ?","0 0 0/12 * * ?","0 0 0 * * ?"]

// HealthCheck constants
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240, options: [10: 'Every 10 minutes', 30: 'Every 30 minutes', 60: 'Every hour', 240: 'Every 4 hours', 720: 'Every 12 hours', 1440: 'Every 24 hours']
]

metadata {
    definition (name: "Fibaro TRV FGT-001 (4.7) (KK Mod)", namespace: "drozovyk", author: "Dmytro Rozovyk") {
        capability("Actuator")
        capability("Configuration")
        capability("Initialize")                 // add 'initialize' command
        capability("Refresh")
        capability("Thermostat")                 // attribute coolingSetpoint - NUMBER, unit:°F || °C
                                                 // attribute heatingSetpoint - NUMBER, unit:°F || °C
                                                 // attribute schedule - JSON_OBJECT (Deprecated)
                                                 // attribute supportedThermostatFanModes - JSON_OBJECT
                                                 // attribute supportedThermostatModes - JSON_OBJECT
                                                 // attribute temperature - NUMBER, unit:°F || °C
                                                 // attribute thermostatFanMode - ENUM ["on", "circulate", "auto"]
                                                 // attribute thermostatMode - ENUM ["auto", "off", "heat", "emergency heat", "cool"]
                                                 // attribute thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]
                                                 // attribute thermostatSetpoint - NUMBER, unit:°F || °C
        capability("TemperatureMeasurement")     // attribute temperature - NUMBER, unit:°F || °C
        capability('HealthCheck')                // attribute healthStatus - ENUM ["unknown", "online", "offline"]
                                                 // ping() command
        command("knobLock")
        command("knobUnlock")
        command("updatePreferencesFromDevice")
        command("updateVersionInfo")
        command("validateClockSettings")
        
        command("zwaveGetSchedule")
        command("zwaveRemoveSchedule")
        command("zwaveSetSchedule")
        
        attribute("alarmCannotReachTemp",     "STRING") // alarm, idle
        attribute("alarmHardwareMalfunction", "STRING") // alarm, idle        
        attribute("alarmWindowOpened",        "STRING") // alarm, idle
        attribute("protectionLocal",          "STRING") // inactive, active
        attribute("zSchedules",               "STRING") // <html table>
        attribute('rtt', 'number')                      // round trip time in ms
        attribute('healthStatus', 'enum', ['unknown', 'offline', 'online'])  // health status

        fingerprint mfr:"010F", prod:"1301", deviceId:"1001", inClusters:"0x5E,0x86,0x8E,0x31,0x40,0x43,0x53,0x98,0x9F,0x55,0x56,0x59,0x5A,0x60,0x6C,0x71,0x72,0x75,0x80,0x70,0x81,0x7A,0x73,0x22,0x85", controllerType: "ZWV"
    }
    preferences {
        input(name: "paramOpenValveWhenOff", type: "bool", title:"<b style='color:red;'>Open valve when off</b>", description: "Overrides 'anti-freeze' function; HUB-side only option",
              defaultValue: false, required: false, displayDuringSetup: false)
        
        input(name: "paramUseSchedule", type: "bool", title:"<b style='color:peru;'>Enable manual override</b>", description: "Send commands using schedule CC instead of direct control.",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "paramScheduleStartTime", type: "time", title:"<b style='color:peru;'>Schedule start time</b>", description: "To avoid midnight noise when using schedules :)",
              defaultValue: "12:00", required: false, displayDuringSetup: false)
        input(name: "param01", type: "number", title: "<b style='color:peru;'>Schedule override duration (min)</b>", description: "Determines duration of 'Override Schedule' after turning the knob while normal schedule is active", 
              range: "10..10000", defaultValue: "240", required: false, displayDuringSetup: false)
        
        input(name: "paramPollBattery", type: "bool", title:"<b style='color:slateblue;'>Timed battery polling</b>", description: "Shortens battery life; HUB-side only option",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "paramPollTemperature", type: "bool", title:"<b style='color:slateblue;'>Timed temperature polling</b>", description: "Polls temperature, thermostat mode, setpoint, and operating state. Shortens battery life; HUB-side only option",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "paramPollInterval", type: "enum", title:"<b style='color:slateblue;'>Timed polling interval</b>", description: "Shortens battery life; HUB-side only option",
              options: paramPollIntervalOptions, defaultValue: paramPollIntervalOptions[0], required: false, displayDuringSetup: false)
        
        input(name: "param02window", type: "enum",   title:"<b style='color:green;'>Open window detection</b>", description: "",
              options: param2windowOptions, defaultValue: param2windowOptions[1], required: false, displayDuringSetup: false)
        input(name: "param02sensetivityBoost", type: "bool", title:"<b style='color:green;'>Reciever sensetivity boost</b>", description: "Shortens battery life",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "param02led", type: "bool", title:"<b style='color:green;'>Remote control LED indication</b>", description: "",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "param02noFullOpenClose", type: "bool", title:"<b style='color:green;'>Forbid full open/close states</b>", description: "When turning the knob manually",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "param02vert", type: "bool", title:"<b style='color:green;'>Mounted vertically</b>", description: "",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "param02behavior", type: "enum", title:"<b style='color:green;'>Regulator behavior</b>", description: "",
              options: param2behaviorOptions, defaultValue: param2behaviorOptions[0], required: false, displayDuringSetup: false)
        input(name: "param02invert", type: "bool", title:"<b style='color:green;'>Invert knob operation</b>", description: "",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "param02hmdr", type: "bool", title:"<b style='color:green;'>Heating medium demand reports</b>", description: "",
              defaultValue: false, required: false, displayDuringSetup: false)
        input(name: "param02detectFailures", type: "bool", title:"<b style='color:green;'>Detect heating failures</b>", description: "",
              defaultValue: false, required: false, displayDuringSetup: false)
        
        // HealthCheck preferences
        input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', 
              options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, 
              required: true, description: 'Method to check device online/offline status.'
        input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', 
              options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, 
              required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"'
        
        inputLogLevel()
    }
}

String getBatteryDeviceNetworkId() {
    return "${device.deviceNetworkId}-battery"
}

String getSensorDeviceNetworkId() {
    return "${device.deviceNetworkId}-sensor"
}

def getBatteryDevice() {
    batteryDevice = getChildDevice(getBatteryDeviceNetworkId())
    if(null == batteryDevice) {
        batteryDevice = addChildDevice("drozovyk", "Virtual Battery Powered Device", getBatteryDeviceNetworkId(),  [isComponent: true, name: "${device.name}-battery", label: "${device.label} battery"])
    }
    return batteryDevice
}

def getSensorDevice() {
    return getChildDevice(getSensorDeviceNetworkId())
}

def getEndpointDevice(Short ep = 0) {
    // Sensor driver is a battery-only device; it has not temperature
    // Moreover only a single temperature value is reported by TRV (internal or external depending on configuration but not both at a time)
    return this
}

// ================  Parameters  =====================
void removeStateHint() {
    state.remove("hint")
}

void knobLock() {
    def commandList = []
    commandList << setProtectionV1String(true)
    commandList << getProtectionReportV1String()
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commandList, 3000), hubitat.device.Protocol.ZWAVE))
}

void knobUnlock() {
    def commandList = []
    commandList << setProtectionV1String(false)
    commandList << getProtectionReportV1String()
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commandList, 3000), hubitat.device.Protocol.ZWAVE))
}

//=============== Z-Wave common =================

// Basic V1
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, Short ep = 0) {
    logDebug("(${ep}) Basic V1 report: ${cmd}")
    
    // Set device online when any activity is detected
    setHealthStatusOnline()        
    
    if(cmd.value == 0) {
        parse([[name:"thermostatMode", value: "off"]])
    } else if(cmd.value == 99) {
        parse([[name:"thermostatMode", value: "heat"]])
    } else if(cmd.value == 255) {
        parse([[name:"thermostatMode", value: "off"]])
    }
}

String getBasicReportString() {
    return encapsulate(zwave.basicV1.basicGet())
}

List<String> setBasicStringList(Short state) {
    def cmds = []    
    cmds << encapsulate(zwave.basicV1.basicSet(value: state))
    cmds << getBasicReportString()
    cmds << getParameterReport(3) // get operating state    
    return cmds
}

//================= Z-Wave device-specific ===========================

// Application status
//    see 'encapsulation1'

// Association grp info
//    Not used atm  

// Association V2
//    see 'association1'

// Battery V1
void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd, Short ep = 0) {
    logDebug("(${ep}) Battery V1 report: ${cmd}")
    
    // Set device online when any activity is detected
    setHealthStatusOnline()
    
    // Handle RTT measurement if this was triggered by ping()
    if (state.cmdSentTime && ep == 0) {
        sendRTTevent()
        state.remove('cmdSentTime')
    }
    
    int batteryLevel = cmd.batteryLevel as int;
    
    if(2 > ep) { // '0' or '1'
        batteryDevice = getBatteryDevice()
        if(batteryLevel <= 100) {
            batteryDevice?.parse([[name:"battery", value: "${cmd.batteryLevel}", unit: "%"]])
        }
        else if (batteryLevel == 255) {
            batteryDevice?.parse([[name:"batteryState", value: "empty"]])
        }
    }
    else if(2 == ep) {
        sensorDevice = getSensorDevice()
        if(batteryLevel <= 100) {
            sensorDevice?.parse([[name:"battery", value: "${cmd.batteryLevel}", unit: "%"]])
        }
        else if (batteryLevel == 255) {
            sensorDevice?.parse([[name:"batteryState", value: "empty"]])
        }
    }
}

List<String> getBatteryReportStringList() {
    def commandList = [encapsulate(zwave.batteryV1.batteryGet(), 1)]
    if(null != getSensorDevice()) {
        commandList << encapsulate(zwave.batteryV1.batteryGet(), 2)
    }
    return commandList
}

// Clock
//    see 'clock1'

// Configuration V1
void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd, Short ep = 0) {
    logDebug("(${ep}) Configuration V1 report: ${cmd}")
    
    // Set device online when any activity is detected
    setHealthStatusOnline()        
    
    if(cmd.parameterNumber == 1) {
        device.updateSetting("param${cmd.parameterNumber}" as String, [type: "number", value: cmd.scaledConfigurationValue])
        state["parameter${cmd.parameterNumber}"] = [desc:"<b style='color:peru;'>Shedule override duration (min)</b>", value: cmd.scaledConfigurationValue]
    } else if(cmd.parameterNumber == 2) {
        def int openWindowDetection             = cmd.scaledConfigurationValue & 3
        def boolean boostRecieverSensitivity    = (cmd.scaledConfigurationValue & 4) > 0
        def boolean indicateRemoteCommands      = (cmd.scaledConfigurationValue & 8) > 0
        def boolean forbidFullOpenClose         = (cmd.scaledConfigurationValue & 16) > 0
        def boolean mountedVertically           = (cmd.scaledConfigurationValue & 32) > 0
        def boolean moderateBehavior            = (cmd.scaledConfigurationValue & 64) > 0
        def boolean invertedKnobOperation       = (cmd.scaledConfigurationValue & 128) > 0
        def boolean heatingMediumDemandReports  = (cmd.scaledConfigurationValue & 256) > 0
        def boolean detectHeatingFailures       = (cmd.scaledConfigurationValue & 512) > 0
        
        
        device.updateSetting("param02window"           as String, [type: "enum", value: param2windowOptions[openWindowDetection]])
        device.updateSetting("param02sensetivityBoost" as String, [type: "bool", value: (boostRecieverSensitivity ? "true" : "false")])
        device.updateSetting("param02led"              as String, [type: "bool", value: (indicateRemoteCommands ? "true" : "false")])
        device.updateSetting("param02noFullOpenClose"  as String, [type: "bool", value: (forbidFullOpenClose ? "true" : "false")])
        device.updateSetting("param02vert"             as String, [type: "bool", value: (mountedVertically ? "true" : "false")])
        device.updateSetting("param02behavior"         as String, [type: "enum", value: param2behaviorOptions[moderateBehavior ? 1 : 0]])
        device.updateSetting("param02invert"           as String, [type: "bool", value: (invertedKnobOperation ? "true" : "false")])
        device.updateSetting("param02hmdr"             as String, [type: "bool", value: (heatingMediumDemandReports ? "true" : "false")])
        device.updateSetting("param02detectFailures"   as String, [type: "bool", value: (detectHeatingFailures ? "true" : "false")])
        
        state["parameter${cmd.parameterNumber}"] = [desc:"<b style='color:green;'>" \
                                                    + "Open window detection: ${param2windowOptions[openWindowDetection]}, " \
                                                    + "Boost receiver sensetivity: ${boostRecieverSensitivity?"yes":"no"}, " \
                                                    + "Remote control LED indication: ${indicateRemoteCommands?"yes":"no"}, " \
                                                    + "Full open/close states forbidden: ${forbidFullOpenClose?"yes":"no"}, " \
                                                    + "Device mounted: ${mountedVertically?"vertically":"horizontally"}, " \
                                                    + "Regulator behavior: ${param2behaviorOptions[moderateBehavior ? 1 : 0]}, " \
                                                    + "Knob operation inverted: ${invertedKnobOperation?"yes":"no"}, " \
                                                    + "Heating medium demand reports: ${heatingMediumDemandReports?"yes":"no"}, " \
                                                    + "Detect heating failures: ${detectHeatingFailures?"yes":"no"}</b>",
                                                    value: cmd.scaledConfigurationValue]
    } else if(cmd.parameterNumber == 3) {
        def boolean sensor   = (cmd.scaledConfigurationValue & 1) > 0
        def boolean window    = (cmd.scaledConfigurationValue & 2) > 0
        def boolean heat = (cmd.scaledConfigurationValue & 4) > 0
        def boolean malfunction  = (cmd.scaledConfigurationValue & 8) > 0  
        state["parameter${cmd.parameterNumber}"] = [desc:"<b>External sensor paired: ${sensor?"yes":"no"}</b>", value: cmd.scaledConfigurationValue]
        
        // Force events if within refresh window
        def forceEvent = isWithinRefreshWindow()
        def refreshTag = forceEvent ? " [refresh]" : ""
        
        sensorDevice = getSensorDevice()
        if(null == sensorDevice && sensor) {
            sensorDevice = addChildDevice("drozovyk", "Virtual Battery Powered Device", getSensorDeviceNetworkId(),  [isComponent: true, name: "${device.name}-sensor", label: "${device.label} sensor"])
        }
        if(null != sensorDevice && !sensor) {            
            deleteChildDevice(getSensorDeviceNetworkId())
            sensorDevice = null
        }
        
        parse([        
            [name:"thermostatOperatingState", value: heat ? "heating" : "idle", isStateChange: forceEvent, descriptionText: "thermostatOperatingState is ${heat ? 'heating' : 'idle'}${refreshTag}"],
            [name:"alarmCannotReachTemp", value: malfunction ? "alarm" : "idle"],
            [name:"alarmWindowOpened", value: window ? "alarm" : "idle"]
        ])
    } else {        
        state["parameter${cmd.parameterNumber}"] = [value: cmd.scaledConfigurationValue]
    }
}

// used above; added to command list
String setParameter(paramId, size, value) {
    return encapsulate(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: paramId, size: size))
}

String setParameter1() {
    def String cmd = ""
    if(param01 != null) { 
        def Number value = param01 as Number
        
        if(state?.parameter1?.value != value) {
            cmd = setParameter( 1,  4,  value)
        }
    }    
    return cmd
}

String setParameter2() {
    def String cmd = ""
    if(param02window != null &&  param02sensetivityBoost != null && 
       param02led != null &&     param02noFullOpenClose != null && 
       param02vert != null &&    param02behavior != null && 
       param02invert != null &&  param02hmdr != null && 
       param02detectFailures != null) {
       
        def Number value = param2windowOptions.indexOf(param02window) + // 0, 1, 2        
                           ((param02sensetivityBoost as boolean) ? 4 : 0) +
                           ((param02led as boolean) ? 8 : 0) +
                           ((param02noFullOpenClose as boolean) ? 16 : 0) +
                           ((param02vert as boolean) ? 32 : 0) +
                           param2behaviorOptions.indexOf(param02behavior) * 64 + // (0, 1) * 64
                           ((param02invert as boolean) ? 128 : 0) +
                           ((param02hmdr as boolean) ? 256 : 0) +
                           ((param02detectFailures as boolean) ? 512 : 0)
        
        if(state?.parameter2?.value != value) {
            cmd = setParameter( 2,  4,  value)
        }
    }    
    
    return cmd
}

String getParameterReport(paramId) {
    return encapsulate(zwave.configurationV1.configurationGet(parameterNumber: paramId))    
}

// Device reset locally
//     not used atm

// Firmware update md V4
//     not used atm

// Manufacturer specific V2
//     not used atm

// Multi channel V4
//    see 'encapsulation1' library

// Notification V8

// NOTIFICATION_TYPE_POWER_MANAGEMENT
//    State idle            0x00
//    Sensor bat is low     0x0A
//    Sensor bat is empty   0x0B
//    Battery is charging   0x0C
//    Battery is full       0x0D
//    Battery is low        0x0E
//    Battery is empty      0x0F
// NOTIFICATION_TYPE_SYSTEM
//     State idle                                                                   0x00
//     System hardware failure (manufacturer proprietary failure code provided)	    0x03
//            External sensor error                                                    0x02
//            Motor error                                                              0x03
//            Calibration error                                                        0x04
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd, Short ep = 0) {
    zwaveNotificationEvent("V8", cmd, ep)
    
    // Set device online when any activity is detected
    setHealthStatusOnline()
    
    if(cmd.notificationType == cmd.NOTIFICATION_TYPE_POWER_MANAGEMENT) {
        if(cmd.event == 0x00) {
            batteryDevice = getBatteryDevice()
            batteryDevice?.parse([
                [name:"batteryState", value: "discharging"],
                [name:"powerSource", value: "battery"]
            ])
            sensorDevice = getSensorDevice()
            sensorDevice?.parse([[name:"batteryState", value: "discharging"]])
        } else if(cmd.event == 0x0A) {
            sensorDevice = getSensorDevice()
            sensorDevice?.parse([[name:"batteryState", value: "low"]])
        } else if(cmd.event == 0x0B) {
            sensorDevice = getSensorDevice()
            sensorDevice?.parse([[name:"batteryState", value: "empty"]])
        } else if(cmd.event == 0x0C) {
            batteryDevice = getBatteryDevice()
            batteryDevice?.parse([
                [name:"batteryState", value: "charging"],
                [name:"powerSource", value: "dc"]
            ])
        } else if(cmd.event == 0x0D) {
            batteryDevice = getBatteryDevice()
            batteryDevice?.parse([
                [name:"batteryState", value: "idle"],
                [name:"powerSource", value: "dc"]
            ])
        } else if(cmd.event == 0x0E) {
            batteryDevice = getBatteryDevice()
            batteryDevice?.parse([
                [name:"batteryState", value: "low"],
                [name:"powerSource", value: "battery"]
            ])
        } else if(cmd.event == 0x0F) {
            batteryDevice = getBatteryDevice()
            batteryDevice?.parse([
                [name:"batteryState", value: "empty"],
                [name:"powerSource", value: "battery"]
            ])
        }
    } else if (cmd.notificationType == cmd.NOTIFICATION_TYPE_SYSTEM) {
        if(cmd.event == 0x00) {
            parse([[name:"alarmHardwareMalfunction", value: "idle"]])
        }
        else if(cmd.event == 0x03) {            
            logWarn "System: harware failure ${cmd}"
            if(cmd.eventParametersLength > 0){
                if(cmd.eventParameter[0] == 0x02) {
                    parse([[name:"alarmHardwareMalfunction", value: "sensor"]])
                }
                else if(cmd.eventParameter[0] == 0x03) {
                    parse([[name:"alarmHardwareMalfunction", value: "motor"]])
                }
                else if(cmd.eventParameter[0] == 0x04) {
                    parse([[name:"alarmHardwareMalfunction", value: "calibration"]])
                }
                else {
                    parse([[name:"alarmHardwareMalfunction", value: "alarm"]])
                }
            }
            else {
                parse([[name:"alarmHardwareMalfunction", value: "alarm"]])
            }
        }
    }
}

// Power level
//    not used atm (radio power level info)

// Protection V2
//    not used atm

// Security
//    see 'encapsulation1' library

// Security 2
//    not used atm

// Sensor multilevel V5
//    see 'sensor1'
List<String> getSensorMultilevelReportStringList() {
    return [
        // Main endpoint
        encapsulate(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 1, sensorType: hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelGet.SENSOR_TYPE_TEMPERATURE_VERSION_1))
        // Sensor endpoint
        //encapsulate(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 1, sensorType: hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelGet.SENSOR_TYPE_TEMPERATURE_VERSION_1), 2)
    ];
}

// Schedule
//    doesn't seem to work at all: support report is full of nulls
//    Manual states:
//        The device allows to create multiple heating schedules to manage temperature in the room throughout the week. Schedules are created via controller interface or app. Up to 253 normal schedules
//        can be created. The lower the schedule ID number, the higher the priority. Schedules with higher priority override those with lower priority in case of overlapping schedules. Schedules can be 
//        disabled without deleting it. Schedules allow to set target temperature for HEAT mode (using Thermostat Setpoint CC) and one of operating modes: HEAT, OFF or MANUFACTURER SPECIFIC (using Thermostat Mode CC)
//        Only SET commands are permitted.
//
//        To create normal schedules user must specify
//            • Day of the week,
//            • Starting time (hour and minute),
//            • Duration,
//            • Temperature Setpoint for HEAT mode in range 10-30°C (using Thermostat Setpoint CC)
//            • One of the operating modes (using Thermostat Mode CC):
//                » HEAT for setting temperature,
//                » OFF for valve fully closed,
//                » MANUFACTURER SPECIFIC for valve fully opened

//        Override Schedule is a special type schedule that has the highest priority; thus it overrides other schedules.
//        The Override Schedule starts right after setting and lasts for specified time, then it is removed and current schedule or normal operation is restored.
//        To create Override Schedule user must specify:
//            • Starting time (START NOW),
//            • Duration,
//            • Temperature Setpoint for HEAT mode in range 10-30°C (using Thermostat Setpoint CC)
//            • One of the operating modes (using Thermostat Mode CC):
//                » HEAT for setting temperature,
//                » OFF for valve fully closed,
//                » MANUFACTURER SPECIFIC for valve fully opened.
//        Override Mode can be enabled in two ways:
//            • By turning the knob, while normal schedule is active. The LED ring will pulse with selected adjustment.
//            • Via controller, by creating schedule with ID set 255, start time set to NOW and duration (in minutes/hours/days).
//        To exit Override Mode grab knob with your hand for 5 seconds
//    ToDo: try without relying on supported features report; try custom parse routine for this specific CC

// Supports 0x40 - Thermostat Mode (set only)
// Supports 0x43 - Thermostat Setpoint (set only)

void zwaveEvent(hubitat.zwave.commands.schedulev1.CommandScheduleReport cmd, payload, Short ep = 0) {
    logDebug("(${ep}) Schedule V1 command report: ${cmd}")        
    
    if(null == state.schedules) {
        state.schedules = [:]
    }
    
    def mode = "none"
    def temp = 0
    def num = cmd.numberOfCmdToFollow
    
    while(num > 0) {
        // manually parsing thermostat mode and setpoint CCs (cheap dirty way)
        if(payload[1] == 0x43) {
            // 43 01 01 01 10
            // 43 01 01 22 00 C8
            def size = payload[4] & 7
            def decp = (payload[4] >> 5) & 7
            for(index = 0; index < size; ++index) {
                temp = temp * 256 + payload[5 + index]
            }
            temp = temp / Math.pow(10, decp)
        }
        
        if(payload[1] == 0x40) {
            mode = (payload[3] == hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT) ? "heat" : "off"
        }
        
        payload = payload.drop(payload[0] + 1)
        --num
    }
    
    if(cmd.activeId > 0) {
        def status = ""
        if(cmd.activeId == 2) { status = "Inactive" }
        if(cmd.activeId == 3) { status = "Active" }
        if(cmd.activeId == 4) { status = "Disabled" }
        if(cmd.activeId == 5) { status = "Override" }
    
        def weekdays = ""
        weekdays += (cmd.startWeekday & 1) ? "M" : "m"            
        weekdays += (cmd.startWeekday & 2) ? "T" : "t"
        weekdays += (cmd.startWeekday & 4) ? "W" : "w"
        weekdays += (cmd.startWeekday & 8) ? "T" : "t"
        weekdays += (cmd.startWeekday & 16) ? "F" : "f"
        weekdays += (cmd.startWeekday & 32) ? "S" : "s"
        weekdays += (cmd.startWeekday & 64) ? "S" : "s"

        def hour = (cmd.startHour == 0x1F) ? "--" : "${cmd.startHour}".padLeft(2, "0")
        def minute = (cmd.startMinute == 0x3F) ? "--" : "${cmd.startMinute}".padLeft(2, "0")
        
        def duration = ""
        if(cmd.durationType == 0) { duration = "${cmd.durationByte}m"}
        if(cmd.durationType == 1) { duration = "${cmd.durationByte}h"}
        if(cmd.durationType == 2) { duration = "${cmd.durationByte}d"}

        state.schedules[cmd.scheduleId] = [state: status, weekdays: weekdays, time: "${hour}:${minute}", duration: duration, mode: mode, setpoint: temp]

        parse([
            [name: "zSchedules", value: printScheduleTable()]
        ])
    }
}

String printScheduleTable() {
    if(state?.schedules) {
        String table = "<p><table border='1'><tr><th>ID</th><th>State</th><th>Weekdays</th><th>Time</th><th>Duration</th><th>Mode</th><th>Temp</th></tr>"
        state.schedules.each({ key, value ->
            table += "<tr><td align='center'>${key}</td><td align='center'>${value.state}</td><td align='center'>${value.weekdays}</td><td align='center'>${value.time}</td><td align='center'>${value.duration}</td><td align='center'>${value.mode}</td><td align='center'>${value.setpoint}</td></tr>"
        })
        table += "</table>"
        
        return table
    }
    else {
        return "none"
    }    
}

void zwaveGetSchedule() {
    state.schedules = [:]
    parse([[name: "zSchedules", value: printScheduleTable()]])
    
    def commands = []
    commands << encapsulate(zwave.scheduleV1.commandScheduleGet(scheduleId: 1))
    commands << encapsulate(zwave.scheduleV1.commandScheduleGet(scheduleId: 255))
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commands, 4000), hubitat.device.Protocol.ZWAVE))
}

void zwaveRemoveSchedule() {
    state.schedules = [:]
    parse([[name: "zSchedules", value: printScheduleTable()]])
    
    def commands = []
    commands << encapsulate(zwave.scheduleV1.scheduleRemove(scheduleId: 1))
    commands << encapsulate(zwave.scheduleV1.commandScheduleGet(scheduleId: 1))
    commands << encapsulate(zwave.scheduleV1.commandScheduleGet(scheduleId: 255))
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commands, 4000), hubitat.device.Protocol.ZWAVE))
}

void zwaveSetSchedule() {
    state.schedules = [:]
    parse([[name: "zSchedules", value: printScheduleTable()]])
    
    def commands = []
    // 53 - class
    // 03 - command
    // 01 - schedule ID
    // 00 - reserved
    // FF - start year
    // 00 - start month
    // 00 - start day of month
    // 7F - start weekday mask
    // 4C - duration type and start hour [3 + 5 bits]: 0x00 | 0x1F // 0x00 (m), 0x20(h), 0x40(d)
    // 00 - start minute
    // FFFF - duration (MSB, LSB)
    // 00 - reports to follow
    // 02 - commands to follow
    // 05 - command length
    // XXXXXXXXXX
    // 03
    // XXXXXX

    def cmd4content0 = zwave.thermostatSetpointV2.thermostatSetpointSet(setpointType: hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointGet.SETPOINT_TYPE_HEATING_1, scaledValue: 16 as BigDecimal)
    def cmd4content1 = zwave.thermostatModeV2.thermostatModeSet(mode: hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF)
    String cmd4 = encapsulateString("53030100FF00007F4C0000010002" +
        "05" + cmd4content0.format() +
        "03" + cmd4content1.format()
    )
    commands << cmd4
    
    commands << encapsulate(zwave.scheduleV1.commandScheduleGet(scheduleId: 1))
    commands << encapsulate(zwave.scheduleV1.commandScheduleGet(scheduleId: 255))
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commands, 4000), hubitat.device.Protocol.ZWAVE))
}

// Supervision
//    see 'encapsulation1'

// Thermostat mode V3 (off:true, heat:true)
//    In fact while device accepts V3 commands it somehow replies with V2 report. So there is no point on using V3 command set
void zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd, Short ep = 0) {
    logDebug("(${ep}) Thermostat Mode V2 report: ${cmd}")
    
    // Set device online when any activity is detected
    setHealthStatusOnline()        
    
    // Force events if within refresh window
    def forceEvent = isWithinRefreshWindow()
    def refreshTag = forceEvent ? " [refresh]" : ""
    
    if(cmd.mode == hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF) {
        parse([[name:"thermostatMode", value: "off", isStateChange: forceEvent, descriptionText: "thermostatMode is off${refreshTag}"]])        
    } else if(cmd.mode == hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT) {
        parse([[name:"thermostatMode", value: "heat", isStateChange: forceEvent, descriptionText: "thermostatMode is heat${refreshTag}"]])
    } else if(cmd.mode == 31) {
        parse([[name:"thermostatMode", value: "off", isStateChange: forceEvent, descriptionText: "thermostatMode is off${refreshTag}"]])
    }
}

String getThermostatModeReportString() {
    // 31 - MANUFACTURER SPECIFIC - open valve
    return encapsulate(zwave.thermostatModeV2.thermostatModeGet())    
}

List<String> setThermostatModeStringList(Short state) {
    def cmds = []
    // approximating to 'basic' command class
    
    Short mode = 0
    if (state == 0) {
        mode = hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF
    } else if (state == 99) {
        mode = hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT
    } else if (state == 255) {
        mode = 31  // MANUFACTURER SPECIFIC - open valve
    }

    // don't allow unsupported modes; MODE_OFF equals '0' by definition
    if(state == 0 || mode > 0) {
        cmds << encapsulate(zwave.thermostatModeV2.thermostatModeSet(mode: mode))
        cmds << getThermostatModeReportString()
    }
    // ToDo: if it is going to be used, add report request here and operation state request. Fibaro TRV needs 2s to handle 'set' commands.
    
    return cmds
}

// Thermostat setpoint V3
//    In fact while device accepts V3 commands it somehow replies with V2 report. So there is no point on using V3 command set
//    Only heating setpoint is supported
void zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd, Short ep = 0) {
    logDebug("(${ep}) Thermostat Setpoint V2 report: ${cmd}")
    
    // Set device online when any activity is detected
    setHealthStatusOnline()        
    
    // Force events if within refresh window
    def forceEvent = isWithinRefreshWindow()
    def refreshTag = forceEvent ? " [refresh]" : ""
    
    parse([
        [name: "heatingSetpoint", value: "${cmd.scaledValue}", unit: "°C", isStateChange: forceEvent, descriptionText: "heatingSetpoint is ${cmd.scaledValue}°C${refreshTag}"],
        [name: "thermostatSetpoint", value: "${cmd.scaledValue}", unit: "°C", isStateChange: forceEvent, descriptionText: "thermostatSetpoint is ${cmd.scaledValue}°C${refreshTag}"]
    ])
}

String getThermostatSetpointReportString() {
    return encapsulate(zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointGet.SETPOINT_TYPE_HEATING_1))
}

List<String> setThermostatSetpointStringList(BigDecimal value) {
    def cmds = []
    
    // The actual TRV range is [10..30]
    BigDecimal temperature = value
    if(temperature < 10) {
        temperature = 10
    }
    if(temperature > 30) {
        temperature = 30
    }
    
    def setpointCmd = zwave.thermostatSetpointV2.thermostatSetpointSet(setpointType: hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointGet.SETPOINT_TYPE_HEATING_1, scaledValue: temperature)
    
    // Set heating setpoint. Has no effect when any schedule active. But will take affect after disabling the schedule usage
    cmds << encapsulate(setpointCmd)
    
    def boolean useSchedule = false;
    if(null != paramUseSchedule) { 
        useSchedule = paramUseSchedule as boolean
    } 
    
    if(useSchedule) {
        // cleanup state
        state.schedules = [:]
        
        // get schedule start time option
        // ToDo
        
        // get current mode
        // ToDo
        
        // add schedule set command
        def modeCmd = zwave.thermostatModeV2.thermostatModeSet(mode: hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF)
        cmds << encapsulateString("53030100FF00007F4C0000010002" + "05" + setpointCmd.format() + "03" + modeCmd.format())
        
        // update schedule table
        cmds << encapsulate(zwave.scheduleV1.commandScheduleGet(scheduleId: 1))
        cmds << encapsulate(zwave.scheduleV1.commandScheduleGet(scheduleId: 255))
    }
    
    cmds << getThermostatSetpointReportString()
    return cmds    
}

// Transport service V2

// Version V2
//    see 'version1'

void updateVersionInfo() {
    sendHubCommand(new hubitat.device.HubAction(getVersionReportCommand(), hubitat.device.Protocol.ZWAVE))
}

// Zwaveplus info V2
//    not used atm

void timedPoll() {
    // Set refresh tracking for timed polling to force events
    state.refreshInProgress = true
    state.refreshTimestamp = now()
    runIn(16, "clearRefreshFlag")
    
    def commands = []
    
    if(paramPollBattery as boolean) {
        commands.addAll(getBatteryReportStringList())
    }
    
    if(paramPollTemperature as boolean) {
        // Enhanced temperature polling includes complete thermal system status
        commands.addAll(getSensorMultilevelReportStringList())
        commands << getThermostatModeReportString()
        commands << getThermostatSetpointReportString()
        commands << getParameterReport(3) // get operating state
    }
       
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commands, 3000), hubitat.device.Protocol.ZWAVE))
}

void updatePreferencesFromDevice() {
    def commands = []
    
    commands << getParameterReport(1)
    commands << getParameterReport(2)
    commands << getParameterReport(3)
        
    state.clear()
    state.hint = "<b style='color:red;'>Not all parameters are reported. Please, refresh this page in a few seconds</b>"
    runIn(20, removeStateHint)    
    
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commands, 3000), hubitat.device.Protocol.ZWAVE))
}

void validateClockSettings() {
    sendHubCommand(new hubitat.device.HubAction(getClockReportString(), hubitat.device.Protocol.ZWAVE))
}

//=============================================================================================================================================================
// Own interface
//=============================================================================================================================================================
/*
def boolean useSchedule = false
if(null != paramUseSchedule) {
    useSchedule = paramUseSchedule as boolean
}
*/
void configure() {
    // device.updateSetting("", [type: "number", value: cmd.scaledConfigurationValue])
    // commands << getProtectionReportV1String()
    updateVersionInfo()
    parse([
        // Initialize supported thermostat modes and fan modes as proper JSON
        [name: "supportedThermostatModes",     value: JsonOutput.toJson(['off', 'heat']), descriptionText: "Initial value"],
        [name: "supportedThermostatFanModes",  value: JsonOutput.toJson(['auto']), descriptionText: "Initial value"],
        [name: "thermostatFanMode",           value: "auto",    descriptionText: "Initial value"],
        // configure alarms
        [name: "alarmCannotReachTemp",        value: "idle",    descriptionText: "Initial value"],
        [name: "alarmHardwareMalfunction",    value: "idle",    descriptionText: "Initial value"],
        [name: "alarmWindowOpened",           value: "idle",    descriptionText: "Initial value"],
        // other attributes
        [name: "heatingSetpoint",             value: "20.0".toBigDecimal(), descriptionText: "Initial value"],
        [name: "coolingSetpoint",             value: "30.0".toBigDecimal(), descriptionText: "Initial value"], // Required for HomeKit - set high since device only heats
        [name: "minHeatingSetpoint",          value: "10.0".toBigDecimal(), descriptionText: "Initial value"], // Required for HomeKit - Fibaro TRV minimum
        [name: "maxHeatingSetpoint",          value: "30.0".toBigDecimal(), descriptionText: "Initial value"], // Required for HomeKit - Fibaro TRV maximum
        [name: "protectionLocal",             value: "unknown", descriptionText: "Initial value"],
        [name: "temperature",                 value: "20.0".toBigDecimal(), descriptionText: "Initial value"],
        [name: "thermostatMode",              value: "unknown", descriptionText: "Initial value"],
        [name: "thermostatOperatingState",    value: "unknown", descriptionText: "Initial value"],
        [name: "thermostatSetpoint",          value: "20.0".toBigDecimal(), descriptionText: "Initial value"],
        [name: "healthStatus",                value: "unknown", descriptionText: "Initial value"]
    ])
    
    initialize()
}

void initialize() {    
    refresh()
    scheduleDeviceHealthCheck((settings?.healthCheckInterval as Integer) ?: 240, (settings?.healthCheckMethod as Integer) ?: 1)
}

def installed() {
    configure()
}

void refresh() {
    // Set refresh tracking to force events for reports received within 15 seconds
    state.refreshInProgress = true
    state.refreshTimestamp = now()
    runIn(16, "clearRefreshFlag")
    
    def commands = []
    
    // These commands will trigger forced events during refresh window:
    // - getThermostatModeReportString() -> thermostatMode event (zwaveEvent ThermostatModeReport)
    // - getThermostatSetpointReportString() -> heatingSetpoint event (zwaveEvent ThermostatSetpointReport)
    // - getParameterReport(3) -> thermostatOperatingState event (zwaveEvent ConfigurationReport)
    // - getSensorMultilevelReportStringList() -> temperature event (handled by drozovyk library)
    commands << getThermostatModeReportString()
    commands << getThermostatSetpointReportString()
    commands << getParameterReport(3) // get operating state
    
    commands.addAll(getBatteryReportStringList())
    commands.addAll(getSensorMultilevelReportStringList())
    
    commands << getClockReportString()
    commands << getProtectionReportV1String()
    
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commands, 3000), hubitat.device.Protocol.ZWAVE))
}

// Helper function to check if we're within the refresh window
private boolean isWithinRefreshWindow() {
    return state.refreshInProgress && 
           state.refreshTimestamp && 
           (now() - state.refreshTimestamp) <= 15000
}

// Clear refresh flag (called automatically 16 seconds after refresh)
void clearRefreshFlag() {
    state.refreshInProgress = false
    state.refreshTimestamp = null
}

void off() {
    def boolean value = false;
    if(null != paramOpenValveWhenOff) { 
        value = paramOpenValveWhenOff as boolean
    } 
    
    def cmds = []
    
    if(value) {
        cmds.addAll(setThermostatModeStringList(255 as Short))
    }
    else {
        cmds.addAll(setThermostatModeStringList(0 as Short))
    }
    
    cmds << getParameterReport(3) // get operating state 
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 4000), hubitat.device.Protocol.ZWAVE))
}

void heat() {
    def cmds = setThermostatModeStringList(99 as Short)
    cmds << getParameterReport(3) // get operating state 
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 4000), hubitat.device.Protocol.ZWAVE))
}

void auto() {
    heat()
}

void setThermostatMode(String mode) {
    if(mode == "heat") {
        heat()
    }
    else if(mode == "off") {
        off()
    }        
}

void setHeatingSetpoint(temperature) {
    def cmds = setThermostatSetpointStringList(temperature as BigDecimal)
    cmds << getParameterReport(3) // get operating state
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 4000), hubitat.device.Protocol.ZWAVE))
}

void setCoolingSetpoint(temperature) {
    // This device only supports heating, but HomeKit requires this command
    // Just update the attribute without sending any Z-Wave commands
    logWarn "setCoolingSetpoint(${temperature}): This device only supports heating mode"
    sendEvent(name: "coolingSetpoint", value: temperature, unit: "°C", descriptionText: "coolingSetpoint set to ${temperature}°C (heating-only device)")
}

void updated() {
    // Send all parameters one by one
    def commands = []
    
    commands << setParameter1()
    commands << setParameter2()
    
    if(null != paramKnobLock) { 
        def boolean value = paramKnobLock as boolean        
        if(state?.parameterKnobLocked?.value != value) {
            commands << setProtectionV1String(value)
        }
    }    
    
    commands << getParameterReport(1)
    commands << getParameterReport(2)
    commands << getParameterReport(3)
    
    state.clear()
    state.hint = "<b style='color:red;'>Not all parameters are reported. Please, refresh this page in a few seconds</b>"
    runIn(20, removeStateHint)
    
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commands, 3000), hubitat.device.Protocol.ZWAVE))
    
// Add polling
// run 'mymethod' every tenth minute.
    pollInterval = paramPollIntervalOptions.indexOf(paramPollInterval)
    if((paramPollBattery as boolean || paramPollTemperature as boolean) && null != pollInterval) {
        schedule(paramPollIntervalCronExp[pollInterval as int], timedPoll)
    }
    else {
        unschedule(timedPoll)
    }
    
    // Schedule health check
    scheduleDeviceHealthCheck((settings?.healthCheckInterval as Integer) ?: 240, (settings?.healthCheckMethod as Integer) ?: 1)
}

// added kkossev //

def ping() {
    def now = new Date().getTime()
    state.cmdSentTime = now
    getBatteryReport()
}

def getBatteryReport(){
    return secureCmd(zwave.batteryV1.batteryGet())		
}

String secureCmd(cmd) {
    if (getDataValue("zwaveSecurePairingComplete") == "true" && getDataValue("S2") == null) {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
		return secure(cmd)
    }	
}

String secure(String cmd){
    return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd){
    return zwaveSecureEncap(cmd)
}

def sendRTTevent() {
    def now = new Date().getTime()
    def timeRunning = now.toInteger() -  state.cmdSentTime.toInteger()
    log.debug "${device.displayName} rtt (ms) : ${timeRunning}"    
    sendEvent(name: "rtt", value: timeRunning, unit: "ms")    
}

// ===============================================================================================
// HealthCheck functionality
// ===============================================================================================

void setHealthStatusOnline() {
    if (state.health == null) { state.health = [:] }
    state.health['checkCtr3'] = 0
    
    def currentStatus = device.currentValue('healthStatus')
    if (currentStatus != 'online') {
        sendHealthStatusEvent('online')
        logInfo "is now online!"
    }
}

void deviceHealthCheck() {
    if (state.health == null) { state.health = [:] }
    int ctr = state.health['checkCtr3'] ?: 0
    if (ctr >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline') {
            logWarn "not present!"
            sendHealthStatusEvent('offline')
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})"
    }
    
    // If periodic polling is enabled, ping the device
    if (((settings.healthCheckMethod as Integer) ?: 0) == 2) {
        ping()
    }
    state.health['checkCtr3'] = ctr + 1
}

void sendHealthStatusEvent(String value) {
    String descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital')
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName} <b>${descriptionText}</b>" }
    }
}

void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) {
    if (healthMethod == 1 || healthMethod == 2) {
        String cron = getCron(intervalMins * 60)
        schedule(cron, 'deviceHealthCheck')
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes"
    }
    else {
        logWarn "deviceHealthCheck is not scheduled!"
        unschedule('deviceHealthCheck')
    }
}

void unScheduleDeviceHealthCheck() {
    unschedule('deviceHealthCheck')
    device.deleteCurrentState('healthStatus')
    logWarn "device health check is disabled!"
}

String getCron(timeInSeconds) {
    final Random rnd = new Random()
    int minutes = (timeInSeconds / 60) as int
    int hours = (minutes / 60) as int
    if (hours > 23) { hours = 23 }
    String cron
    if (timeInSeconds < 60) {
        cron = "*/$timeInSeconds * * * * ? *"
    }
    else {
        if (minutes < 60) {
            cron = "${rnd.nextInt(59)} 0/$minutes * * * ? *"
        }
        else {
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} 0/$hours * * ? *"
        }
    }
    return cron
}

