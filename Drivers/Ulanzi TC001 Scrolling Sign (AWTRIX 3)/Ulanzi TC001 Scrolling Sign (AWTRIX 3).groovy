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
 * ver. 1.0.1  2024-09-26 kkossev  - added ping, awtrixIP, healthStatus, parse deviceNotification JSON payload; added commonly used text preferences; dismiss; HTTP interface - HTTP stats
 * ver. 1.1.0  2024-09-26 kkossev  - renamed driver to 'Ulanzi TC001 Scrolling Sign (AWTRIX 3)'
 * ver. 1.1.1  2024-10-01 kkossev  - (dev. branch)
 *                                   
 *                                   TODO: 2.3.9.188 - nothing is working?
 *                                   TODO: try the HTTP interface - HTTP stats
 *                                   TODO: sound!
 *                                   TODO: implement HTTP /api/effects /api/transitions /api/loop
 *                                   TODO: replease buttonSelect, buttonLeft, buttonRight with HE standard button events (pushed, released)
*/

// https://github.com/Blueforcer/awtrix3/blob/main/docs/api.md
// https://github.com/Blueforcer/awtrix3/releases  (ulanzi_TC001_0.96.bin)	http://192.168.0.234/

import groovy.transform.Field

@Field static String version = "1.1.1"
@Field static String timeStamp = "2024/10/01 7:27 AM"

metadata {
	definition(name: "Ulanzi TC001 Scrolling Sign (AWTRIX 3)", namespace: "kkossev", author: "Krassimir Kossev", importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Drivers/Ulanzi%20TC001%20Scrolling%20Sign%20(AWTRIX%203)/Ulanzi%20TC001%20Scrolling%20Sign%20(AWTRIX%203).groovy' ) { 
		capability "Initialize"
		capability "Refresh"
		capability "Switch"
		capability "Sensor"
		capability "Notification"
        capability 'HealthCheck'
		
		//command "deviceNotification", ["string"]	// "Notification" capability is already included
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]]
		command 'dismiss'
		command 'sound', [[name: 'Play a sound', type: 'STRING', description: 'RTTTL in JSON format']]
		if (_DEBUG) {
        	command "mqttConnect"
        	command "disconnect"
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
		attribute "buttonSelect", "string"
		attribute "buttonLeft", "string"
		attribute "buttonRight", "string"
	}

	preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables events logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
		input name: 'communicationMode', type: 'enum', title: '<b>Communication Mode</b>', options: CommunicationModes, defaultValue: 'HTTP', description: 'Select the communication mode.'
		if (settings?.communicationMode == 'MQTT') {
			input name: "mqttBroker", type: "text", title: "<b>MQTT Broker</b>", description: "MQTT Broker Address", required: true, defaultValue: '192.168.0.159'
			input name: "mqttPort", type: "number", title: "<b>MQTT Port</b>", description: "MQTT Broker Port", required: true, defaultValue: 1883
			input name: "mqttUsername", type: "text", title: "<b>MQTT Username</b>", description: "MQTT Username", required: false, defaultValue: "mqtt_user"
			input name: "mqttPassword", type: "password", title: "<b>MQTT Password</b>", description: "MQTT Password", required: false, defaultValue: "mqtt_pass"
			input name: "mqttTopic", type: "text", title: "<b>MQTT Topic</b>", description: "MQTT Topic", required: false, defaultValue: "awtrix_21b0c0"
		}
		input name: "awtrixIP", type: "text", title: "<b>AWTRIX IP</b>", description: getAwtrixIpDescription(settings), required: false, defaultValue: '192.168.0.234'

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



@Field static final Boolean _DEBUG = true
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true    // disable it for production

@Field static final String DEVICE_TYPE = 'Message Sign'
@Field static final List<String> TopicsToSubscribe = ['stats', 'stats/currentApp', 'stats/buttonSelect', 'stats/buttonLeft', 'stats/buttonRight']

import groovy.transform.CompileStatic

@Field static final Map<String, String> CommunicationModes = [
	"HTTP" : "HTTP (default)",
	"MQTT" : "MQTT (requires MQTT broker)"
]

static String getAwtrixIpDescription(settings) {
	return "AWTRIX IP Address ${(settings?.communicationMode == 'MQTT') ? ' (optional)' : ''}"
}

