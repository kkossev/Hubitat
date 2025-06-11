 /**
 *  ESPHome Discovery - application for Hubitat Elevation hub
 *
 *  https://community.hubitat.com/t/project-alpha-device-health-status/111817
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Based on Eric Maycock (erocm123) "Service Manager for Sonoff switches"
 *
 *  ver. 1.0.0 2025-06-06 kkossev - first version:
 *
 *                                  TODO: 
 */

import groovy.transform.Field

final String version() { '1.0.0' }
final String timeStamp() { '2025/06/07 9:51 PM' }

@Field static final Boolean _DEBUG = true
@Field static final Integer ESPHOME_PORT = 6053 // default port for ESPHome devices
//@Field static final Integer ESPHOME_PORT = 80
//@Field static final String  ESPHOME_MDNS = "_awtrix._tcp"
@Field static final String  ESPHOME_MDNS = "_raop._tcp"
//@Field static final String  ESPHOME_MDNS = "_services._dns-sd._udp"
//@Field static final String  ESPHOME_MDNS = "_aqara._tcp" // mDNS service name for ESPHome devices


definition(
    name: "ESPHome Discovery",
    namespace: "kkossev",
    author: "Krassimir Kossev",
    description: "Service Manager for ESPHome devices",
    category: "Convenience",
    iconUrl:   "",
    iconX2Url: "",
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Apps/Device%20Health%20Status.groovy',
    documentationLink: 'https://community.hubitat.com/t/alpha-device-health-status/111817/1'
)



preferences {
	page(name: "mainPage")
    page(name: "configurePDevice")
    page(name: "deletePDevice")
    page(name: "changeName")
    page(name: "discoveryPage", title: "ESPHome Devices Discovery", content: "discoveryPage", refreshTimeout:10)
    page(name: "addDevices", title: "Add ESPHome Devices", content: "addDevices")
    page(name: "manuallyAdd")
    page(name: "manuallyAddConfirm")
    page(name: "deviceDiscovery")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: "Discover your <b>ESPHome</b> Devices <i>(app ver. ${driverVersionAndTimeStamp()})</i>", nextPage: null, uninstall: true, install: true) {
        logDebug "mainPage() called"
        section("Configure"){
           href "deviceDiscovery", title:"Discover Devices", description:""
           href "manuallyAdd", title:"Manually Add Device", description:""
        }
        section("Installed Devices"){
            getChildDevices().sort({ a, b -> a["deviceNetworkId"] <=> b["deviceNetworkId"] }).each {
                href "configurePDevice", title:"$it.label", description:"", params: [did: it.deviceNetworkId]
            }
        }
        section ("Options") {
            input "logEnable", "bool", title: "Enable debug logging", defaultValue: true, required: false
            input "txtEnable", "bool", title: "Enable descriptionText logging", defaultValue: true, required: false
        }
    }
}

def configurePDevice(params){
   if (params?.did || params?.params?.did) {
      if (params.did) {
         state.currentDeviceId = params.did
         state.currentDisplayName = getChildDevice(params.did)?.displayName
      } else {
         state.currentDeviceId = params.params.did
         state.currentDisplayName = getChildDevice(params.params.did)?.displayName
      }
   }  
   if (getChildDevice(state.currentDeviceId) != null) getChildDevice(state.currentDeviceId).configure()
   dynamicPage(name: "configurePDevice", title: "Configure ESPHome Devices created with this app", nextPage: null) {
		section {
            app.updateSetting("${state.currentDeviceId}_label", getChildDevice(state.currentDeviceId).label)
            input "${state.currentDeviceId}_label", "text", title:"Device Name", description: "", required: false
            href "changeName", title:"Change Device Name", description: "Edit the name above and click here to change it"
        }
        section {
              href "deletePDevice", title:"Delete $state.currentDisplayName", description: ""
        }
   }
}

@Field static final Map DEVICE_TYPES = [
    "ESPHome Wifi Switch" : "ESPHome Wifi Switch",
    "Sonoff TH Wifi Switch": "Sonoff TH Wifi Switch",
    "Sonoff POW Wifi Switch": "Sonoff POW Wifi Switch",
    "Sonoff Dual Wifi Switch": "Sonoff Dual Wifi Switch",
    "Sonoff 4CH Wifi Switch": "Sonoff 4CH Wifi Switch",
    "Sonoff 2CH Wifi Switch": "Sonoff 2CH Wifi Switch"
]

