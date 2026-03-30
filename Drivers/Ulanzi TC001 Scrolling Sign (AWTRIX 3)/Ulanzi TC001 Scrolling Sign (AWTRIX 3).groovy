/**
 *  AWTRIX 3 MQTT  - driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 * 	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * 	in compliance with the License. You may obtain a copy of the License at:
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * 	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * 	for the specific language governing permissions and limitations under the License.
 *
 * Changelog:
 *
 * ver. 1.0.0  2024-09-14 kkossev  - Initial dummy version
 * ver. 1.0.1  2024-09-26 kkossev  - added ping, healthStatus, parse deviceNotification JSON payload; added commonly used text preferences; dismiss
 * ver. 1.1.0  2024-09-26 kkossev  - renamed driver to 'Ulanzi TC001 Scrolling Sign (AWTRIX 3)'
 * ver. 1.1.1  2024-10-01 kkossev  - (dev. branch)
 * ver. 1.2.0  2026-03-27 kkossev  - MQTT hardening: publish fix, reconnect logic, lifecycle cleanup
 *                                   
 *                                   TODO: sound!
*/

// https://github.com/Blueforcer/awtrix3/blob/main/docs/api.md
// https://github.com/Blueforcer/awtrix3/releases  (ulanzi_TC001_0.96.bin)	http://192.168.0.234/

import groovy.transform.Field

@Field static String version = "1.2.0"
@Field static String timeStamp = "2026/03/27 10:15 AM"

@Field static final Boolean _DEBUG = false                  // set to false for production, true for more verbose logging and extra trace logs
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true    // disable it for production



metadata {
	definition(name: "Ulanzi TC001 Scrolling Sign (AWTRIX 3)", namespace: "kkossev", author: "Krassimir Kossev", importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Drivers/Ulanzi%20TC001%20Scrolling%20Sign%20(AWTRIX%203)/Ulanzi%20TC001%20Scrolling%20Sign%20(AWTRIX%203).groovy' ) { 
		capability "Initialize"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"
		capability "Sensor"
		capability "Notification"
        capability "PushableButton"
        capability "ReleasableButton"
        capability 'HealthCheck'
		
		//command "deviceNotification", ["string"]	// "Notification" capability is already included
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]]
		command 'dismiss'
		command 'reboot'
		command 'statusRetrieval', [[name: 'Status Retrieval', type: 'ENUM', description: 'Request status data from AWTRIX', constraints: StatusRetrievalOpts.keySet() as List<String>]]
		command 'sound', [[name: 'Play a sound', type: 'STRING', description: 'RTTTL in JSON format']]
		if (_DEBUG) {
        	command "mqttConnect"
        	command "disconnect"
			command "mqttStatus"
			command 'subscribe', [[name: 'Subscribe to a topic', type: 'STRING', description: 'Topic to subscribe to']]
			command 'unsubscribe', [[name: 'Unsubscribe from a topic', type: 'STRING', description: 'Topic to unsubscribe from']]
			command 'publish', [
				[name: 'Publish to a topic', type: 'STRING', description: 'Topic to publish to', constraints: ['STRING']], 
				[name: 'Payload', type: 'STRING', description: 'Payload to publish', constraints: ['STRING']]
			]
		}

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'Status', 'string'
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
		attribute "uid", "string"
		attribute "matrix", "string"
		attribute "ip_address", "string"
		attribute "currentApp", "string"
		attribute "effects", "string"
		attribute "transitions", "string"
		attribute "appLoop", "string"
	}

	preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables events logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
		input name: "mqttBroker", type: "text", title: "<b>MQTT Broker</b>", description: "MQTT Broker Address", required: true, defaultValue: '192.168.0.159'
		input name: "mqttPort", type: "number", title: "<b>MQTT Port</b>", description: "MQTT Broker Port", required: true, defaultValue: 1883
		input name: "mqttUsername", type: "text", title: "<b>MQTT Username</b>", description: "MQTT Username", required: false, defaultValue: "mqtt_user"
		input name: "mqttPassword", type: "password", title: "<b>MQTT Password</b>", description: "MQTT Password", required: false, defaultValue: "mqtt_pass"
		input name: "mqttTopic", type: "text", title: "<b>MQTT Topic</b>", description: "MQTT Topic", required: false, defaultValue: "awtrix_21b0c0"

		input name: 'color', type: 'enum', title: '<b>Color</b>', options: COLORS.keySet(), defaultValue: 'White', description: 'Select the color for the text.'
		input name: 'rainbowEffect', type: 'bool', title: '<b>Rainbow Effect</b>', defaultValue: false, description: 'Enable the rainbow effect for the text.'
		input name: "duration", type: "number", title: "<b>Notification Duration</b>", description: "Sets how long the notification should be displayed.", required: true, defaultValue: 5
		input name: "repeat", type: "number", title: "<b>Repeat</b>", description: "Sets how many times the text should be scrolled through the matrix before the app ends..", required: true, defaultValue: -1
		input name: 'hold', type: 'bool', title: '<b>Hold Notification</b>', defaultValue: false, description: 'Set it to true, to hold your notification on top until you press the middle button or dismiss it via command.'
		input name: 'stack', type: 'bool', title: '<b>Stack Notification</b>', defaultValue: true, description: 'Defines if the notification will be stacked. false will immediately replace the current notification.'
		input name: 'wakeup', type: 'bool', title: '<b>Wakeup Notification</b>', defaultValue: false, description: 'if the Matrix is off, the notification will wake it up for the time of the notification.'
		input name: 'noScroll', type: 'bool', title: '<b>Disable Text Scrolling</b>', defaultValue: false, description: 'Disables the text scrolling.'

		input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: true
		if (advancedOptions == true) {
			input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.'
			input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"'
			input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.'
			input name: 'extendedStats', type: 'bool', title: '<b>Extended Statustics</b>', defaultValue: false, description: 'If not interested in all the statistics, disable this option to reduce the HE CPU usage.'
		}
	}
}



