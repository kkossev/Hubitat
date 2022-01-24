/*
* Moes ZSS-ZK-THL illuminance, temperature, humidity sensor model TS0222 (_TYZB01_kvwjujy9) driver for Hubitat 
*
* Description:
* Proceses illuminance, temperature, humidity and battery level
*
* Information:
* https://www.moeshouse.com/collections/zigbee/products/zigbee-smart-brightness-thermometer-real-time-light-sensitive-temperature-and-humidity-detector 
*
* Credits: code sections borrowed from WooBooung, chirpy, Markus Liljergren 
*
* Licensing:
* Copyright 2021 Krassimir Kossev.
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Version Control:
* 1.0.0  2021-09-05 kkossev    Initial version
* 1.1.0  2021-09-05 kkossev    Filter Zero Readings option added (default:true)
* 1.1.1  2021-09-05 kkossev    filterZero bug fix :) 
* 1.2.0  2021-09-05 kkossev    Added bindings for both endPoint 1 and endPoint 2 for humidity,temperature and illuminance clusters; delay 1..2 seconds!
* 1.2.1  2021-09-08 kkossev    Added binding for genTime cluster ( 0x000A )
* 1.2.2  2021-09-08 kkossev    Reporting interval changed to 3600, 28800 
* 1.2.3  2021-10-02 kkossev    Removed any clusters bindings and reporting configurations!
* 1.3.0  2022-01-24 kkossev    A la Tuya GW pairing initialization
*
*/
def version() { "1.3.0" }
def timeStamp() {"2022/01/24 10:53 AM"}

import hubitat.zigbee.zcl.DataType
import groovy.json.JsonOutput
import hubitat.helper.HexUtils

metadata {
    definition (name: "Moes ZSS-ZK-THL sensor (TS0222)", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Moes_ZSS-ZK-THL_TS0222/Moes_ZSS-ZK-THL_TS0222.groovy") {
        capability "Configuration"
        capability "Initialize"
        capability "Battery"
        capability "Refresh"
        capability "IlluminanceMeasurement"
		capability "RelativeHumidityMeasurement"
		capability "TemperatureMeasurement"
        capability "Sensor"

        fingerprint profileId: "0104", deviceId: "0106", inClusters: "0000,0001,0400,0402,0405", outClusters: "0019", manufacturer: "_TYZB01_kvwjujy9", model: "TS0222", deviceJoinName: "Moes ZSS-ZK-THL illuminance temperature humidity sensor"
    }

    preferences {
        input ("logEnable", "bool", title: "Enable debug logging", defaultValue: false)
        input ("txtEnable", "bool", title: "Enable description text logging", defaultValue: true)
        input ("tempOffset", "number", title: "Temperature offset", description: "Select how many degrees to adjust the temperature.", range: "-100..100", displayDuringSetup: false)
        input ("humidityOffset", "number", title: "Humidity offset", description: "Enter a percentage to adjust the humidity.", range: "*..*", displayDuringSetup: false)
        input ("filterZero", "bool", title: "Filter zero readings", defaultValue: true)
    }
}

def parse(String description) {
    if (logEnable) log.debug "description: $description"
    checkDriverVersion()
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {
        return null
    }
    Map descMap = zigbee.parseDescriptionAsMap(description)

    if ( true ) {
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else {
                getBatteryResult(Integer.parseInt(descMap.value, 16))
            }
        } 
        // humidity
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
				def rawValue = Integer.parseInt(descMap.value,16) / 100
                rawValue = rawValue + ((humidityOffset ?: 0) as float)
                rawValue =  ((float)rawValue).trunc(1)
            
				if ((rawValue > 100 || rawValue <= 0) && filterZero ) {
					if (txtEnable) log.warn "$device.displayName ignored humidity value: $rawValue"
				} else {
					sendEvent("name": "humidity", "value": rawValue, "unit": "%", isStateChange: true)
					if (txtEnable) log.info "$device.displayName humidity changed to $rawValue"
				}
			}
        // illuminance
		else if (descMap.cluster == "0400" && descMap.attrId == "0000") {
				def rawEncoding = Integer.parseInt(descMap.encoding, 16)
				def rawLux = Integer.parseInt(descMap.value,16)
				def lux = rawLux > 0 ? Math.round(Math.pow(10,(rawLux/10000))) : 0
            
				if (lux < 0.01f && filterZero) {
					log.warn "$device.displayName ignored illuminance value: $lux"
				} else {
				    sendEvent("name": "illuminance", "value": lux, "unit": "lux", isStateChange: true)
				    if (txtEnable) log.info "$device.displayName illuminance changed to $lux"
				}            
			}
        // temperature
		else if (descMap.cluster == "0402" && descMap.attrId == "0000") {
				def rawValue = hexStrToSignedInt(descMap.value) / 100
                rawValue = rawValue +  ((tempOffset ?: 0) as float)
                rawValue =  Math.round((rawValue - 0.05) * 10) / 10

				def Scale = location.temperatureScale
				if (Scale == "F") rawValue = (rawValue * 1.8) + 32
				if (temperatureOffset == null) temperatureOffset = "0"
				def offsetrawValue = (rawValue  + Float.valueOf(temperatureOffset))
				rawValue = offsetrawValue
				if ((rawValue > 200 || rawValue < -200 || (Math.abs(rawValue)<0.1f)) && filterZero ){
					log.warn "$device.displayName Ignored temperature value: $rawValue\u00B0"+Scale
				} else {
					sendEvent("name": "temperature", "value": rawValue, "unit": "\u00B0"+Scale, isStateChange: true)
					if (txtEnable) log.info "$device.displayName temperature changed to $rawValue\u00B0"+Scale
				}
			}
        
        else if (descMap?.clusterInt == 0x0402 && descMap.commandInt == 0x07) {
            if (descMap.data[0] == "00") {
                if (logEnable) log.debug "TEMP REPORTING CONFIG RESPONSE: $descMap"
                sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
            } else {
                log.warn "TEMP REPORTING CONFIG FAILED- error code: ${descMap.data[0]}"
            }
        }
        else {
            if (logEnable) log.warn "UNPROCESSED descMap: ${descMap}"
            Map map = zigbee.getEvent(description)
            if (logEnable) log.debug "zigbee.getEvent: ${map}"
        }
    }
   // refresh()
}



