/**
 *  Xiaomi Aqara Mijia Sensors and Switches:
 *
 *  Xiaomi Aqara Contact Sensor				: MCCGQ11LM [*]
 *  Xiaomi Aqara Motion Sensor				: RTCGQ11LM [*]
 *  Xiaomi Aqara Temperature Sensor			: WSDCGQ11LM
 *  Xiaomi Aqara Vibration Sensor			: DJT11LM [*]
 *  Xiaomi Aqara Water Leak Sensor			: SJCGQ11LM [*]
 *  Xiaomi Aqara Wireless Double Remote Switch		: WXKG02LM [*]
 *  Xiaomi Aqara Wireless Mini Switch			: WXKG11LM
 *  Xiaomi Aqara Wireless Mini Switch with Gyroscope	: WXKG12LM [*]
 *  Xiaomi Aqara Wireless Single Remote Switch		: WXKG03LM [*]
 *  Xiaomi Mijia Door and Window Sensor			: MCCGQ01LM
 *  Xiaomi Mijia Human Body Sensor			: RTCGQ01LM
 *  Xiaomi Mijia Light Sensor				: GZCGQ01LM
 *  Xiaomi Mijia Wireless Switch			: WXKG01LM [*]
 *
 *  [*] These devices have an internal temperature sensor, though with only rough accuracy
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Changelog:
 *
 *  v0.18 - Added the ability to pass a button number and default none to 1 - thanks to @FourEyedPanda
 *          Added the release command to the available list
 *
 *  v0.17 - Check device model is set before interrogating it
 *
 *  v0.16 - Added decoding of the (ir)regular device data for devices that use
 *          the 4C FF02 encoding (i.e. Xiaomi Mijia Door and Window Sensor)
 *
 *  v0.15 - Update contact/water sensors from the (ir)regular device data
 *          updates in case of sensor bounce leaving it in an incorrect state
 *          Added VoltageMeasurement as a capability
 *
 *  v0.14 - Improvements to Presence detection - You should check each device
 *          and hit Configure to ensure each device has the presenceTracker job
 *          schedules
 *          Changed from BETA to RELEASE
 *
 *  v0.13 - Modified Temperature Offset setting to float to allow for decimal corrections (e.g. 2.5)
 *          Fixed virtual Release button number assignment
 *
 *  v0.12 - Added more presence intervals
 *          Added device Commands, however they are all commented out by default
 *          Removed unnecessary "isStateChange:true" for all devices apart from buttons
 *          Added fingerprint for WXKG02LM
 *          Modified Held duration setting to float to allow for milliseconds (e.g 0.5 = 500 milliseconds)
 *          Added configurable virtual Release event for WXKG02LM and WXKG03LM
 *          Added setting to allow WXKG01LM to represent 5 buttons instead of a single button with multiple states
 *
 *  v0.11 - Modified presence to only update if previously not present or set
 *          Added a temperature offset setting
 *          Added experimental support for an internal temperature sensor that some of these devices contain (see above [*])
 *
 *  v0.10 - Added motion to contact sensors via an option for those want it
 *          Fixed button pushed status for WXKG01LM
 *
 *  v0.09 - Added support for WXKG11LM
 *
 *  v0.08 - Added simple presence tracking that checks the devices presence and will change state if no data receieved
 *          Added support for MCCGQ01LM
 *          Added safeguard limits to humidity, pressure and temperature measurements
 *          Fixed the pressure unit description to hPa
 *
 *  v0.07 - Added support for WXKG01LM
 *          Added support for WXKG12LM
 *          Added support for SJCGQ11LM
 *          Removed unnecessary button scheduled reset
 *          Standardised the info logging text
 *
 *  v0.06 - Added battery level detection for older Xiaomi sensors
 *          Fixed lux calculation for RTCGQ11LM
 *
 *  v0.05 - Added workaround for Xiaomi data structure oddities
 *          Added fingerprint for RTCGQ01LM
 *
 *  v0.04 - Fixed temperature calculation for negative temps
 *
 *  v0.03 - Fix for spurious voltage calculation from device data
 *
 *  v0.02 - Added state and schedule cleanup to configure command if you move from an old driver
 *
 *  v0.01 - Initial public release
 */