@Field static final String DEVICE_TYPE = 'Message Sign'
@Field static final List<String> TopicsToSubscribe = [
	'stats', 'stats/currentApp', 'stats/buttonSelect', 'stats/buttonLeft', 'stats/buttonRight',
	'stats/effects', 'stats/transitions', 'stats/loop',
	'effects', 'transitions', 'loop'
]
@Field static final Integer NUMBER_OF_BUTTONS = 3
@Field static final Integer MQTT_RECONNECT_BASE_DELAY = 5
@Field static final Integer MQTT_RECONNECT_MAX_DELAY = 300
@Field static final Integer MQTT_RECONNECT_MAX_ATTEMPTS = 12

import groovy.transform.CompileStatic

@Field static Map<String, String> TopicParsers = [
	"stats" : "parseStats",
	"stats/currentApp" : "parseCurrentApp",
	"stats/buttonSelect" : "parseButtonSelect",
	"stats/buttonLeft" : "parseButtonLeft",
	"stats/buttonRight" : "parseButtonRight",
	"stats/effects" : "parseEffects",
	"stats/transitions" : "parseTransitions",
	"stats/loop" : "parseLoop",
	"effects" : "parseEffects",
	"transitions" : "parseTransitions",
	"loop" : "parseLoop",
]

@Field static final Map<String, Map<String, Object>> StatusRetrievalOpts = [
	all: [topics: ['stats', 'stats/effects', 'stats/transitions', 'stats/loop', 'effects', 'transitions', 'loop'], label: 'All'],
	stats: [topics: ['stats'], label: 'General stats'],
	effects: [topics: ['stats/effects', 'effects'], label: 'Effects list'],
	transitions: [topics: ['stats/transitions', 'transitions'], label: 'Transition effects'],
	loop: [topics: ['stats/loop', 'loop'], label: 'App loop']
]

def parse(String description) {
    checkDriverVersion(state)
    updateRxStats(state)
    unscheduleCommandTimeoutCheck(state)
    setHealthStatusOnline(state)

	try {
		Map msg = interfaces.mqtt.parseMessage(description)
		if (msg == null || msg.topic == null) {
			logWarn "parse: invalid MQTT message '${description}'"
			return
		}
		logDebug "Received MQTT message: ${msg}"

		String topic = msg.topic as String
		String payload = msg.payload as String
		String subTopic = extractMqttSubTopic(topic)
		if (subTopic == null || subTopic == '') {
			logWarn "parse: unable to resolve subTopic for '${topic}'"
			return
		}
		logTrace "Topic: ${topic} Payload: ${payload} <b>subTopic : ${subTopic}</b>"

		String handler = TopicParsers[subTopic]
		if (handler) {
			logTrace "Calling handler ${handler} for topic ${subTopic} with payload ${payload}"
			this."${handler}"(subTopic, payload)
		} else {
			if (state.mqtt == null) { state.mqtt = [:] }
			state.mqtt['unknownTopicCtr'] = (state.mqtt['unknownTopicCtr'] ?: 0) + 1
			logDebug "<b> no handler</b> for Topic <b>${topic}</b>, ignoring message."
		}
	} catch (Exception e) {
		logError "parse: MQTT parse failure: ${e.message}"
	}
	
}// Parse JSON payload

private String extractMqttSubTopic(String topic) {
	if (topic == null || topic == '') {
		return null
	}
	int idx = topic.indexOf('/')
	if (idx < 0) {
		return topic
	}
	if (idx >= topic.length() - 1) {
		return null
	}
	return topic.substring(idx + 1)
}

static void updateRxStats(final Map state) {
	if (state?.stats == null) { state.stats = [:] }
    state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1
	Long now = new Date().getTime()
    state.stats['lastRx'] = now.toString()
}

static void updateTxStats(final Map state) {
	//logDebug('updateTxStats...')
	if (state?.stats == null) { state.stats = [:] }
	state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1
	Long now = new Date().getTime()
    state.stats['lastTx'] = now.toString()
}

void parseStats(String topic, String payload) {
	logTrace "parseStats: topic: ${topic}, payload:${payload}"
	if (payload == null || payload.trim() == '') {
		logTrace "parseStats: ignoring empty payload on ${topic}"
		return
	}
	try {
		def jsonPayload = new groovy.json.JsonSlurper().parseText(payload)
		processStats(jsonPayload)
	} catch (Exception e) {
		logError "parseStats: failed to parse payload '${payload}' : ${e.message}"
	}

}

