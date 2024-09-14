
/**
 *  AWTRIX 3 MQTT Driver
 *
 *  This driver integrates AWTRIX 3 with Hubitat Elevation using MQTT.
 *  It allows you to control and monitor AWTRIX 3 through Hubitat.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Changelog:
 * ver. 1.0.0  2024-09-0145: Initial version
 */

import groovy.transform.Field

@Field static String version = "1.0.0"
@Field static String timeStamp = "2024/09/14 8:51 AM"

metadata {
	definition(name: "AWTRIX 3 MQTT Driver", namespace: "kkossev", author: "Krassimir Kossev", importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Chlorine%20Meter/Tuya_Zigbee_Chlorine_Meter_lib_included.groovy' ) { 
		capability "Initialize"
		capability "Refresh"
		capability "Switch"
		capability "Sensor"
		capability "Notification"
		command "sendNotification", ["string"]
        command "mqttConnect"
        command "disconnect"

		attribute "switch", "string"
		attribute "battery", "number"
		attribute "temperature", "number"
		attribute "humidity", "number"
		attribute "brightness", "number"
		attribute "lux", "number"
		attribute "ram", "number"
		attribute "uptime", "number"
		attribute "wifi_signal", "number"
		attribute "messages", "number"
		attribute "version", "string"
		attribute "indicator1", "string"
		attribute "indicator2", "string"
		attribute "indicator3", "string"
		attribute "app", "string"
		attribute "uid", "string"
		attribute "matrix", "string"
		attribute "ip_address", "string"
	}

	preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables events logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
		input name: "mqttBroker", type: "text", title: "<b>MQTT Broker</b>", description: "MQTT Broker Address", required: true
		input name: "mqttPort", type: "number", title: "<b>MQTT Port</b>", description: "MQTT Broker Port", required: true, defaultValue: 1883
		input name: "mqttUsername", type: "text", title: "<b>MQTT Username</b>", description: "MQTT Username", required: false
		input name: "mqttPassword", type: "password", title: "<b>MQTT Password</b>", description: "MQTT Password", required: false
		input name: "mqttTopic", type: "text", title: "<b>MQTT Topic</b>", description: "MQTT Topic", required: false, defaultValue: "awtrix_21b0c0"
		input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false
		if (advancedOptions == true) {
			input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.'
			input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"'
			input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.'
		}
	}
}



@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true    // disable it for production

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]

def installed() {
	logDebug "Installed"
	initialize()
}

def updated() {
	logDebug "Updated"
	initialize()
}

def initialize() {
	logDebug "Initializing"
	try {
		interfaces.mqtt.connect("tcp://${settings?.mqttBroker}:${settings?.mqttPort}", "hubitat_${device.deviceNetworkId}", settings?.mqttUsername, settings?.mqttPassword)
		interfaces.mqtt.subscribe("${settings?.mqttTopic}/#")
	} catch (e) {
		logError "MQTT Initialization Error: ${e.message}"
	}
}

void disconnect() {
	interfaces.mqtt.disconnect()
	logDebug "disconnect: Disconnected from broker ${settings.mqttBroker} (${settings.mqttTopic})."

}


def parse(String description) {
    def msg = interfaces.mqtt.parseMessage(description)
    logDebug "Received MQTT message: ${msg}"

    // Extract topic and payload
    def topic = msg.topic
    def payload = msg.payload

    if (topic.endsWith('/stats')) {
		parseStats(payload)
    } else {
        logDebug "Topic <b>${topic}</b> is not 'stats', ignoring message."
    }
}// Parse JSON payload