@Field static Map<String, String> TopicParsers = [
	"stats" : "parseStats",
	"stats/currentApp" : "parseCurrentApp",
	"stats/buttonSelect" : "parseButtonSelect",
	"stats/buttonLeft" : "parseButtonLeft",
	"stats/buttonRight" : "parseButtonRight",
]

def parse(String description) {
    checkDriverVersion(state)
    updateRxStats(state)
    unscheduleCommandTimeoutCheck(state)
    setHealthStatusOnline(state)

	if (settings?.communicationMode == 'MQTT') {
		Map msg = interfaces.mqtt.parseMessage(description)
		logDebug "Received MQTT message: ${msg}"

		// Extract topic and payload
		String topic = msg.topic
		String payload = msg.payload
		// Exclude the characters before the first '/'
		String[] topicParts = topic.split('/')
		String subTopic = topicParts[1..-1].join('/')
		logTrace "Topic: ${topic} Payload: ${payload} <b>subTopic : ${subTopic}</b>"	
		// find the handler for the topic in the TopicParsers map
		String handler = TopicParsers[subTopic]
		if (handler) {
			logTrace "Calling handler ${handler} for topic ${subTopic} with payload ${payload}"
			this."${handler}"(subTopic, payload)
		} else {
			logDebug "<b> no handler</b> for Topic <b>${topic}</b>, ignoring message."
		}
	} else {
		logWarn "Received HTTP message: ${description} (NO PARSER!)"
	}
	
}// Parse JSON payload

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
	def jsonPayload = new groovy.json.JsonSlurper().parseText(payload)
	processStats(jsonPayload)

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
	sendEvent(name: "brightness", value: jsonPayload.bri)
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

void parseCurrentApp(String topic, String payload) {
	logTrace "parseCurrentApp: topic: ${topic}, payload:${payload}"
	sendEvent(name: "currentApp", value: payload)
}

void parseButtonSelect(String topic, String payload) {
	logTrace "parseButtonSelect: topic: ${topic}, payload:${payload}"
	sendEvent(name: "buttonSelect", value: payload)
}

void parseButtonLeft(String topic, String payload) {
	logTrace "parseButtonLeft: topic: ${topic}, payload:${payload}"
	sendEvent(name: "buttonLeft", value: payload)
}

void parseButtonRight(String topic, String payload) {
	logTrace "parseButtonRight: topic: ${topic}, payload:${payload}"
	sendEvent(name: "buttonRight", value: payload)
}



void installed() {
	logDebug "Installed"
	initialize()
}

@Field static List<String> StatAttributesList = ['app', 'battery', 'brightness', 'hum', 'indicator1', 'indicator2', 'indicator3', 'ip_address', 'ldr_raw', 'lux', 'matrix', 'messages', 'ram', 'temp', 'type', 'uid', 'uptime', 'version', 'wifi_signal']

void updated() {
	logDebug "Updated"
    checkDriverVersion(state)
	if (settings?.extendedStats != true) {
		String attributesDeleted = ''
		StatAttributesList.each { it -> 
			attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") 
		}
		logDebug "Deleted attributes: ${attributesDeleted}";
	}
}