void processStats(Map jsonPayload) {
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

	sendEvent(name: "battery", value: jsonPayload.bat)
	sendEvent(name: "temperature", value: jsonPayload.temp)
	sendEvent(name: "humidity", value: jsonPayload.hum)
	Integer brightness = (jsonPayload.bri != null) ? (jsonPayload.bri as Integer) : null
	if (brightness != null) {
		brightness = Math.max(0, Math.min(255, brightness))
		sendEvent(name: "brightness", value: brightness)
		sendEvent(name: "level", value: awtrixBrightnessToLevel(brightness), unit: "%")
	}
	if (settings?.extendedStats == true) {
		// send events also for the extended statistics
		sendEvent(name: "lux", value: jsonPayload.lux)
		sendEvent(name: "ram", value: jsonPayload.ram)
		sendEvent(name: "uptime", value: jsonPayload.uptime)
		sendEvent(name: "wifi_signal", value: jsonPayload.wifi_signal)
		sendEvent(name: "messages", value: jsonPayload.messages)
		sendEvent(name: "version", value: jsonPayload.version)
		sendEvent(name: "indicator1", value: jsonPayload.indicator1)
		sendEvent(name: "indicator2", value: jsonPayload.indicator2)
		sendEvent(name: "indicator3", value: jsonPayload.indicator3)
		sendEvent(name: "currentApp", value: jsonPayload.app)
		sendEvent(name: "uid", value: jsonPayload.uid)
		sendEvent(name: "matrix", value: jsonPayload.matrix)
		sendEvent(name: "ip_address", value: jsonPayload.ip_address)
	}
}

private Integer normalizeLevelValue(Object levelParam) {
	if (levelParam == null) { return 0 }
	Integer level
	try {
		level = (levelParam as BigDecimal).intValue()
	} catch (Exception e) {
		level = 0
	}
	return Math.max(0, Math.min(100, level))
}

private Integer awtrixBrightnessToLevel(Integer brightness) {
	if (brightness == null) { return 0 }
	return Math.round((brightness as Float) * 100.0f / 255.0f) as Integer
}

private Integer levelToAwtrixBrightness(Integer level) {
	return Math.round((level as Float) * 255.0f / 100.0f) as Integer
}

private String getBrightnessCommandTopic() {
	return 'settings'
}

void setLevel(levelParam) {
	setLevel(levelParam, null)
}

void setLevel(levelParam, duration) {
	Integer level = normalizeLevelValue(levelParam)
	Integer brightness = levelToAwtrixBrightness(level)
	Map payloadMap = [BRI: brightness]
	if (duration != null) {
		logTrace "setLevel: duration parameter is ignored by AWTRIX settings API"
	}
	publish(getBrightnessCommandTopic(), JsonOutput.toJson(payloadMap))
	sendEvent(name: "level", value: level, unit: "%")
	sendEvent(name: "brightness", value: brightness)
	if (level > 0 && device.currentValue('switch') != 'on') {
		sendEvent(name: "switch", value: "on")
	}
	if (level == 0 && device.currentValue('switch') != 'off') {
		sendEvent(name: "switch", value: "off")
	}
	logDebug "setLevel: level=${level}% brightness=${brightness} topic=${getBrightnessCommandTopic()}"
}

void parseCurrentApp(String topic, String payload) {
	logTrace "parseCurrentApp: topic: ${topic}, payload:${payload}"
	sendEvent(name: "currentApp", value: payload)
}

void parseEffects(String topic, String payload) {
	logTrace "parseEffects: topic: ${topic}, payload:${payload}"
	processListPayload('effects', payload)
}

void parseTransitions(String topic, String payload) {
	logTrace "parseTransitions: topic: ${topic}, payload:${payload}"
	processListPayload('transitions', payload)
}

void parseLoop(String topic, String payload) {
	logTrace "parseLoop: topic: ${topic}, payload:${payload}"
	processListPayload('appLoop', payload)
}

private void processListPayload(String attributeName, String payload) {
	if (payload == null || payload.trim() == '') {
		logTrace "processListPayload: ignoring empty payload for ${attributeName}"
		return
	}
	String eventValue = payload
	try {
		Object parsedPayload = new JsonSlurper().parseText(payload)
		if (parsedPayload instanceof Collection) {
			eventValue = (parsedPayload as Collection).collect { it?.toString() ?: '' }.join(', ')
		} else if (parsedPayload instanceof Map) {
			eventValue = JsonOutput.toJson(parsedPayload)
		}
	} catch (Exception e) {
		logDebug "processListPayload: unable to parse '${attributeName}' payload as JSON, storing raw payload"
	}
	sendEvent(name: attributeName, value: eventValue)
}

private String normalizeButtonAction(String payload) {
	if (payload == null) { return 'pushed' }
	String normalized = payload.toLowerCase()
	if (normalized in ['released', 'release', 'up']) {
		return 'released'
	}
	return 'pushed'
}

private void processButtonEvent(String payload, Integer buttonNumber) {
	String action = normalizeButtonAction(payload)
	sendEvent(name: action, value: buttonNumber, isStateChange: true, type: 'physical')
	logDebug "processButtonEvent: button=${buttonNumber} action=${action} payload=${payload}"
}

void parseButtonSelect(String topic, String payload) {
	logTrace "parseButtonSelect: topic: ${topic}, payload:${payload}"
	processButtonEvent(payload, 2)
}