def installed() {
	if (logEnable) log.debug "installed"
    configure()
}


def getBatteryPercentageResult(rawValue) {
    if (logEnable) log.debug "Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.value = Math.round(rawValue / 2)
        result.descriptionText = "${device.displayName} battery was ${result.value}%"
        result.isStateChange = true
        sendEvent(result)
    }

    return result
}

private Map getBatteryResult(rawValue) {
    if (logEnable) log.debug 'Battery'
    def linkText = getLinkText(device)

    def result = [:]

    def volts = rawValue / 10
    if (!(rawValue == 0 || rawValue == 255)) {
        def minVolts = 2.1
        def maxVolts = 3.0
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0)
        roundedPct = 1
        result.value = Math.min(100, roundedPct)
        result.descriptionText = "${linkText} battery was ${result.value}%"
        result.name = 'battery'
        result.isStateChange = true
        sendEvent(result)
    }
    return result
}

def refresh() {
    if (logEnable) log.debug "SKIPPING refreshing Moes ZSS-ZK-THL battery status"
    return
}

def tuyaBlackMagic() {
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
}

def configure() {
    if (logEnable) log.debug "configure().."
    List<String> cmds = []
    cmds += tuyaBlackMagic()    
    sendZigbeeCommands(cmds)    
}

// This method is called when the preferences of a device are updated.
def updated(){
    if (txtEnable==true) log.info "Updating ${device.getLabel()} (${device.getName()})"
    if (txtEnable==true) log.info "Debug logging is <b>${logEnable}</b> Description text logging is  <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn( 86400, logsOff)    // turn off debug logging after 24 hours
        if (txtEnable==true) log.info "Debug logging will be automatically switched off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    configure()
}


void initializeVars( boolean fullInit = true ) {
    if (txtEnable == true) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", false)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || device.getDataValue("tempOffset") == null) device.updateSetting("tempOffset", 0)
    if (fullInit == true || device.getDataValue("humidityOffset") == null) device.updateSetting("humidityOffset", 0)
    if (fullInit == true || device.getDataValue("filterZero") == null) device.updateSetting("tempOffset", true)
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion != null && driverVersionAndTimeStamp() == state.driverVersion) {
        //log.trace "driverVersion is the same ${driverVersionAndTimeStamp()}"
    }
    else {
        if (txtEnable==true) log.debug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

def initialize() {
    if (txtEnable==true) log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
    updated()            // calls also configure()
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value:"false",type:"bool"])
}

boolean isTuyaE00xCluster( String description )
{
    if(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0) {
        if (logEnable) log.debug " Tuya cluster: E000 or E001 - don't know how to handle it, skipping it for now..."
        return true
    }
    else
        return false
}

boolean otherTuyaOddities( String description )
{
    if(description.indexOf('cluster: 0000') >= 0 || description.indexOf('attrId: 0004') >= 0) {
        if (logEnable) log.debug " other Tuya oddities - don't know how to handle it, skipping it for now..."
        return true
    }
    else {
        return false
        log.trace "no oddities detected..."
    }
}


void sendZigbeeCommands(ArrayList<String> cmd) {
    if (logEnable) log.debug "sendZigbeeCommands(cmd=$cmd)"
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    sendHubCommand(allActions)
}



private logDebug(msg) {
	if (settings?.logEnable) log.debug "${msg}"
}