import hubitat.zigbee.zcl.DataType
import hubitat.helper.HexUtils

metadata {
	definition (name: "Xiaomi Aqara Mijia Sensors and Switches", namespace: "waytotheweb", author: "Jonathan Michaelson", importUrl: "https://raw.githubusercontent.com/waytotheweb/hubitat/main/drivers/Xiaomi_Aqara_Mijia_Sensors.groovy") {
		capability "Battery"
		capability "VoltageMeasurement"
		capability "Sensor"
		capability "Refresh"
		capability "Configuration"
		capability "PresenceSensor"

		capability "IlluminanceMeasurement"
		capability "RelativeHumidityMeasurement"
		capability "TemperatureMeasurement"
		capability "PressureMeasurement"
		capability "AccelerationSensor"
		capability "MotionSensor"
		capability "ContactSensor"
		capability "WaterSensor"

		capability "PushableButton"
		capability "HoldableButton"
		capability "DoubleTapableButton"
		capability "ReleasableButton"

		attribute "tilt", "string"
		attribute "taps", "number"
		attribute "released", "number"
		attribute "shaken", "number"
		attribute "temperature", "number"


// If you want to use these you will have to remove the comment prefix for
// those you want. They are not enabled by default as they can make a mess of
// the Attribute states which can only be cleared by deleting and adding the
// device back. If the driver is updated you will have to comment them out
// again.
// Note: The attribute state will be updated regardless of the previous state.

//		command "open"
//		command "closed"
//		command "active"
//		command "inactive"
//		command "present"
//		command "notPresent"
//		command "push"
//		command "hold"
//		command "doubleTap"
//		command "release"
//		command "shake"
//		command "wet"
//		command "dry"

		fingerprint profileId: "0104", inClusters: "0000,0400,0003,0001", outClusters: "0003", manufacturer: "LUMI", model: "lumi.sen_ill.mgl01", deviceJoinName: "Xiaomi Mijia Light Sensor"
		fingerprint profileId: "0104", inClusters: "0000,0003,FFFF,0402,0403,0405", outClusters: "0000,0004,FFFF", manufacturer: "LUMI", model: "lumi.weather", deviceJoinName: "Xiaomi Aqara Temperature Sensor"
		fingerprint profileId: "0104", inClusters: "0000,FFFF,0406,0400,0500,0001,0003", outClusters: "0000,0019", manufacturer: "LUMI", model: "lumi.sensor_motion.aq2", deviceJoinName: "Xiaomi Aqara Motion Sensor"
		fingerprint profileId: "0104", inClusters: "0000,FFFF,0406,0400,0500,0001,0003", outClusters: "0000,0019", manufacturer: "LUMI", model: "lumi.sensor_motion", deviceJoinName: "Xiaomi Aqara Motion Sensor"
		fingerprint profileId: "0104", inClusters: "0003,0012", outClusters: "0004,0003,0005,0012", manufacturer: "LUMI", model: "lumi.vibration.aq1", deviceJoinName: "Xiaomi Aqara Vibration Sensor"
		fingerprint profileId: "0104", inClusters: "0000,0003,FFFF,0006", outClusters: "0000,0004,FFFF", manufacturer: "LUMI", model: "lumi.sensor_magnet.aq2", deviceJoinName: "Xiaomi Aqara Contact Sensor"
		fingerprint profileId: "0104", inClusters: "0000,0003,FFFF,0019", outClusters: "0000,0004,0003,0006,0008,0005,0019", manufacturer: "LUMI", model: "lumi.sensor_magnet", deviceJoinName: "Xiaomi Mijia Door and Window Sensor"
		fingerprint profileId: "0104", inClusters: "0000,0003,0019,0012,FFFF", outClusters: "0000,0003,0004,0005,0019,0012,FFFF", manufacturer: "LUMI", model: "lumi.remote.b186acn01", deviceJoinName: "Xiaomi Aqara Wireless Single Remote Switch"
		fingerprint profileId: "0104", inClusters: "0000,0003,0019,0012,FFFF", outClusters: "0000,0003,0004,0005,0019,0012,FFFF", manufacturer: "LUMI", model: "lumi.remote.b286acn01", deviceJoinName: "Xiaomi Aqara Wireless Double Remote Switch"
		fingerprint profileId: "0104", inClusters: "0000,0003,0019,0012,FFFF", outClusters: "0000,0003,0004,0005,0019,0012,FFFF", manufacturer: "LUMI", model: "lumi.sensor_86sw1", deviceJoinName: "Xiaomi Aqara Wireless Single Remote Switch"
		fingerprint profileId: "0104", inClusters: "0000,0003,FFFF,0019", outClusters: "0000,0004,0003,0006,0008,0005,0019", manufacturer: "LUMI", model: "lumi.sensor_switch", deviceJoinName: "Xiaomi Mijia Wireless Switch"
		fingerprint profileId: "0104", inClusters: "0000,FFFF,0006", outClusters: "0000,0004,FFFF", manufacturer: "LUMI", model: "lumi.sensor_switch.aq3", deviceJoinName: "Aqara Wireless Mini Switch with Gyroscope"
		fingerprint profileId: "0104", inClusters: "0000,0012,0006,0001", outClusters: "0000", manufacturer: "LUMI", model: "lumi.sensor_swit", deviceJoinName: "Xiaomi Aqara Wireless Mini Switch"
		fingerprint profileId: "0104", inClusters: "0000,0003,0001", outClusters: "0019", manufacturer: "LUMI", model: "lumi.sensor_wleak.aq1", deviceJoinName: "Xiaomi Aqara Water Leak Sensor"

	}
	preferences {
		input name: "infoLogging", type: "bool", title: "Enable info message logging", description: "", defaultValue: true
		input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: "", defaultValue: false
		input name: "presenceDetect", type: "bool", title: "Enable Presence Detection", description: "This will keep track of the devices presence and will change state if no data received within the Presence Timeout. If it does lose presence try pushing the reset button on the device if available.", defaultValue: true
		input name: "presenceHours", type: "enum", title: "Presence Timeout", description: "The number of hours before a device is considered 'not present'.<br>Note: Some of these devices only update their battery every 6 hours.", defaultValue: "12", options: ["1","2","3","4","6","12","24"]
		input name: "holdDuration", type: "number", title: "Button Hold Duration", description: "For WXKG01LM, this is how long the button needs to be pushed to be in a held state.<br> For WXKG02LM, WXKG03LM it is how long the button needs to be held to register a release state.<br> Time is in seconds, decimals can be used, e.g. 0.5 is 500ms", defaultValue: "1"
		input name: "allButtons", type: "bool", title: "WXKG01LM Button Function", description: "By default, the WXKG01LM is treated as a single button. To enable separate buttons for each press type, enable this option.", defaultValue: false
		input name: "temperatureOffset", type: "number", title: "Temperature Offset", description: "This setting compensates for an inaccurate temperature sensor. For example, set to -7 if the temperature is 7 degress too warm.", defaultValue: "0"
		input name: "internalTemperature", type: "bool", title: "Experimental Internal Temperature", description: "Some of these devices have an internal temperature sensor. It only reports when the battery reports (usually every 50 minutes) and is not very accurate and usually requires an offset.", defaultValue: false
		input name: "motionContact", type: "bool", title: "Add Motion To Contact Sensors", description: "This adds a motion state to contact sensors, i.e. 'contact: open' = 'motion: active'", defaultValue: false
	}
}