void parseButtonLeft(String topic, String payload) {
	logTrace "parseButtonLeft: topic: ${topic}, payload:${payload}"
	processButtonEvent(payload, 1)
}

void parseButtonRight(String topic, String payload) {
	logTrace "parseButtonRight: topic: ${topic}, payload:${payload}"
	processButtonEvent(payload, 3)
}



void installed() {
	logDebug "Installed"
	configureButtonMetadata()
	applyHealthCheckSettings()
	initialize()
}

@Field static List<String> StatAttributesList = ['app', 'battery', 'brightness', 'hum', 'indicator1', 'indicator2', 'indicator3', 'ip_address', 'ldr_raw', 'lux', 'matrix', 'messages', 'ram', 'temp', 'type', 'uid', 'uptime', 'version', 'wifi_signal']

void updated() {
	logDebug "Updated"
    checkDriverVersion(state)
	configureButtonMetadata()
	applyHealthCheckSettings()
	if (state.states == null) { state.states = [:] }
	String currentMqttSignature = buildMqttConfigSignature()
	String previousMqttSignature = state.states['lastMqttConfigSignature']
	boolean mqttConfigChanged = (previousMqttSignature != null && previousMqttSignature != currentMqttSignature)

	if (mqttConfigChanged) {
		logInfo "MQTT settings changed (mqttConfigChanged=${mqttConfigChanged}), reconnecting session"
		disconnectMqttSession('updated')
	}

	if (settings?.extendedStats != true) {
		String attributesDeleted = ''
		StatAttributesList.each { it -> 
			attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") 
		}
		logDebug "Deleted attributes: ${attributesDeleted}";
	}

	initialize()
	state.states['lastMqttConfigSignature'] = currentMqttSignature
}

void initialize() {
	configureButtonMetadata()
	applyHealthCheckSettings()
	connectMqttSession('initialize')
}

private void configureButtonMetadata() {
	if ((device.currentValue('numberOfButtons') ?: 0) != NUMBER_OF_BUTTONS) {
		sendEvent(name: 'numberOfButtons', value: NUMBER_OF_BUTTONS, isStateChange: true)
	}
}

private void applyHealthCheckSettings() {
	int healthMethod = (settings?.healthCheckMethod != null) ? settings.healthCheckMethod.toInteger() : (HealthcheckMethodOpts.defaultValue as int)
	int intervalMins = (settings?.healthCheckInterval != null) ? settings.healthCheckInterval.toInteger() : (HealthcheckIntervalOpts.defaultValue as int)
	if (healthMethod == 0) {
		unScheduleDeviceHealthCheck()
		return
	}
	scheduleDeviceHealthCheck(intervalMins, healthMethod)
}

private String buildMqttConfigSignature() {
	return "${settings?.mqttBroker ?: ''}|${settings?.mqttPort ?: ''}|${settings?.mqttUsername ?: ''}|${settings?.mqttTopic ?: ''}"
}

private boolean isMqttConfigValid() {
	if (!settings?.mqttBroker) {
		logError 'MQTT broker is not configured'
		return false
	}
	if (!settings?.mqttTopic) {
		logError 'MQTT topic is not configured'
		return false
	}
	return true
}

private String getMqttBrokerUrl() {
	Integer brokerPort = (settings?.mqttPort ?: 1883) as Integer
	return "tcp://${settings?.mqttBroker}:${brokerPort}"
}

private String getMqttClientId() {
	return "hubitat_${device.deviceNetworkId}"
}

private boolean connectMqttSession(String source = 'initialize') {
	if (!isMqttConfigValid()) {
		return false
	}
	if (state.mqtt == null) { state.mqtt = [:] }

	String mqttBrokerUrl = getMqttBrokerUrl()
	String clientID = getMqttClientId()
	logDebug "connectMqttSession(${source}): broker=${mqttBrokerUrl} topic=${settings?.mqttTopic}"

	try {
		unschedule('mqttReconnect')
		interfaces.mqtt.connect(mqttBrokerUrl, clientID, settings?.mqttUsername, settings?.mqttPassword)
		state.mqtt['lastConnectTs'] = new Date().getTime()
		state.mqtt['isConnected'] = true
		state.mqtt['reconnectAttempts'] = 0
		updateTxStats(state)
		subscribeMultipleTopics(TopicsToSubscribe)
		return true
	} catch (Exception e) {
		state.mqtt['isConnected'] = false
		logError "connectMqttSession(${source}): ${e.message}"
		scheduleMqttReconnect("connect failure (${source})")
		return false
	}
}

// called from initializeVars(true) and resetStatistics()
void resetStats() {
    logDebug 'resetStats...'
    state.stats = [:] ; state.states = [:] ; state.health = [:]
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0; state.stats['lastRx'] = 0; state.stats['lastTx'] = 0
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0
}