def manuallyAdd(){
   dynamicPage(name: "manuallyAdd", title: "Manually add an ESPHome device", nextPage: "manuallyAddConfirm") {
        section {
            paragraph "This process will manually create an ESPHome device based on the entered IP address. The SmartApp needs to then communicate with the device to obtain additional information from it. Make sure the device is on and connected to your wifi network."
            input "deviceType", "enum", title:"Device Type", description: "", required: false, options: DEVICE_TYPES.keySet()
            input "ipAddress", "text", title:"IP Address", description: "", required: false 
        }
    }
}

/**
 * Manually adds an ESPHome device based on the provided IP address.
 *
 * This method validates the provided IP address and, if valid, creates a new ESPHome device
 * with the specified IP and port. If the IP address is invalid, it displays an error message.
 *
 * Preconditions:
 * - The `ipAddress` variable must contain the IP address to be validated and used.
 * - The `ESPHOME_PORT` variable must contain the port number for the ESPHome device.
 * - The `convertIPtoHex` and `convertPortToHex` methods must be defined to convert IP and port
 *   values to their hexadecimal representations.
 * - The `addChildDevice` method must be available to add the ESPHome device.
 * - The `app.updateSetting` method must be available to reset the `ipAddress` setting.
 *
 * Behavior:
 * - If the IP address matches the IPv4 format (e.g., "192.168.1.1"):
 *   - Logs the creation of the ESPHome device.
 *   - Adds the ESPHome device with the specified IP and port.
 *   - Resets the `ipAddress` setting to an empty string.
 *   - Displays a confirmation message to the user.
 * - If the IP address is invalid:
 *   - Displays an error message to the user indicating the IP address is not valid.
 *
 * Dynamic Pages:
 * - Displays a confirmation page if the device is successfully added.
 * - Displays an error page if the IP address is invalid.
 *
 * Note:
 * - The `deviceType` variable is optional. If not provided, it defaults to "ESPHome Device".
 * - The `location.hubs[0].id` is used to associate the device with the first hub in the location.
 */
def manuallyAddConfirm(){
   if ( ipAddress =~ /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/) {
       log.debug "Creating ESPHome Device with dni: ${convertIPtoHex(ipAddress)}:${convertPortToHex(ESPHOME_PORT.toString())}"
       addChildDevice("esphome", deviceType ? deviceType : "ESPHome Device", "${convertIPtoHex(ipAddress)}:${convertPortToHex("80")}", location.hubs[0].id, [
           "label": (deviceType ? deviceType : "ESPHome Device") + " (${ipAddress})",
           "data": [
           "ip": ipAddress,
           "port": ESPHOME_PORT.toString()
           ]
       ])
   
       app.updateSetting("ipAddress", "")
            
       dynamicPage(name: "manuallyAddConfirm", title: "Manually add a ESPHome device", nextPage: "mainPage") {
		   section {
			   paragraph "The device has been added. Press next to return to the main page."
	    	}
       }
    } else {
        dynamicPage(name: "manuallyAddConfirm", title: "Manually add a ESPHome device", nextPage: "mainPage") {
		    section {
			    paragraph "The entered ip address is not valid. Please try again."
		    }
        }
    }
}

/**
 * Deletes the currently selected physical device and handles any errors that may occur during the process.
 *
 * This method performs the following actions:
 * 1. Unsubscribes from any subscriptions associated with the device.
 * 2. Deletes the child device identified by `state.currentDeviceId`.
 * 3. Displays a dynamic page summarizing the deletion process.
 *
 * If an error occurs during the deletion process, it:
 * - Displays a dynamic page with an error message extracted from the exception.
 *
 * @throws Exception If an error occurs during the deletion of the child device.
 */