def parse(String description) {
	if (debugLogging) log.debug "Incoming data from device : $description"

	if (description?.startsWith("zone status ")) {
		if (description?.startsWith("zone status 0x0001")){
			sendEvent("name": "water", "value": "wet")
			if (infoLogging) log.info "$device.displayName water changed to wet"
		}
		else if (description?.startsWith("zone status 0x0000")){
			sendEvent("name": "water", "value": "dry")
			if (infoLogging) log.info "$device.displayName water changed to dry"
		}
	}
	if (description?.startsWith("read attr -")) {
		def mydescMap = description.split(', ').collectEntries {
			entry -> def pair = entry.split(': ')
			[(pair.first()): pair.last()]
		}
		if (mydescMap.attrId == "FF01" || mydescMap.attrId == "FF02") {
			if (debugLogging) log.debug "Processing Xiaomi data (cluster:$mydescMap.cluster, attrId:$mydescMap.attrId)"
			if (mydescMap.cluster == "0000") {
				def MsgLength = mydescMap.value.size()
				if (MsgLength > 20){
					def batteryVoltage = ""
					if (mydescMap.attrId == "FF01" && mydescMap.value[4..5] == "21"){
						batteryVoltage = mydescMap.value[8..9] + mydescMap.value[6..7]
					}
					else if (mydescMap.attrId == "FF02" && mydescMap.value[8..9] == "21"){
						batteryVoltage = mydescMap.value[12..13] + mydescMap.value[10..11]
					}
					
					if (batteryVoltage != ""){
						batteryEvent(Integer.parseInt(batteryVoltage, 16) / 100)
					}

					if (internalTemperature && mydescMap.attrId == "FF01" && mydescMap.value[10..13] == "0328"){
						rawValue = hexStrToSignedInt(mydescMap.value[14..15])
						if (debugLogging) log.debug "Processing Xiaomi data (internal temperature: $rawValue)"
						def Scale = location.temperatureScale
						if (Scale == "F") rawValue = (rawValue * 1.8) + 32
						if (temperatureOffset == null) temperatureOffset = "0"
						def offsetrawValue = (rawValue + Float.valueOf(temperatureOffset))
						rawValue = offsetrawValue
						if (debugLogging) log.debug "Processing Xiaomi data (internal temperature: $rawValue with $internalTempOffset offset and conversion)"
						if (rawValue > 200 || rawValue < -200){
							if (infoLogging) log.info "$device.displayName Ignored internal temperature value: $rawValue\u00B0"+Scale
						} else {
							sendEvent("name": "temperature", "value": rawValue, "unit": "\u00B0"+Scale)
							if (infoLogging) log.info "$device.displayName internal temperature changed to $rawValue\u00B0"+Scale
						}
					}
					if (mydescMap.attrId == "FF01" && mydescMap.value[-6..-3] == "6410"){
						rawValue = (mydescMap.value[-2..-1]).toInteger()
						if (device.getDataValue("model") != null && getDataValue("model").contains("magnet")){
							if (debugLogging) log.debug "Processing Xiaomi data (contact status) = ${rawValue}"
							def contact = "closed"
							if (rawValue == 1) contact = "open"
							sendEvent("name": "contact", "value": contact)
							if (infoLogging) log.info "$device.displayName contact updated to $contact"
							if (motionContact){
								def motion = "inactive"
								if (rawValue == 1) motion = "active"
								sendEvent("name": "motion", "value": motion)
								if (infoLogging) log.info "$device.displayName motion updated to $motion"
							}
						}
						if (device.getDataValue("model") != null && getDataValue("model").contains("leak")){
							if (debugLogging) log.debug "Processing Xiaomi data (leak status) = ${rawValue}"
							def contact = "dry"
							if (rawValue == 1) contact = "wet"
							sendEvent("name": "water", "value": contact)
							if (infoLogging) log.info "$device.displayName water updated to $contact"
						}
					}
					if (mydescMap.attrId == "FF02" && mydescMap.value[0..5] == "060010"){
						rawValue = (mydescMap.value[6..7]).toInteger()
						if (device.getDataValue("model") != null && getDataValue("model").contains("magnet")){
							if (debugLogging) log.debug "Processing Xiaomi data (contact status) = ${rawValue}"
							def contact = "closed"
							if (rawValue == 1) contact = "open"
							sendEvent("name": "contact", "value": contact)
							if (infoLogging) log.info "$device.displayName contact updated to $contact"
							if (motionContact){
								def motion = "inactive"
								if (rawValue == 1) motion = "active"
								sendEvent("name": "motion", "value": motion)
								if (infoLogging) log.info "$device.displayName motion updated to $motion"
							}
						}
					}
				}
			}
		
		}
		else if (mydescMap.cluster == "0000" && mydescMap.attrId == "0005" &&  mydescMap.encoding == "42"){
			if (debugLogging) log.debug "Processing Xiaomi data (cluster:$mydescMap.cluster, attrId:$mydescMap.attrId, encoding:$mydescMap.encoding)"
			if (mydescMap.value.size() > 60){
				def batteryData = mydescMap.value.split('FF42')[1]
				if (batteryData[4..5] == "21"){
					batteryVoltage = batteryData[8..9] + batteryData[6..7]
					if (batteryVoltage != ""){
						batteryEvent(Integer.parseInt(batteryVoltage, 16) / 100)
					}
				}
			}
		} else {
			def descMap = zigbee.parseDescriptionAsMap(description)

			if (debugLogging) log.debug "Processing Xigbee data (cluster:$descMap.cluster, attrId:$descMap.attrId)"

			if (descMap.cluster == "0001" && descMap.attrId == "0020") {
				batteryEvent(Integer.parseInt(descMap.value,16))
			}
			else if (descMap.cluster == "0400" && descMap.attrId == "0000") {
				def rawEncoding = Integer.parseInt(descMap.encoding, 16)
				def rawLux = Integer.parseInt(descMap.value,16)
				def lux = rawLux > 0 ? Math.round(Math.pow(10,(rawLux/10000)) - 1) : 0
				if (getDataValue("model") == "lumi.sensor_motion.aq2") lux = rawLux
				sendEvent("name": "illuminance", "value": lux, "unit": "lux")
				if (infoLogging) log.info "$device.displayName illuminance changed to $lux"
			}
			else if (descMap.cluster == "0402" && descMap.attrId == "0000") {
				def rawValue = hexStrToSignedInt(descMap.value) / 100
				def Scale = location.temperatureScale
				if (Scale == "F") rawValue = (rawValue * 1.8) + 32
				if (temperatureOffset == null) temperatureOffset = "0"
				def offsetrawValue = (rawValue  + Float.valueOf(temperatureOffset))
				rawValue = offsetrawValue
				if (rawValue > 200 || rawValue < -200){
					if (infoLogging) log.info "$device.displayName Ignored temperature value: $rawValue\u00B0"+Scale
				} else {
					sendEvent("name": "temperature", "value": rawValue, "unit": "\u00B0"+Scale)
					if (infoLogging) log.info "$device.displayName temperature changed to $rawValue\u00B0"+Scale
				}
			}
			else if (descMap.cluster == "0403" && descMap.attrId == "0000") {
				def rawValue = Integer.parseInt(descMap.value,16)
				if (rawValue > 2000 || rawValue < 500){
					if (infoLogging) log.info "$device.displayName Ignored pressure value: $rawValue"
				} else {
					sendEvent("name": "pressure", "value": rawValue, "unit": "hPa")
					if (infoLogging) log.info "$device.displayName pressure changed to $rawValue"
				}
			}
			else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
				def rawValue = Integer.parseInt(descMap.value,16) / 100
				if (rawValue > 100 || rawValue < 0){
					if (infoLogging) log.info "$device.displayName Ignored humidity value: $rawValue"
				} else {
					sendEvent("name": "humidity", "value": rawValue, "unit": "%")
					if (infoLogging) log.info "$device.displayName humidity changed to $rawValue"
				}
			}
			else if (descMap.cluster == "0406" && descMap.attrId == "0000") {
				def rawValue = Integer.parseInt(descMap.value,16)
				def status = "inactive"
				if (rawValue == 1) status = "active"
				sendEvent("name": "motion", "value": status)
				if (infoLogging) log.info "$device.displayName motion changed to $status"
				unschedule(resetMotion)
				runIn(65, resetMotion)
			}
			else if (descMap.cluster == "0101" && descMap.attrId == "0508") {
				def status = "active"
				sendEvent("name": "acceleration", "value": status)
				sendEvent("name": "motion", "value": "active")
				if (infoLogging) log.info "$device.displayName acceleration changed to $status"
				unschedule(resetVibration)
				runIn(65, resetVibration)
			}
			else if (descMap.cluster == "0101" && descMap.attrId == "0055") {
				def status = "active"
				sendEvent("name": "tilt", "value": status)
				sendEvent("name": "motion", "value": "active")
				if (infoLogging) log.info "$device.displayName tilt changed to $status"
				unschedule(resetVibration)
				runIn(65, resetVibration)
			}
			else if (descMap.cluster == "0006" && descMap.attrId == "0000") {
				def rawValue = Integer.parseInt(descMap.value,16)
				def contact = "closed"
				if (rawValue == 1) contact = "open"
				sendEvent("name": "contact", "value": contact)
				if (infoLogging) log.info "$device.displayName contact changed to $contact"
				if (motionContact){
					def motion = "inactive"
					if (rawValue == 1) motion = "active"
					sendEvent("name": "motion", "value": motion)
					if (infoLogging) log.info "$device.displayName motion changed to $motion"
				}
				if (getDataValue("model") == "lumi.sensor_switch.aq2"){
					sendEvent("name": "pushed", "value": 1, isStateChange: true)
					if (infoLogging) log.info "$device.displayName was pushed"
				}
				if (getDataValue("model") == "lumi.sensor_switch"){
					if (rawValue == 0){
						int thisHold = Float.valueOf(holdDuration) * 1000
						runInMillis(thisHold, deviceHeld)
						state.held = false
					} else {
						if (state.held == true){
							state.held = false
							unschedule(deviceHeld)
							sendEvent("name": "released", "value":  1, isStateChange: true)
							if (infoLogging) log.info "$device.displayName was released"
						} else {
							unschedule(deviceHeld)
							if (allButtons){
								sendEvent("name": "pushed", "value": 1, isStateChange: true)
								if (infoLogging) log.info "$device.displayName button 1 was pushed"
							} else {
								sendEvent("name": "pushed", "value": 1, isStateChange: true)
								sendEvent("name": "taps", "value": 1, isStateChange: true)
								if (infoLogging) log.info "$device.displayName was pushed"
							}
						}
					}
				}
			}
			else if (descMap.cluster == "0006" && descMap.attrId == "8000" && (getDataValue("model") == "lumi.sensor_switch" || getDataValue("model") == "lumi.sensor_switch.aq2")) {
				def rawValue = Integer.parseInt(descMap.value,16)
				if (rawValue > 4) rawValue = 4
				sendEvent("name": "taps", "value":  rawValue, isStateChange: true)
				if (rawValue == 2 && allButtons == false){
					sendEvent("name": "doubleTapped", "value":  1, isStateChange: true)
					if (infoLogging) log.info "$device.displayName was doubleTapped"
				}
				if (allButtons){
					sendEvent("name": "pushed", "value": rawValue, isStateChange: true)
					if (infoLogging) log.info "$device.displayName button $rawValue was pushed"
				} else {
					if (infoLogging) log.info "$device.displayName tapped $rawValue time(s)"
				}
			}
			else if (descMap.cluster == "0012" && descMap.attrId == "0055") {
				def button = Integer.parseInt(descMap.endpoint,16) 
				def action = Integer.parseInt(descMap.value,16)
				if (debugLogging) log.debug "$device.displayName Button:$button, Action:$action"

				if (action == 0) {
					sendEvent("name": "held", "value":  button, isStateChange: true)
					if (infoLogging) log.info "$device.displayName button $button was held"
					int thisHold = Float.valueOf(holdDuration) * 1000
					state.held = true
					runInMillis(thisHold, deviceReleased, [data: button])
				}
				else if (action == 1) {
					sendEvent("name": "pushed", "value":  button, isStateChange: true)
					if (infoLogging) log.info "$device.displayName button $button was pushed $action time(s)"
				}
				else if (action == 2) {
					sendEvent("name": "doubleTapped", "value":  button, isStateChange: true)
					if (infoLogging) log.info "$device.displayName button $button was double tapped"
				}
				else if (action == 16) {
					sendEvent("name": "held", "value":  button, isStateChange: true)
					if (infoLogging) log.info "$device.displayName button $button was held"
				}
				else if (action == 17) {
					if (state.held) {
						state.held = false
						unschedule(deviceReleased);
					}
					sendEvent("name": "released", "value":  button, isStateChange: true)
					if (infoLogging) log.info "$device.displayName button $button was released"
				}
				else if (action == 18) {
					sendEvent("name": "shaken", "value":  button, isStateChange: true)
					if (infoLogging) log.info "$device.displayName button $button was shaken"
				}
				else if (action == 255) {
					sendEvent("name": "released", "value":  button, isStateChange: true)
					if (infoLogging) log.info "$device.displayName button $button was released"
				}
			}
		}
	}
	if (presenceDetect != false) {
		unschedule(presenceTracker)
		if (device.currentValue("presence") != "present"){
			sendEvent("name": "presence", "value":  "present")
			if (debugLogging) log.debug "$device.displayName present"
		}
		presenceStart()
	}
}