void initializeVars( boolean fullInit = false ) {
    logDebug "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        unschedule()
        resetStats()
        state.comment = 'Works with AWTRIX 3 firmware Message Signs'
        logInfo 'all states and scheduled jobs cleared!'
        state.driverVersion = driverVersionAndTimeStamp()
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}"
        state.deviceType = DEVICE_TYPE
        sendInfoEvent('Initialized')
    }

    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.health == null) { state.health = [:] }

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) }
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) }
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) }
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) }

	if (fullInit || settings?.mqttBroker == null) { device.updateSetting('mqttBroker', [value: '192.168.0.159', type: 'text']) }
	if (fullInit || settings?.mqttPort == null) { device.updateSetting('mqttPort', [value: 1883, type: 'number']) }
	if (fullInit || settings?.mqttUsername == null) { device.updateSetting('mqttUsername', [value: 'mqtt_user', type: 'text']) }
	if (fullInit || settings?.mqttPassword == null) { device.updateSetting('mqttPassword', [value: 'mqtt_pass', type: 'password']) }
	if (fullInit || settings?.mqttTopic == null) { device.updateSetting('mqttTopic', [value: 'awtrix_21b0c0', type: 'text']) }
	if (fullInit || settings?.extendedStats == null) { device.updateSetting('extendedStats', [value: false, type: 'bool']) }

	if (fullInit || settings?.rainbowEffect == null) { device.updateSetting('rainbowEffect', false) }
	if (fullInit || settings?.duration == null) { device.updateSetting('duration', [value: 5, type: 'number']) }
	if (fullInit || settings?.repeat == null) { device.updateSetting('repeat', [value: -1, type: 'number']) }
	if (fullInit || settings?.hold == null) { device.updateSetting('hold', [value: false, type: 'bool']) }
	if (fullInit || settings?.stack == null) { device.updateSetting('stack', [value: true, type: 'bool']) }
	if (fullInit || settings?.wakeup == null) { device.updateSetting('wakeup', [value: false, type: 'bool']) }
	if (fullInit || settings?.noScroll == null) { device.updateSetting('noScroll', [value: false, type: 'bool']) }

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') }

}

private String driverVersionAndTimeStamp() { version + ' ' + timeStamp + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${getModel()} ${location.hub.firmwareVersionString})" }

void checkDriverVersion(final Map state) {
	logTrace "checkDriverVersion: driverVersion = ${state.driverVersion} driverVersionAndTimeStamp = ${driverVersionAndTimeStamp()}"
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}")
        state.driverVersion = driverVersionAndTimeStamp()
        initializeVars(false)
    }
    if (state.states == null) { state.states = [:] }
    if (state.stats  == null) { state.stats =  [:] }
}

// max 10 topics !
void subscribeMultipleTopics(List<String> topics) {
	if (state.mqtt == null) { state.mqtt = [:] }
	state.mqtt['subscribedTopics'] = []
	String subscribedTopics = ""
	topics.take(10).each { topic ->
		String fullTopic = "${settings?.mqttTopic}/${topic}"
		try {
			interfaces.mqtt.subscribe(fullTopic)
			(state.mqtt['subscribedTopics'] as List) << fullTopic
			subscribedTopics += "${fullTopic}, "
		} catch (Exception e) {
			logError "subscribeMultipleTopics: failed to subscribe '${fullTopic}' : ${e.message}"
		}
	}
	if (topics.size() > 10) {
		logWarn "subscribeMultipleTopics: only first 10 topics subscribed (requested=${topics.size()})"
	}
	logDebug "Subscribed to topics: ${subscribedTopics}"
}


private void disconnectMqttSession(String source = 'disconnect') {
	try {
		interfaces.mqtt.disconnect()
		updateTxStats(state)
		logDebug "disconnectMqttSession(${source}): disconnected from broker ${settings?.mqttBroker} (${settings?.mqttTopic})"
	} catch (Exception e) {
		logWarn "disconnectMqttSession(${source}): ${e.message}"
	}
	if (state.mqtt == null) { state.mqtt = [:] }
	state.mqtt['isConnected'] = false
	unschedule('mqttReconnect')
}


void disconnect() {
	disconnectMqttSession('manual')
}

void subscribe(String topicParam) {
	String topic = settings?.mqttTopic + '/' + topicParam
	logDebug "Subscribing to topic ${topic}"
	interfaces.mqtt.subscribe(topic)
	updateTxStats(state)
}

void unsubscribe(String topicParam) {
	String topic = settings?.mqttTopic + '/' + topicParam
	logDebug "Unsubscribing from topic ${topic}"
	interfaces.mqtt.unsubscribe(topic)
	updateTxStats(state)
}



def on() {
	logDebug "on: publishing power on"
	publish("power", JsonOutput.toJson([power: true]))
	sendEvent(name: "switch", value: "on")
}

def off() {
	logDebug "off: publishing power off"
	publish("power", JsonOutput.toJson([power: false]))
	sendEvent(name: "switch", value: "off")
}

void ping() {
    if (state.stats == null ) { state.stats = [:] } ; state.stats['pingTime'] = new Date().getTime()
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true
    scheduleCommandTimeoutCheck()
	if (settings?.mqttBroker != null && settings?.mqttBroker != "") {
		sendPing(settings?.mqttBroker)
	} else {
		logWarn 'ping: mqttBroker is not configured.'
		sendRttEvent('mqttBroker is not configured')
	}
}

