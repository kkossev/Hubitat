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
final String timeStamp() { '2025/06/06 8:42 PM' }

@Field static final Boolean _DEBUG = true
//@Field static final Integer ESPHOME_PORT = 6053 // default port for ESPHome devices
@Field static final Integer ESPHOME_PORT = 80
@Field static final String  ESPHOME_URN = "urn:schemas-upnp-org:device:esphome:1" // urn for ESPHome devices

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
    page(name: "discoveryPage", title: "Device Discovery", content: "discoveryPage", refreshTimeout:10)
    page(name: "addDevices", title: "Add ESPHome Switches", content: "addDevices")
    page(name: "manuallyAdd")
    page(name: "manuallyAddConfirm")
    page(name: "deviceDiscovery")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: "Manage your ESPHome switches", nextPage: null, uninstall: true, install: true) {
        logDebug "mainPage() called"
        section ("Options") {
            input "logEnable", "bool", title: "Enable debug logging", defaultValue: true, required: false
            input "txtEnable", "bool", title: "Enable descriptionText logging", defaultValue: true, required: false
        }
        section("Configure"){
           href "deviceDiscovery", title:"Discover Devices", description:""
           href "manuallyAdd", title:"Manually Add Device", description:""
        }
        section("Installed Devices"){
            getChildDevices().sort({ a, b -> a["deviceNetworkId"] <=> b["deviceNetworkId"] }).each {
                href "configurePDevice", title:"$it.label", description:"", params: [did: it.deviceNetworkId]
            }
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

def manuallyAdd(){
   dynamicPage(name: "manuallyAdd", title: "Manually add an ESPHome device", nextPage: "manuallyAddConfirm") {
		section {
			paragraph "This process will manually create an ESPHome device based on the entered IP address. The SmartApp needs to then communicate with the device to obtain additional information from it. Make sure the device is on and connected to your wifi network."
            input "deviceType", "enum", title:"Device Type", description: "", required: false, options: ["ESPHome Wifi Switch","Sonoff TH Wifi Switch","Sonoff POW Wifi Switch","Sonoff Dual Wifi Switch","Sonoff 4CH Wifi Switch","Sonoff 2CH Wifi Switch"]
            input "ipAddress", "text", title:"IP Address", description: "", required: false 
		}
    }
}

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
    log.debug getMDNSEntries("_awtrix._tcp")
    def entries = getMDNSEntries("_awtrix._tcp")
    if (entries) {
        log.debug "MDNS Entries for _awtrix._tcp: ${entries}"
    } else {
        log.debug "No MDNS entries found for _awtrix._tcp"
    }
}


void hubRestartHandler(evt) {
    registerMDNSListener("_awtrix._tcp")
}



def discoveryPage(){
    log.debug "discoveryPage() called"
    return deviceDiscovery()
}

def deviceDiscovery(params=[:])
{
    log.debug "deviceDiscovery() called with params: ${params}"
    logMDNSEntries()
    logDebug "deviceDiscovery() called with params: ${params}"
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
    log.debug "deviceDiscovery() - calling ssdpSubscribe()"
	ssdpSubscribe()

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

def getVerifiedDevices() {
	getDevices().findAll{ it?.value?.verified == true }
}

private discoverDevices() {
    logDebug "discoverDevices() called"
	sendHubCommand(new hubitat.device.HubAction("lan discovery urn:schemas-upnp-org:device:Basic:1", hubitat.device.Protocol.LAN))
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
	initialize()
}

def updated() {
	unsubscribe()
    unschedule()
	initialize()
    subscribe(location, "systemStart", "hubRestartHandler")
}

def initialize() {
    ssdpSubscribe()
    registerMDNSListener("_awtrix._tcp")
    log.debug "initialize(): Calling registerMDNSListener() with _awtrix._tcp"
    runEvery5Minutes("ssdpDiscover")
}

void ssdpSubscribe() {
    log.debug "ssdpSubscribe() called : subscribe(location, 'ssdpTerm.urn:schemas-upnp-org:device:Basic:1', ssdpHandler)"
    subscribe(location, "ssdpTerm.urn:schemas-upnp-org:device:Basic:1", ssdpHandler)
    registerMDNSListener("_awtrix._tcp")
    log.debug "ssdpSubscribe(): Calling registerMDNSListener() with _awtrix._tcp"
}

void ssdpDiscover() {
    logDebug "ssdpDiscover() called : sendHubCommand(new hubitat.device.HubAction('lan discovery urn:schemas-upnp-org:device:Basic:1', hubitat.device.Protocol.LAN))"
    sendHubCommand(new hubitat.device.HubAction("lan discovery urn:schemas-upnp-org:device:Basic:1", hubitat.device.Protocol.LAN))
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
        def ip = convertHexToIP(it.value.networkAddress)
        def port = convertHexToInt(it.value.deviceAddress)
        String host = "${ip}:${port}"
        sendHubCommand(new hubitat.device.HubAction("""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: $host\r\n\r\n""", hubitat.device.Protocol.LAN, host, [callback: deviceDescriptionHandler]))
    }
}

def getDevices() {
    state.devices = state.devices ?: [:]
}

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

def uninstalled() {
    unsubscribe()
    getChildDevices().each {
        deleteChildDevice(it.deviceNetworkId)
    }
}



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