def open() {
	sendEvent("name": "contact", "value":  "open", isStateChange: true)
	if (infoLogging) log.info "$device.displayName contact changed to open [virtual]"
}

def closed() {
	sendEvent("name": "contact", "value":  "closed", isStateChange: true)
	if (infoLogging) log.info "$device.displayName contact changed to closed [virtual]"
}

def present() {
	sendEvent("name": "presence", "value":  "present", isStateChange: true)
	if (infoLogging) log.info "$device.displayName contact changed to present [virtual]"
}

def notPresent() {
	sendEvent("name": "presence", "value":  "not present", isStateChange: true)
	if (infoLogging) log.info "$device.displayName contact changed to not present [virtual]"
}

def active() {
	sendEvent("name": "motion", "value":  "active", isStateChange: true)
	if (infoLogging) log.info "$device.displayName motion changed to active [virtual]"
}

def inactive() {
	sendEvent("name": "motion", "value":  "inactive", isStateChange: true)
	if (infoLogging) log.info "$device.displayName motion changed to inactive [virtual]"
}

def push(button = 1) {
	sendEvent("name": "pushed", "value":  button, isStateChange: true)
	if (infoLogging) log.info "$device.displayName pushed $button [virtual]"
}

def doubleTap(button = 1) {
	sendEvent("name": "doubleTapped", "value":  button, isStateChange: true)
	if (infoLogging) log.info "$device.displayName doubleTapped $button [virtual]"
}