// credits: @thebearmay
boolean sendPing(ipAddress) {
	if (ipAddress == null || ipAddress == "") {
		logWarn "sendPing: ipAddress is not set."
		sendRttEvent('ipAddress is not set')
		return false
	}
    if (!validIP (ipAddress)) {
        logWarn "IP address $ipAddress failed pattern check - ping request terminated"
		sendRttEvent('invalid IP address')
		return false
	}			
	logDebug "sendPing: pinging ${ipAddress}"
	hubitat.helper.NetworkUtils.PingData pingData = hubitat.helper.NetworkUtils.ping(ipAddress, 3 /*numPings.toInteger()*/)
	updateTxStats(state)
	logDebug "sendPing: pingData: ${pingData}"
	if (pingData.packetsReceived > 0) {
		BigDecimal roundedRttAvg = Math.round(pingData.rttAvg * 10) / 10.0
		sendRttEvent(roundedRttAvg.toString())
	} else {
		sendRttEvent('timeout')
		state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1
	}
	// PingData(rttAvg: 0.413, rttMin: 0.336, rttMax: 0.568, packetsTransmitted: 3, packetsReceived: 3, packetLoss: 0)
	// PingData(rttAvg: 0.0, rttMin: 0.0, rttMax: 0.0, packetsTransmitted: 3, packetsReceived: 0, packetLoss: 100)
	unscheduleCommandTimeoutCheck(state)
    return pingData.packetsReceived > 0
}

def validIP(ipAddress){
    regxPattern =/^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/
    boolean match = ipAddress ==~ regxPattern
    return match
}

// credits @thebearmay
String getModel() {
    try {
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */
        String model = getHubVersion() // requires >=2.2.8.141
    } catch (ignore) {
        try {
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res ->
                model = res.data.device.modelName
                return model
            }
        } catch (ignore_again) {
            return ''
        }
    }
}

def refresh() {
	checkDriverVersion(state)
	logInfo "refresh: MQTT mode only. Waiting for '${settings?.mqttTopic}/stats' updates."
}


// {text: "Hubitat", rainbow:true}
// {"text": [{"t": "Hello, ", "c": "FF0000"}, {"t": "world!", "c": "00FF00"}], "repeat": 2}

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

@Field static final Map<String, Map<String, Object>> COLORS = [
    "Black"              : [hex: "#000000", rgb: [0, 0, 0]],
    "White"              : [hex: "#FFFFFF", rgb: [255, 255, 255]],
    "Red"                : [hex: "#FF0000", rgb: [255, 0, 0]],
    "Lime"               : [hex: "#00FF00", rgb: [0, 255, 0]],
    "Blue"               : [hex: "#0000FF", rgb: [0, 0, 255]],
    "Yellow"             : [hex: "#FFFF00", rgb: [255, 255, 0]],
    "Cyan"        		 : [hex: "#00FFFF", rgb: [0, 255, 255]],
    "Magenta"  			 : [hex: "#FF00FF", rgb: [255, 0, 255]],
    "Silver"             : [hex: "#C0C0C0", rgb: [192, 192, 192]],
    "Gray"               : [hex: "#808080", rgb: [128, 128, 128]],
    "Maroon"             : [hex: "#800000", rgb: [128, 0, 0]],
    "Olive"              : [hex: "#808000", rgb: [128, 128, 0]],
    "Green"              : [hex: "#008000", rgb: [0, 128, 0]],
    "Purple"             : [hex: "#800080", rgb: [128, 0, 128]],
    "Teal"               : [hex: "#008080", rgb: [0, 128, 128]],
    "Navy"               : [hex: "#000080", rgb: [0, 0, 128]]
]

