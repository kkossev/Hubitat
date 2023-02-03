/**
 *  Device Health Status - application for Hubitat Elevation hub
 *
 *  https://community.hubitat.com/t/devicepresent-capability-healthstatus/89774/18?u=kkossev
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
 *  Based on "Light Usage Table" Hubitat sample code by Bruce Ravenel
 *
 *  ver. 1.0.0 2023-02-03 kkossev - first version: 'Light Usage Table' sample app code modification
 *  ver. 1.0.1 2023-02-03 kkossev - added powerSource, battery, model, manufacturer, driver name; added option to skip the 'capability.healthCheck' filtering;
 *
 *                                  TODO :
 */

import groovy.transform.Field

def version() { "1.0.1" }
def timeStamp() {"2023/02/03 3:44 PM"}

@Field static final Boolean debug = true

definition(
	name: "Device Health Status",
	namespace: "kkossev",
	author: "Krassimir Kossev",
	description: "Device Health Status",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: ""
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	if(state.devices == null) state.devices = [:]
	if(state.devicesList == null) state.devicesList = []
	if(app.getInstallationState() == "COMPLETE") {hideDevices=true} else {hideDevices=false}

	dynamicPage(name: "mainPage", title: "Device Health Status Control Table", uninstall: true, install: true) {
		section("Device Selection", hideable: true,hidden: hideDevices) {
			input name: "filterHealthCheckOnly", type: "bool", title: "Filter only devices that have 'Healtch Check' capability", submitOnChange: true, defaultValue: false
			input name:"devices", type: filterHealthCheckOnly ? "capability.healthCheck" : "capability.*", title: "Select devices w/ <b>Health Status</b> attribute", multiple: true, submitOnChange: true, width: 4
			devices.each {dev ->
				if(!state.devices["$dev.id"]) {
					state.devices["$dev.id"] = [
						healthStatus: dev.currentValue("healthStatus"), 
                    ]
					state.devicesList += dev.id
				}
			}
			if(devices) {
				if(devices.id.sort() != state.devicesList.sort()) { //something was removed
					state.devicesList = devices.id
					Map newState = [:]
					devices.each{d ->  newState["$d.id"] = state.devices["$d.id"]}
					state.devices = newState
				}
			}
		}
		if(hideDevices) {
			section {
				updated()
				paragraph displayTable()
				input "refresh", "button", title: "Refresh Table", width: 2
			}
		} else {
			section("CLICK DONE TO INSTALL APP AFTER SELECTING DEVICES") {
				paragraph ""
			}
		}
	}
}

String displayTable() {
	String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
        
		"<thead><tr style='border-bottom:2px solid black'><th style='border-right:2px solid black'>Device</th>" +
    		"<th>healthStatus</th>"  +
    		"<th>powerSource</th>"  +
    		"<th>battery</th>"  +
    		"<th>model</th>"  +
    		"<th>manufacturer</th>"  +
    		"<th>driver</th>"  +
        "</tr></thead>"
    
	devices.sort{it.displayName.toLowerCase()}.each {dev ->
		String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
        def healthColor = dev.currentHealthStatus == null ? "black" : dev.currentHealthStatus == "online" ? "green" : "red"
        def healthStatus = dev.currentHealthStatus ?: "n/a"
        def devData = dev.getData()              // [model:TS0601, application:44, manufacturer:_TZE200_ikvncluo]
        def devType = dev.getTypeName()
            
		str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>" +
			"<td style='color:${healthColor}'>$healthStatus</td>" +
            "<td style='color:${black}'>${dev.currentpowerSource ?: "n/a"}</td>" +
            "<td style='color:${black}'>${dev.currentBattery ?: "n/a"}</td>" +
            "<td style='color:${black}'>${devData.model ?: "n/a"}</td>" +
            "<td style='color:${black}'>${devData.manufacturer ?: "n/a"}</td>" +
            "<td style='color:${black}'>${devType ?: "n/a"}</td>" // +
	}
	str += "</table></div>"
	str
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

void appButtonHandler(btn) {
    if(btn == "refresh") state.devices.each{k, v ->
		def dev = devices.find{"$it.id" == k}
		if(dev.currentHealthStatus == "online") {
			//state.devices[k].refreshTime = now()
		}
	} 
}

def updated() {
	unsubscribe()
	initialize()
}

def installed() {
}

void initialize() {
	subscribe(devices, "healthStatus.online", healthStatusOnlineHandler)
	subscribe(devices, "healthStatus.offline", healthStatusOfflineHandler)
}

void healthStatusOnlineHandler(evt) {
    log.debug "healthStatusOnlineHandler evt=${evt}"
}

void healthStatusOfflineHandler(evt) {
    log.debug "healthStatusOfflineHandler evt=${evt}"
}