def hold(button = 1) {
	sendEvent("name": "held", "value":  button, isStateChange: true)
	if (infoLogging) log.info "$device.displayName held $button [virtual]"
}

def release(button = 1) {
	sendEvent("name": "released", "value":  button, isStateChange: true)
	if (infoLogging) log.info "$device.displayName released $button [virtual]"
}

def shake() {
	sendEvent("name": "shaken", "value":  1, isStateChange: true)
	if (infoLogging) log.info "$device.displayName shaken [virtual]"
}

def wet() {
	sendEvent("name": "water", "value":  "wet", isStateChange: true)
	if (infoLogging) log.info "$device.displayName changed to wet [virtual]"
}

def dry() {
	sendEvent("name": "water", "value":  "dry", isStateChange: true)
	if (infoLogging) log.info "$device.displayName changed to dry [virtual]"
}

def updated() {
	unschedule(presenceTracker)
	if (presenceDetect != false) presenceStart()
}

def presenceTracker() {
	sendEvent("name": "presence", "value":  "not present")
	if (infoLogging) log.info "$device.displayName not present"
	presenceStart()
}

def presenceStart() {
	if (presenceHours == null || presenceHours == "") presenceHours = "12"
	def scheduleHours = presenceHours.toInteger() * 60 * 60
	if (scheduleHours < 1 || scheduleHours > 86400) scheduleHours = 43200
	if (infoLogging) log.info "$device.displayName presense check in ${presenceHours} hours"
	runIn(scheduleHours, "presenceTracker")
}