def deletePDevice(){
    try {
        unsubscribe()
        deleteChildDevice(state.currentDeviceId)
        dynamicPage(name: "deletePDevice", title: "Deletion Summary", nextPage: "mainPage") {
            section {
                paragraph "The device has been deleted. Press next to continue"
            } 
        }
    
	} catch (e) {
        dynamicPage(name: "deletePDevice", title: "Deletion Summary", nextPage: "mainPage") {
            section {
                paragraph "Error: ${(e as String).split(":")[1]}."
            } 
        }
    
    }
}

def changeName(){
    def thisDevice = getChildDevice(state.currentDeviceId)
    thisDevice.label = settings["${state.currentDeviceId}_label"]

    dynamicPage(name: "changeName", title: "Change Name Summary", nextPage: "mainPage") {
	    section {
            paragraph "The device has been renamed. Press \"Next\" to continue"
        }
    }
}

void logMDNSEntries() {
    def entries = getMDNSEntries(ESPHOME_MDNS)
    if (entries) {
        log.debug "MDNS Entries for ${ESPHOME_MDNS}: ${entries}"
    } else {
        log.debug "No MDNS entries found for ${ESPHOME_MDNS}"
    }
}


def hubRestartHandler(evt) {
    log.info "hubRestartHandler() called - Hub restarted. Scheduling mDNSDiscover in 60s"
    runIn(60, "mDNSDiscover")
}

def discoveryPage(){
    log.debug "discoveryPage() called"
    return deviceDiscovery()
}

/**
 * Discovers ESPHome devices on the network and provides a dynamic page for device selection.
 *
 * This method performs the following tasks:
 * - Logs the method call and any provided parameters.
 * - Logs mDNS entries and initiates mDNS discovery.
 * - Retrieves the list of discovered devices.
 * - Manages the refresh count and resets the state if necessary.
 * - Periodically sends discovery and verification requests.
 * - Generates a dynamic page for users to select discovered devices.
 *
 * @param params A map of optional parameters. Supported keys:
 *               - "reset": If set to "true", clears the list of discovered devices and resets the refresh count.
 *
 * @return A dynamicPage object that displays the discovery status and allows device selection.
 *
 * State Variables:
 * - state.deviceRefreshCount: Tracks the number of refresh cycles.
 * - state.devices: Stores the list of discovered devices.
 *
 * Key Logic:
 * - Devices are rediscovered every 5 refresh cycles.
 * - Device verification is performed every 3 refresh cycles, except during discovery cycles.
 * - If no devices are found after 25 refresh cycles or if reset is requested, the state is cleared.
 *
 * Dynamic Page Sections:
 * - Displays a message about the discovery process.
 * - Provides a dropdown for selecting discovered devices.
 * - Includes an option to reset the list of discovered devices.
 */
def deviceDiscovery(params=[:])
{
    log.debug "deviceDiscovery() called with params: ${params}"
    logMDNSEntries()
    mDNSDiscover()
	def devices = devicesDiscovered()
    
	int deviceRefreshCount = !state.deviceRefreshCount ? 0 : state.deviceRefreshCount as int
	state.deviceRefreshCount = deviceRefreshCount + 1
	def refreshInterval = 20
    
	def options = devices ?: []
	def numFound = options.size() ?: 0

	if (/*(numFound == 0 && state.deviceRefreshCount > 25) || */params.reset == "true") {
    	log.trace "Cleaning old device memory"
    	state.devices = [:]
        state.deviceRefreshCount = 0
        app.updateSetting("selectedDevice", "")
    }

    log.debug "deviceDiscovery() - deviceRefreshCount: ${deviceRefreshCount}, numFound: ${numFound}, options: ${options}"
    //log.debug "deviceDiscovery() - calling mDNSSubscribe()"
	//mDNSSubscribe()

	//ESPHome discovery request every 15 //25 seconds
	if((deviceRefreshCount % 5) == 0) {
		discoverDevices()
	}

	//setup.xml request every 3 seconds except on discoveries
	if(((deviceRefreshCount % 3) == 0) && ((deviceRefreshCount % 5) != 0)) {
		verifyDevices()
	}

	return dynamicPage(name:"deviceDiscovery", title:"Discovery Started!", nextPage:"addDevices", refreshInterval:refreshInterval, uninstall: true) {
		section("Please wait while we discover your ESPHome devices. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
			input "selectedDevices", "enum", required:false, title:"Select ESPHome Device (${numFound} found)", multiple:true, options:options
		}
        section("Options") {
			href "deviceDiscovery", title:"Reset list of discovered devices", description:"", params: ["reset": "true"]
		}
	}
}