void parseStats(String payload) {
	def jsonPayload = new groovy.json.JsonSlurper().parseText(payload)
	logTrace "Battery: ${jsonPayload.bat}"
	logTrace "Battery Raw: ${jsonPayload.bat_raw}"
	logTrace "Type: ${jsonPayload.type}"
	logTrace "Lux: ${jsonPayload.lux}"
	logTrace "LDR Raw: ${jsonPayload.ldr_raw}"
	logTrace "RAM: ${jsonPayload.ram}"
	logTrace "Brightness: ${jsonPayload.bri}"
	logTrace "Temperature: ${jsonPayload.temp}"
	logTrace "Humidity: ${jsonPayload.hum}"
	logTrace "Uptime: ${jsonPayload.uptime}"
	logTrace "WiFi Signal: ${jsonPayload.wifi_signal}"
	logTrace "Messages: ${jsonPayload.messages}"
	logTrace "Version: ${jsonPayload.version}"
	logTrace "Indicator1: ${jsonPayload.indicator1}"
	logTrace "Indicator2: ${jsonPayload.indicator2}"
	logTrace "Indicator3: ${jsonPayload.indicator3}"
	logTrace "App: ${jsonPayload.app}"
	logTrace "UID: ${jsonPayload.uid}"
	logTrace "Matrix: ${jsonPayload.matrix}"
	logTrace "IP Address: ${jsonPayload.ip_address}"
	// send events for some of the values
	sendEvent(name: "battery", value: jsonPayload.bat)
	sendEvent(name: "temperature", value: jsonPayload.temp)
	sendEvent(name: "humidity", value: jsonPayload.hum)
	sendEvent(name: "brightness", value: jsonPayload.bri)
	sendEvent(name: "lux", value: jsonPayload.lux)
	sendEvent(name: "ram", value: jsonPayload.ram)
	sendEvent(name: "uptime", value: jsonPayload.uptime)
	sendEvent(name: "wifi_signal", value: jsonPayload.wifi_signal)
	sendEvent(name: "messages", value: jsonPayload.messages)
	sendEvent(name: "version", value: jsonPayload.version)
	sendEvent(name: "indicator1", value: jsonPayload.indicator1)
	sendEvent(name: "indicator2", value: jsonPayload.indicator2)
	sendEvent(name: "indicator3", value: jsonPayload.indicator3)
	sendEvent(name: "app", value: jsonPayload.app)
	sendEvent(name: "uid", value: jsonPayload.uid)
	sendEvent(name: "matrix", value: jsonPayload.matrix)
	sendEvent(name: "ip_address", value: jsonPayload.ip_address)

}

def on() {
	sendMqttMessage("awtrix/control", "on")
	sendEvent(name: "switch", value: "on")
}

def off() {
	sendMqttMessage("awtrix/control", "off")
	sendEvent(name: "switch", value: "off")
}

def refresh() {
	logDebug "Refreshing"
	// Implement refresh logic if needed
}

def deviceNotification(message) {
	sendMqttMessage("awtrix/notification", message)
	logDebug "Sent notification: ${message}"
}

def sendNotification(message) {
	deviceNotification(message)
}

private sendMqttMessage(topic, payload) {
	try {
		interfaces.mqtt.publish(topic, payload)
		logDebug "Published MQTT message: ${topic} - ${payload}"
	} catch (e) {
		logError "MQTT Publish Error: ${e.message}"
	}
}


// ---------------- credits to Andrew Davison (BirdsLikeWires) ----------------

void mqttConnect() {
    logDebug "Connecting to MQTT broker"
	try {

		def mqttInt = interfaces.mqtt
        mqttInt.each { logDebug "MQTT Interface: ${it}" }

		if (mqttInt.isConnected()) {
			logDebug "mqttConnect : Connection to broker ${settings?.mqttBroker} (${settings?.mqttTopic}) is live."
			//return
		}

		if (settings?.mqttTopic == null || settings?.mqttTopic == "") {
			logError "mqttConnect : Topic is not set." 
			return
		}

		String clientID = "hubitat-" + device.deviceNetworkId
		mqttBrokerUrl = "tcp://" + settings?.mqttBroker + ":1883"
		mqttInt.connect(mqttBrokerUrl, clientID, settings?.mqttUsername, settings?.mqttPassword)
		pauseExecution(500)
		mqttInt.subscribe(settings?.mqttTopic)

	} catch (Exception e) {
		if (settings?.mqttBroker == null) {
			logWarn "mqttConnect : No broker configured."
		} else {

			logError "mqttConnect : ${e.message}"

		}

	}

} 


void mqttClientStatus(String status) {
    logDebug "mqttClientStatus : ${status}"
	if (status.indexOf('Connection succeeded') >= 0) {
		logDebug "mqttClientStatus : Connection to broker ${settings?.mqttBroker} (${settings?.mqttTopic}) is live."
	} else {
		logError "mqttClientStatus : ${status}"
	}
}

// ---------------------------------------------------------------------

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } }
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } }
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } }
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } }
void logError(final String msg) { if (settings?.logEnable)   { log.error "${device.displayName} " + msg } }