def deviceHeld() {
	if (state.held == false){
		state.held = true
		if (allButtons){
			sendEvent("name": "pushed", "value": 5, isStateChange: true)
			if (infoLogging) log.info "$device.displayName button 5 was pushed"
		} else {
			sendEvent("name": "held", "value":  1, isStateChange: true)
			if (infoLogging) log.info "$device.displayName was held"
		}
	}
}

def deviceReleased(button) {
	if (state.held == true){
		state.held = false
		sendEvent("name": "released", "value":  button, isStateChange: true)
		if (infoLogging) log.info "$device.displayName button $button was released"
	}
}

def batteryEvent(rawValue) {
	def batteryVolts = (rawValue / 10).setScale(2, BigDecimal.ROUND_HALF_UP)
	def minVolts = 20
	def maxVolts = 30
	def pct = (((rawValue - minVolts) / (maxVolts - minVolts)) * 100).toInteger()
	def batteryValue = Math.min(100, pct)
	if (batteryValue > 0){
		sendEvent("name": "battery", "value": batteryValue, "unit": "%")
		sendEvent("name": "voltage", "value": batteryVolts, "unit": "volts")
		if (infoLogging) log.info "$device.displayName battery changed to $batteryValue%"
		if (infoLogging) log.info "$device.displayName voltage changed to $batteryVolts volts"
	}

	return
}