/**
 * Retrieves a map of discovered and verified devices.
 *
 * This method fetches a list of verified devices and constructs a map where
 * the keys are the MAC addresses of the devices and the values are their names.
 *
 * @return A map containing the MAC addresses as keys and device names as values.
 */
Map devicesDiscovered() {

	def vdevices = getVerifiedDevices()
	def map = [:]
	vdevices.each {
		def value = "${it.value.name}"
		def key = "${it.value.mac}"
		map["${key}"] = value
	}
	map
}

/**
 * Retrieves a list of verified devices.
 *
 * This method filters the devices returned by the `getDevices()` method
 * and includes only those devices that have their `verified` property set to `true`.
 *
 * @return A list of devices where the `verified` property is `true`.
 */
def getVerifiedDevices() {
	getDevices().findAll{ it?.value?.verified == true }
}

private discoverDevices() {
	log.warn "discoverDevices() not implemented!"
}

def configured() {
}

def buttonConfigured(idx) {
	return settings["lights_$idx"]
}

def isConfigured(){
   if(getChildDevices().size() > 0) return true else return false
}

def isVirtualConfigured(did){ 
    def foundDevice = false
    getChildDevices().each {
       if(it.deviceNetworkId != null){
       if(it.deviceNetworkId.startsWith("${did}/")) foundDevice = true
       }
    }
    return foundDevice
}

private virtualCreated(number) {
    if (getChildDevice(getDeviceID(number))) {
        return true
    } else {
        return false
    }
}

private getDeviceID(number) {
    return "${state.currentDeviceId}/${app.id}/${number}"
}

def installed() {
    log.info "installed() called"
    subscribe(location, "systemStart", "hubRestartHandler")
    runIn(5, "mDNSDiscover")
}

def uninstalled() {
    log.info "uninstalled() called"
    unsubscribe()
    unschedule()
    state.remove("latestEntries")
    state.remove("mdnsSubscribed")
}

def updated() {
    log.info "updated() called"
    unsubscribe()
    unschedule()
    subscribe(location, "systemStart", "hubRestartHandler")
    runIn(5, "mDNSDiscover")
}


void logsOff() {
    log.info "logsOff() called - disabling debug logging"
    logEnable = false
    app.updateSetting("logEnable", [value: false, type: "bool"])
    log.info "Debug logging disabled"
}

def initialize() {
    log.info "initialize() called"
    if (logEnable == null) logEnable = true
    if (!state.mdnsSubscribed) {
        registerMDNSListener(ESPHOME_MDNS)
        state.mdnsSubscribed = true
        log.info "Registered mDNS listener for ${ESPHOME_MDNS}"
    }
    runIn(2, "mDNSDiscover")
    runEvery1Minute("mDNSDiscover")
}

def mDNSSubscribe() {
    log.debug "mDNSSubscribe(): Calling registerMDNSListener() with ${ESPHOME_MDNS}"
    registerMDNSListener(ESPHOME_MDNS)
}

def mDNSDiscover() {
    log.info "mDNSDiscover() called"
    def entries = getMDNSEntries(ESPHOME_MDNS)
    if (state.devices == null) {
        state.devices = [:]
    }
    if (entries && entries.size() > 0) {
        entries.each { entry ->
            state.devices[entry.toString()] = entry 
            /*
            if (!state.devices.find { it.toString() == entry.toString() }) {
                state.devices.add(entry as Map)
                log.info "New device added to discoveredDevices: ${entry}"
            }
            */
        }
    } else {
        log.warn "No mDNS entries found for ${ESPHOME_MDNS}"
    }
}