String ensureValidJsonString(String message) {
	if (message == null || message == "") { return "{}" }
    // Add double quotes around keys, but exclude keys that are already quoted
    message = message.replaceAll(/(\w+):/, '"$1":')
    // Add double quotes around string values, but exclude values that are already quoted or are arrays/objects
    message = message.replaceAll(/:\s*([^"\s\[\{][^,}\]]*)/, ': "$1"')
    return message
}

void deviceNotification(String messageParam) {
	String message = ensureValidJsonString(messageParam)
	logDebug "deviceNotification: ${messageParam} -> ${message}"
    JsonSlurper jsonSlurper = new JsonSlurper()
    Map parsedMessageMap = [:]
	boolean validJson = false
    try {
        parsedMessageMap = jsonSlurper.parseText(message)
        logDebug "deviceNotification: ${parsedMessageMap}"
		validJson = true
    } catch (Exception e) {
        logWarn "Invalid JSON message: ${message}. Error: ${e.message}"
		validJson = false
    }

	if (validJson) {
		logDebug "deviceNotification: validJson: ${parsedMessageMap}"
	}
	else {
		parsedMessageMap.text = message
		parsedMessageMap.rainbow = (settings?.rainbowEffect == true)
		parsedMessageMap.duration = (settings?.duration != null) ? settings.duration.toInteger() : 5
		parsedMessageMap.repeat = (settings?.repeat != null) ? settings.repeat.toInteger() : -1
		parsedMessageMap.noScroll = (settings?.noScroll == true)
		parsedMessageMap.color = COLORS[settings?.color ?: 'White']?.hex ?: '#FFFFFF'

		logDebug "deviceNotification: parsedMessageMap: ${parsedMessageMap}"
		message = JsonOutput.toJson(parsedMessageMap)
		logDebug "deviceNotification: message: ${message}"
	}
	//
    publish("notify", message)

}

void dismiss() {
	publish("notify/dismiss", "")
}

void reboot() {
	logWarn 'reboot: publishing reboot command to AWTRIX'
	publish('reboot', '')
	sendInfoEvent('Reboot command sent')
}

void statusRetrieval(String requestType = 'all') {
	String normalizedRequest = requestType ?: 'all'
	if (!(normalizedRequest in (StatusRetrievalOpts.keySet() as List<String>))) {
		logWarn "statusRetrieval: unsupported request '${requestType}', using 'all'"
		normalizedRequest = 'all'
	}
	List<String> topics = (StatusRetrievalOpts[normalizedRequest]?.topics ?: []) as List<String>
	topics.unique().each { topic ->
		publish(topic, '')
	}
	sendInfoEvent("Status retrieval requested: ${normalizedRequest}")
}

void sound(String soundParam) {
	String sound = soundParam
	if (sound == null || sound == "" || sound == "{}") {
		sound = "{Knock:d=32,o=4,b=100:e,4p,e,p,e,8p,e,4p,e,8p,e,4p}"
	}
	logDebug "sound: ${soundParam} -> ${sound}"
	publish("rtttl", JsonOutput.toJson([sound: sound]))
}

void publish(String topicParam, String payload) {
	if (!isMqttConfigValid()) {
		return
	}
	String topic = settings?.mqttTopic + '/' + topicParam
	logTrace "Publishing to topic ${topic} : ${payload}"
	sendMqttMessage(topic, payload)
}

void sendMqttMessage(topic, payload) {
	if (!isMqttConfigValid()) {
		return
	}
	try {
		interfaces.mqtt.publish(topic, payload)
		logDebug "Published MQTT message: ${topic} - ${payload}"
		updateTxStats(state)
	} catch (e) {
		logError "MQTT Publish Error: ${e.message}"
		scheduleMqttReconnect('publish failure')
	}
}


// ---------------- credits to Andrew Davison (BirdsLikeWires) ----------------

void mqttConnect() {
	connectMqttSession('manual')
} 

void mqttClientStatus(String status) {
    if (state.mqtt == null) { state.mqtt = [:] }
    logDebug "mqttClientStatus : ${status}"
	if (isMqttConnectedStatus(status)) {
		state.mqtt['isConnected'] = true
		state.mqtt['lastStatus'] = status
		state.mqtt['reconnectAttempts'] = 0
		unschedule('mqttReconnect')
		sendHealthStatusEvent('online')
		logDebug "mqttClientStatus : Connection to broker ${settings?.mqttBroker} (${settings?.mqttTopic}) is live."
	} else if (isMqttDisconnectedStatus(status)) {
		state.mqtt['isConnected'] = false
		state.mqtt['lastStatus'] = status
		logError "mqttClientStatus : ${status}"
		sendHealthStatusEvent('offline')
		scheduleMqttReconnect('mqttClientStatus')
	} else {
		state.mqtt['lastStatus'] = status
		logDebug "mqttClientStatus: informational status '${status}'"
	}
}

private boolean isMqttConnectedStatus(String status) {
	if (status == null) { return false }
	String normalized = status.toLowerCase()
	if (normalized.contains('disconnected')) { return false }
	return normalized.contains('connection succeeded') || normalized == 'connected'
}

private boolean isMqttDisconnectedStatus(String status) {
	if (status == null) { return true }
	String normalized = status.toLowerCase()
	return normalized.contains('lost') || normalized.contains('error') || normalized.contains('failed') || normalized.contains('disconnected')
}

private void scheduleMqttReconnect(String reason = 'unknown') {
	if (state.mqtt == null) { state.mqtt = [:] }
	Integer attempts = ((state.mqtt['reconnectAttempts'] ?: 0) as Integer) + 1
	state.mqtt['reconnectAttempts'] = attempts
	if (attempts > MQTT_RECONNECT_MAX_ATTEMPTS) {
		logError "scheduleMqttReconnect: max reconnect attempts reached (${MQTT_RECONNECT_MAX_ATTEMPTS})"
		return
	}
	Integer exp = Math.min(attempts - 1, 8)
	Integer delaySec = Math.min(MQTT_RECONNECT_MAX_DELAY, MQTT_RECONNECT_BASE_DELAY * (int)Math.pow(2, exp))
	logWarn "scheduleMqttReconnect: attempt=${attempts}, delay=${delaySec}s, reason=${reason}"
	runIn(delaySec, 'mqttReconnect', [overwrite: true])
}

void mqttReconnect() {
	connectMqttSession('reconnect')
}

void mqttStatus() {
	if (state.mqtt == null) { state.mqtt = [:] }
	String subscribedTopics = ((state.mqtt['subscribedTopics'] ?: []) as List).join(', ')
	logInfo "mqttStatus: connected=${state.mqtt['isConnected'] ?: false}, broker=${settings?.mqttBroker}:${settings?.mqttPort}, topic=${settings?.mqttTopic}, reconnectAttempts=${state.mqtt['reconnectAttempts'] ?: 0}, unknownTopics=${state.mqtt['unknownTopicCtr'] ?: 0}, subscribed=[${subscribedTopics}]"
}

// ----------- kkossev commonLib methods --------------

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final String  UNKNOWN = 'UNKNOWN'
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]
@Field static final Map ConfigureOpts = [
    'Configure the device'       : [key:2, function: 'configureNow'],
    'Reset Statistics'           : [key:9, function: 'resetStatistics'],
    '           --            '  : [key:3, function: 'configureHelp'],
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'],
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'],
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'],
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'],
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'],
    '           -             '  : [key:1, function: 'configureHelp'],
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults']
]

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } }
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } }
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } }
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } }
void logError(final String msg) { if (settings?.logEnable)   { log.error "${device.displayName} " + msg } }