def resetMotion() {
	if (device.currentState('motion')?.value == "active"){
		sendEvent("name": "motion", "value": "inactive")
		if (infoLogging) log.info "$device.displayName motion changed to inactive"
	}

	return
}

def resetVibration() {
	if (device.currentState('acceleration')?.value == "active"){
		sendEvent("name": "acceleration", "value": "inactive")
		if (infoLogging) log.info "$device.displayName acceleration changed to inactive"
	}
	if (device.currentState('tilt')?.value != "inactive"){
		sendEvent("name": "tilt", "value": "inactive")
		if (infoLogging) log.info "$device.displayName tilt changed to inactive"
	}
	if (device.currentState('motion')?.value != "inactive"){
		sendEvent("name": "motion", "value": "inactive")
		if (infoLogging) log.info "$device.displayName motion changed to inactive"
	}

	return
}

def refresh() {
	List<String> cmd = []

	if (debugLogging) log.debug "refresh()"
	if (device.currentState('motion')?.value == "active"){
		unschedule(resetMotion)
		resetMotion()
	}
	if (device.currentState('acceleration')?.value == "active"){
		unschedule(resetVibration)
		resetVibration()
	}

	cmd += zigbee.onOffRefresh()
	cmd += zigbee.onOffConfig()
	cmd += zigbee.batteryConfig()

	cmd += zigbee.readAttribute(0x0001, 0x0020)
	cmd += zigbee.readAttribute(0x0000, 0x0004)
	cmd += zigbee.readAttribute(0x0000, 0x0005)
	cmd += zigbee.readAttribute(0x0400, 0x0000)
	cmd += zigbee.readAttribute(0x0402, 0x0000)

	return cmd
}

def configure() {
	Integer zDelay = 100
	List<String> cmd = []

	if (debugLogging) log.debug "configure()"

	unschedule()
	state.clear()

	if (presenceDetect != false) presenceStart()

	cmd = [
		"zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0000 {${device.zigbeeId}} {}",
		"zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0001 {${device.zigbeeId}} {}",
		"zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0003 {${device.zigbeeId}} {}",
		"zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0400 {${device.zigbeeId}} {}",
		"zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0402 {${device.zigbeeId}} {}",
	]

	cmd += zigbee.configureReporting(0x0400, 0x0000, 0x21, 10,   3600, 300)
	cmd += zigbee.configureReporting(0x0402, 0x0000, 0x21, 10,   3600, 300)
	cmd += zigbee.configureReporting(0x0001, 0x0020, 0x20, 3600, 3600, 1)

	return cmd
}