def ssdpHandler(evt) {
    log.debug "ssdpHandler() called with event: ${evt}"
    log.debug "ssdpHandler() - description: ${evt.description}, ssdpUSN: ${evt.ssdpUSN}, ssdpPath: ${evt.ssdpPath}, networkAddress: ${evt.networkAddress}, deviceAddress: ${evt.deviceAddress}"
    def description = evt.description
    def hub = evt?.hubId
    def parsedEvent = parseLanMessage(description)
    parsedEvent << ["hub":hub]

    def devices = getDevices()
    
    String ssdpUSN = parsedEvent.ssdpUSN.toString()
    
    if (devices."${ssdpUSN}") {
        def d = devices."${ssdpUSN}"
        def child = getChildDevice(parsedEvent.mac)
        def childIP
        def childPort
        if (child) {
            childIP = child.getDeviceDataByName("ip")
            childPort = child.getDeviceDataByName("port").toString()
            log.debug "Device data: ($childIP:$childPort) - reporting data: (${convertHexToIP(parsedEvent.networkAddress)}:${convertHexToInt(parsedEvent.deviceAddress)})."
            if("${convertHexToIP(parsedEvent.networkAddress)}" != "0.0.0.0"){
               if(childIP != convertHexToIP(parsedEvent.networkAddress) || childPort != convertHexToInt(parsedEvent.deviceAddress).toString()){
                  log.debug "Device data (${child.getDeviceDataByName("ip")}) does not match what it is reporting(${convertHexToIP(parsedEvent.networkAddress)}). Attempting to update."
                  child.sync(convertHexToIP(parsedEvent.networkAddress), convertHexToInt(parsedEvent.deviceAddress).toString())
               }
            } else {
               log.debug "Device is reporting ip address of ${convertHexToIP(parsedEvent.networkAddress)}. Not updating." 
            }
        }

        if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
            d.networkAddress = parsedEvent.networkAddress
            d.deviceAddress = parsedEvent.deviceAddress
        }
    } else {
        devices << ["${ssdpUSN}": parsedEvent]
    }
}

void verifyDevices() {
    def devices = getDevices().findAll { it?.value?.verified != true }
    logDebug "verifyDevices() called with devices: ${devices}"
    devices.each {
        /*
        def ip = convertHexToIP(it.value.networkAddress)
        def port = convertHexToInt(it.value.deviceAddress)
        String host = "${ip}:${port}"
        sendHubCommand(new hubitat.device.HubAction("""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: $host\r\n\r\n""", hubitat.device.Protocol.LAN, host, [callback: deviceDescriptionHandler]))
        */
    }
}

def getDevices() {
    state.devices = state.devices ?: [:]
    return state.devices
}

/**
 * Handles the response from a device's description.xml request.
 *
 * This method processes the XML response from a device and extracts relevant information
 * such as the friendly name, model name, serial number, and IP address. It verifies if the
 * device is an ESPHome device and updates the device list accordingly.
 *
 * @param hubResponse The response object from the Hubitat hub containing the XML data.
 *
 * The method performs the following steps:
 * - Logs the received hubResponse for debugging purposes.
 * - Parses the XML body of the response to extract device details.
 * - Checks if the device model name starts with "ESPHome".
 * - Searches for the device in the existing device list using its UDN (Unique Device Name).
 * - If the device exists, updates its details (name, serial number, and verification status).
 * - Logs an error if the device is not found in the existing device list.
 *
 * Note:
 * - The method assumes the response contains valid XML data with a specific structure.
 * - The `convertHexToIP` method is used to convert the IP address from hexadecimal format.
 */
void deviceDescriptionHandler(hubitat.device.HubResponse hubResponse) {
    logDebug "deviceDescriptionHandler() called with hubResponse: ${hubResponse}"
	//log.trace "description.xml response (application/xml)"
	def body = hubResponse.xml
    log.debug body?.device?.friendlyName?.text()
	if (body?.device?.modelName?.text().startsWith("ESPHome")) {
		def devices = getDevices()
		def device = devices.find {it?.key?.contains(body?.device?.UDN?.text())}
		if (device) {
			device.value << [name:body?.device?.friendlyName?.text() + " (" + convertHexToIP(hubResponse.ip) + ")", serialNumber:body?.device?.serialNumber?.text(), verified: true]
		} else {
			log.error "/description.xml returned a device that didn't exist"
		}
	}
}