void initialize() {
	if (settings?.communicationMode == 'MQTT') {
		logDebug "Initializing MQTT connection... mqttBroker : ${settings?.mqttBroker} mqttPort : ${settings?.mqttPort}"
		try {
			interfaces.mqtt.connect("tcp://${settings?.mqttBroker}:${settings?.mqttPort}", "hubitat_${device.deviceNetworkId}", settings?.mqttUsername, settings?.mqttPassword)
			updateTxStats(state)
			//interfaces.mqtt.subscribe("${settings?.mqttTopic}/#")
			subscribeMultipleTopics(TopicsToSubscribe)
		} catch (e) {
			logError "MQTT Initialization Error: ${e.message}"
		}
	} else {
		logWarn "initialize: HTTP not implemented (yet!)"
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

	if (fullInit || settings?.communicationMode == null) { device.updateSetting('communicationMode', [value: 'HTTP', type: 'enum']) }
	if (fullInit || settings?.mqttBroker == null) { device.updateSetting('mqttBroker', [value: '192.168.0.159', type: 'text']) }
	if (fullInit || settings?.mqttPort == null) { device.updateSetting('mqttPort', [value: 1883, type: 'number']) }
	if (fullInit || settings?.mqttUsername == null) { device.updateSetting('mqttUsername', [value: 'mqtt_user', type: 'text']) }
	if (fullInit || settings?.mqttPassword == null) { device.updateSetting('mqttPassword', [value: 'mqtt_pass', type: 'password']) }
	if (fullInit || settings?.mqttTopic == null) { device.updateSetting('mqttTopic', [value: 'awtrix_21b0c0', type: 'text']) }
	if (fullInit || settings?.awtrixIP == null) { device.updateSetting('awtrixIP', [value: '192.168.0.234', type: 'text']) }
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
	if (settings?.communicationMode != 'MQTT') {
		logWarn "subscribeMultipleTopics: HTTP not implemented (yet!)"
		return
	}
	String subscribedTopics = ""
	topics.each { topic ->
		String fullTopic = "${settings?.mqttTopic}/${topic}"
		interfaces.mqtt.subscribe(fullTopic)
		subscribedTopics += "${fullTopic}, "
	}
	logDebug "Subscribed to topics: ${subscribedTopics}"
}


void disconnect() {
	if (settings?.communicationMode != 'MQTT') {
		logWarn "disconnect: HTTP not implemented (yet!)"
		return
	}
	interfaces.mqtt.disconnect()
	updateTxStats(state)
	logDebug "disconnect: Disconnected from broker ${settings.mqttBroker} (${settings.mqttTopic})."
}

void subscribe(String topicParam) {
	if (settings?.communicationMode != 'MQTT') {
		logWarn "subscribe: HTTP not implemented (yet!)"
		return
	}
	String topic = settings?.mqttTopic + '/' + topicParam
	logDebug "Subscribing to topic ${topic}"
	interfaces.mqtt.subscribe(topic)
	updateTxStats(state)
}

void unsubscribe(String topicParam) {
	if (settings?.communicationMode != 'MQTT') {
		logWarn "unsubscribe: HTTP not implemented (yet!)"
		return
	}
	String topic = settings?.mqttTopic + '/' + topicParam
	logDebug "Unsubscribing from topic ${topic}"
	interfaces.mqtt.unsubscribe(topic)
	updateTxStats(state)
}



def on() {
	if (settings?.communicationMode == 'MQTT') {
		logDebug "on: publishing power on"
		publish("power", "{'power': true}")
		sendEvent(name: "switch", value: "on")
	} else {
		logWarn "on: HTTP not implemented (yet!)"
	}
}

def off() {
	if (settings?.communicationMode == 'MQTT') {
		logDebug "off: publishing power off"
		publish("power", "{'power': false}")
		sendEvent(name: "switch", value: "off")
	} else {
		logWarn "off: HTTP not implemented (yet!)"
	}
}

void ping() {
	logDebug "settings?.awtrixIP : ${settings?.awtrixIP}"
    if (state.stats == null ) { state.stats = [:] } ; state.stats['pingTime'] = new Date().getTime()
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true
    scheduleCommandTimeoutCheck()
	// try to ping the device, even if the communication mode is MQTT ? TODO
	if (settings?.awtrixIP != null && settings?.awtrixIP != "") {
		sendPing(settings?.awtrixIP)
	} else if (settings?.mqttBroker != null && settings?.mqttBroker != "") {
		sendPing(settings?.mqttBroker)
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
	if (settings?.communicationMode == 'MQTT') {
		logWarn "Refreshing via MQTT protocol is not supported for this device."
		return
	}
	// HTTP refresh - get the stats
	fetchAndProcessStats()
}

def fetchAndProcessStats() {
    //def url = "http://192.168.0.234/api/stats"
	String url = "http://${settings?.awtrixIP}/api/stats"
    try {
        httpGet(url) { response ->
            if (response.status == 200) {
                log.trace "response.data: ${response.data}"
                def stats
                if (response.data instanceof Map) {
                    // If response.data is already a map, use it directly
                    stats = response.data
                } else {
                    // Otherwise, parse the response data as JSON
                    JsonSlurper jsonSlurper = new JsonSlurper()
                    stats = jsonSlurper.parseText(response.data.toString())
                }
                processStats(stats)
            } else {
                log.error "Failed to fetch stats. HTTP status: ${response.status}"
            }
        }
    } catch (Exception e) {
        log.error "Error fetching stats: ${e.message}"
    }
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
		parsedMessageMap.text = message /*
		parsedMessageMap.rainbow = settings?.rainbowEffect
		parsedMessageMap.duration = settings?.duration
		parsedMessageMap.repeat = settings?.repeat
		parsedMessageMap.hold = settings?.hold
		parsedMessageMap.stack = settings?.stack
		parsedMessageMap.wakeup = settings?.wakeup
		parsedMessageMap.noScroll = settings?.noScroll
		parsedMessageMap.color = COLORS[settings?.color]?.hex */

		logDebug "deviceNotification: parsedMessageMap: ${parsedMessageMap}"
		message = JsonOutput.toJson(parsedMessageMap)
		logDebug "deviceNotification: message: ${message}"
	}
	//
    publish("notify", message)

}

void dismiss() {
	if (settings?.communicationMode == 'MQTT') {
		publish("notify/dismiss", "")
	} else {
		logWarn "dismiss: HTTP not implemented (yet!)"
	}
}

void sound(String soundParam) {
	String sound = soundParam
	if (sound == null || sound == "" || sound == "{}") {
		sound = "{Knock:d=32,o=4,b=100:e,4p,e,p,e,8p,e,4p,e,8p,e,4p}"
	}
	logDebug "sound: ${soundParam} -> ${sound}"
	if (settings?.communicationMode == 'MQTT') {
		publish("rtttl", "{'sound': '${sound}'}")
	} else if (settings?.communicationMode == 'HTTP') {
		try {
			String payload = "${sound}"
			Map params = [
				uri: 'http://' + settings?.awtrixIP + '/api/rtttl',
				contentType: 'application/json',
				requestContentType: 'application/json',
				body: [payload]
			]
			log.trace "sound: Sending HTTP POST request to ${params.uri} with payload: ${payload}"
			asynchttpPost('handleHttpResponse', params)
		} catch (Exception e) {
			logError "sound: HTTP request failed: ${e.message}"
		}
	} else {
		logWarn "sound: Unsupported communication mode: ${settings?.communicationMode}"
	}
}

void handleHttpResponse(response, data) {
	if (response.hasError()) {
		logError "sound: HTTP request failed: ${response.getErrorMessage()}"
	} else {
		logDebug "sound: HTTP response code: ${response.status}"
	}
}

void publish(String topicParam, String payload) {
	if (settings?.communicationMode != 'MQTT') {
		logWarn "publish: HTTP not implemented (yet!)"
		return
	}
	String topic = settings?.mqttTopic + '/' + topicParam
	logTrace "Publishing to topic ${topic} : ${payload}"
	//interfaces.mqtt.publish(topic, payload)
}

void sendMqttMessage(topic, payload) {
	if (settings?.communicationMode != 'MQTT') {
		logWarn "sendMqttMessage: HTTP not implemented (yet!)"
		return
	}
	try {
		interfaces.mqtt.publish(topic, payload)
		logDebug "Published MQTT message: ${topic} - ${payload}"
	} catch (e) {
		logError "MQTT Publish Error: ${e.message}"
	}
	updateTxStats(state)
}


// ---------------- credits to Andrew Davison (BirdsLikeWires) ----------------

void mqttConnect() {
	if (settings?.communicationMode != 'MQTT') {
		logWarn "mqttConnect: HTTP not implemented (yet!)"
		return
	}
    logDebug "Connecting to the MQTT broker ${settings?.mqttBroker} (${settings?.mqttTopic})"
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

	    logDebug "Subscribing to topic ${settings?.mqttTopic}"
		mqttInt.subscribe(settings?.mqttTopic)

	} catch (Exception e) {
		if (settings?.mqttBroker == null || settings?.mqttBroker == "") {
			logWarn "mqttConnect : No broker configured."
		} else {
			logError "mqttConnect : ${e.message}"
		}
	}
} 

void mqttClientStatus(String status) {
	if (settings?.communicationMode != 'MQTT') {
		logWarn "mqttClientStatus: HTTP not implemented (yet!)"
		return
	}
    logDebug "mqttClientStatus : ${status}"
	if (status.indexOf('Connection succeeded') >= 0) {
		logDebug "mqttClientStatus : Connection to broker ${settings?.mqttBroker} (${settings?.mqttTopic}) is live."
	} else {
		logError "mqttClientStatus : ${status}"
	}
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
        String cron = getCron( intervalMins * 60 )
        schedule(cron, 'deviceHealthCheck')
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes"
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