void configure(String command) {
    logInfo "configure(${command})..."
    if (!(command in (ConfigureOpts.keySet() as List))) {
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}"
        return
    }
    //
    String func
    try {
        func = ConfigureOpts[command]?.function
        "$func"()
    }
    catch (e) {
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)"
        return
    }
    logInfo "executed '${func}'"
}

void configure() {
    List<String> cmds = []
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}"
    logDebug "configure(): settings: $settings"
/*	
    List<String> initCmds = initializeDevice()
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds }
    List<String> cfgCmds = configureDevice()
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds }
    if (cmds != null && !cmds.isEmpty()) {
        sendZigbeeCommands(cmds)
        logDebug "configure(): sent cmds = ${cmds}"
        sendInfoEvent('sent device configuration')
    }
    else {
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}"
    }
*/	
}

/* groovylint-disable-next-line UnusedMethodParameter */
void configureHelp(final String val) {
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" }
}

void configureNow() {
    configure()
}

// delete all Preferences
void deleteAllSettings() {
    String preferencesDeleted = ''
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") }
    logDebug "Deleted settings: ${preferencesDeleted}"
    logInfo  'All settings (preferences) DELETED'
}

// delete all attributes
void deleteAllCurrentStates() {
    String attributesDeleted = ''
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") }
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED'
}

// delete all State Variables
void deleteAllStates() {
    String stateDeleted = ''
    state.each { it -> stateDeleted += "${it.key}, " }
    state.clear()
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED'
}

void deleteAllScheduledJobs() {
    unschedule() ; logInfo 'All scheduled jobs DELETED'
}

void deleteAllChildDevices() {
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) }
    sendInfoEvent 'All child devices DELETED'
}

void loadAllDefaults() {
    logDebug 'loadAllDefaults() !!!'
    deleteAllSettings()
    deleteAllCurrentStates()
    deleteAllScheduledJobs()
    deleteAllStates()
    deleteAllChildDevices()

    initialize()
    configureNow()     // calls  also   configureDevice()
    updated()
    sendInfoEvent('All Defaults Loaded! F5 to refresh')
}

void clearInfoEvent()      { sendInfoEvent('clear') }

void sendInfoEvent(String info=null) {
    if (info == null || info == 'clear') {
        logDebug 'clearing the Status event'
        sendEvent(name: 'Status', value: 'clear', type: 'digital')
    }
    else {
        logInfo "${info}"
        sendEvent(name: 'Status', value: info, type: 'digital')
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute
    }
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    if (state.states == null) { state.states = [:] }
    state.states['isTimeoutCheck'] = true
    runIn(delay, 'deviceCommandTimeout')
}

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call !
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :(
    if (state.states == null) { state.states = [:] }
    if (state.states['isTimeoutCheck'] == true) {
        state.states['isTimeoutCheck'] = false
        unschedule('deviceCommandTimeout')
    }
}

void deviceCommandTimeout() {
    logWarn 'no response received (sleepy device or offline?)'
    sendRttEvent('timeout')
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1
}

void sendRttEvent( String value=null) {
    Long now = new Date().getTime()
    if (state.stats == null ) { state.stats = [:] }
    int timeRunning = now.toInteger() - (state.stats['pingTime'] ?: now).toInteger()
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})"
    if (value == null) {
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical')
    }
    else {
        descriptionText = "Round-trip time : ${value}"
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical')
    }
}


private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) {
    if (healthMethod == 1 || healthMethod == 2)  {
		String cron
		if (intervalMins < 60) {
			cron = "0 */${intervalMins} * ? * *"
		} else {
			int hours = Math.max(1, (int)(intervalMins / 60))
			cron = "0 0 */${hours} ? * *"
		}
		try {
        	schedule(cron, 'deviceHealthCheck')
        	logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes (cron=${cron})"
		} catch (Exception e) {
			logWarn "scheduleDeviceHealthCheck: invalid cron '${cron}' for interval=${intervalMins}, falling back to 60 minutes (${e.message})"
			schedule('0 0 * ? * *', 'deviceHealthCheck')
		}
    }
    else {
        logWarn 'deviceHealthCheck is not scheduled!'
        unschedule('deviceHealthCheck')
    }
}

private void unScheduleDeviceHealthCheck() {
    unschedule('deviceHealthCheck')
    device.deleteCurrentState('healthStatus')
    logWarn 'device health check is disabled!'
}

// called when any event was received from the Zigbee device in the parse() method.
private void setHealthStatusOnline(Map state) {
    if (state.health == null) { state.health = [:] }
    state.health['checkCtr3']  = 0
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {
        sendHealthStatusEvent('online')
        logInfo 'is now online!'
    }
}

private void deviceHealthCheck() {
    checkDriverVersion(state)
    if (state.health == null) { state.health = [:] }
    int ctr = state.health['checkCtr3'] ?: 0
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) {
            logWarn 'not present!'
            sendHealthStatusEvent('offline')
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})"
    }
    state.health['checkCtr3'] = ctr + 1
}

private void sendHealthStatusEvent(final String value) {
    String descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital')
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" }
    }
}