def addDevices() {
    logDebug "addDevices() called"
    def devices = getDevices()
    def sectionText = ""

    selectedDevices.each { dni ->bridgeLinking
        def selectedDevice = devices.find { it.value.mac == dni }
        def d
        if (selectedDevice) {
            d = getChildDevices()?.find {
                it.deviceNetworkId == selectedDevice.value.mac
            }
        }
        
        if (!d) {
            log.debug selectedDevice
            log.debug "Creating ESPHome Device with dni: ${selectedDevice.value.mac}"

            def deviceHandlerName
            if (selectedDevice?.value?.name?.startsWith("Sonoff TH"))
                deviceHandlerName = "Sonoff TH Wifi Switch"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff POW"))
                deviceHandlerName = "Sonoff POW Wifi Switch"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff Dual "))
                deviceHandlerName = "Sonoff 2CH - Tasmota"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff Dual"))
                deviceHandlerName = "Sonoff Dual Wifi Switch"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff 4CH "))
                deviceHandlerName = "Sonoff 4Ch - Tasmota"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff 4CH"))
                deviceHandlerName = "Sonoff 4CH Wifi Switch"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff IFan02"))
                deviceHandlerName = "Sonoff IFan02 Wifi Controller"
			else if (selectedDevice?.value?.name?.startsWith("Sonoff S31"))
                deviceHandlerName = "Sonoff S31 - Tasmota"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff S2"))
                deviceHandlerName = "Sonoff S20 - Tasmota"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff SC"))
                deviceHandlerName = "Sonoff SC - Tasmota"
            else if (selectedDevice?.value?.name?.startsWith("Sonoff Bridge"))
                deviceHandlerName = "Sonoff Bridge - Tasmota"
			else if (selectedDevice?.value?.name?.startsWith("quired"))
                deviceHandlerName = "Sonoff 2CH - Tasmota"
            else 
                deviceHandlerName = "Sonoff Wifi Switch"
            try {
            def newDevice = addChildDevice("esphome", deviceHandlerName, selectedDevice.value.mac, selectedDevice?.value.hub, [
                "label": selectedDevice?.value?.name ?: "Sonoff Wifi Switch",
                "data": [
                    "mac": selectedDevice.value.mac,
                    "ip": convertHexToIP(selectedDevice.value.networkAddress),
                    "port": "" + Integer.parseInt(selectedDevice.value.deviceAddress,16)
                ]
            ])
                sectionText = sectionText + "Succesfully added ESPHome device with ip address ${convertHexToIP(selectedDevice.value.networkAddress)} \r\n"
            } catch (e) {
                sectionText = sectionText + "An error occured ${e} \r\n"
            }
            
        }
        
	} 
    log.debug sectionText
        return dynamicPage(name:"addDevices", title:"Devices Added", nextPage:"mainPage",  uninstall: true) {
        if(sectionText != ""){
		section("Add ESPHome Results:") {
			paragraph sectionText
		}
        }else{
        section("No devices added") {
			paragraph "All selected devices have previously been added"
		}
        }
}
    }

/*
def uninstalled() {
    unsubscribe()
    getChildDevices().each {
        deleteChildDevice(it.deviceNetworkId)
    }
}
*/


private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    return hexport
}

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp().split(' ')[0] }

void logDebug(String msg) { if (logEnable) { log.debug msg } }

void logWarn(String msg) { if (logEnable) { log.warn msg } }

void logInfo(String msg) { if (txtEnable) { log.info msg } }

def runDiagnosticScan() {
    log.warn "[mDNS Diagnostic] Running immediate one-shot mDNS scan..."
    try {
        registerMDNSListener(ESPHOME_MDNS)
        pauseExecution(3000)  // wait 3 seconds for responses
        def entries = getMDNSEntries(ESPHOME_MDNS)
        if (entries && entries.size() > 0) {
            log.warn "[mDNS Diagnostic] Found ${entries.size()} entries:"
            entries.each { log.warn "[mDNS Diagnostic] ➤ ${it}" }
        } else {
            log.warn "[mDNS Diagnostic] No entries found for ${ESPHOME_MDNS}"
        }
    } catch (e) {
        log.error "[mDNS Diagnostic] Exception during scan: ${e.message}"
    }
}


