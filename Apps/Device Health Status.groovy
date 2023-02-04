/**
 *  Device Health Status - application for Hubitat Elevation hub
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
 *  Based on "Light Usage Table" Hubitat sample code by Bruce Ravenel
 *
 *  ver. 1.0.0 2023-02-03 kkossev - first version: 'Light Usage Table' sample app code modification
 *  ver. 1.0.1 2023-02-03 kkossev - added powerSource, battery, model, manufacturer, driver name; added option to skip the 'capability.healthCheck' filtering;
 *  ver. 1.0.2 2023-02-03 FriedCheese2006 - Tweaks to Install Process
 *  ver. 1.0.3 2023-02-04 kkossev - importUrl; documentationLink; app version; debug and info logs options; added controller type; added an option to filter battery-powered only devices, hide poweSource column;
 *
 *          TODO :Add the "Last Activity At" devices property in the table
 *                    Green if time less than 8 hours
 *                    Black if time is less than 25 hours
 *                    Red if time is greater than 25 hours
 *                Show the time elapsed in a format (999d,23h) / (23h,59m) / (59m,59s) since the last battery report. Display the battery percentage remaining in red, if last report was before more than 25 hours. (will this work for all drivers ?)
 */

import groovy.transform.Field

def version() { "1.0.4" }
def timeStamp() {"2023/02/04 10:15 PM"}

@Field static final Boolean debug = true

definition(
	name: "Device Health Status",
	namespace: "kkossev",
	author: "Krassimir Kossev",
	description: "Device Health Status",
	category: "Utility",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Apps/Device%20Health%20Status.groovy",
    documentationLink: "https://community.hubitat.com/t/alpha-device-health-status/111817/1"
    
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	if(state.devices == null) state.devices = [:]
	if(state.devicesList == null) state.devicesList = []
	if(app.getInstallationState() == "COMPLETE") {hideDevices=true} else {hideDevices=false}

    dynamicPage(name: "mainPage", title: "<b>Device Health Status</b> ver. ${driverVersionAndTimeStamp()}", uninstall: true, install: true) {
		section("Device Selection", hideable: true, hidden: hideDevices) {
			input name:"devices", type: filterHealthCheckOnly ? "capability.healthCheck" : "capability.*", title: "Select devices w/ <b>Health Status</b> attribute", multiple: true, submitOnChange: true, width: 4
            logDebug "Device Selection : start"
			devices.each {dev ->
				if(!state.devices["$dev.id"]) {
                    //logDebug "Device Selection : new device ${state.devices["$dev.id"]}" 
                }
                else {
                    //logDebug "Device Selection : existing device ${state.devices["$dev.id"]}" 
                }
                def devData = dev.getData()              // [model:TS0601, application:44, manufacturer:_TZE200_ikvncluo]
                def devType = dev.getTypeName()
                def hasBattery = dev.capabilities.find { it.toString().contains('Battery') }  ? true : false
        		state.devices["$dev.id"] = [
		    		healthStatus: dev.currentValue("healthStatus"), 
                    hasBattery: hasBattery
                ]
			    state.devicesList += dev.id
            }
			if(devices) {
				if(devices.id.sort() != state.devicesList.sort()) { //something was removed
                    logDebug "Device Selection : something was changed" 
					state.devicesList = devices.id
					Map newState = [:]
					devices.each{d ->  newState["$d.id"] = state.devices["$d.id"]}
					state.devices = newState
				}
                else {
                    logDebug "Device Selection : no changes" 
                }
			}
            else {
                logWarn "Device Selection : devices = ${devices}" 
            }
            logDebug "Device Selection : start"
		} // section "Device Selection"
        
		if(hideDevices) {
			section {
				updated()
				paragraph displayTable()
				input "refresh", "button", title: "Refresh Table", width: 2
			}
     		section("Options", hideable: true, hidden: hideDevices) {
       			input("logEnable", "bool", title: "Debug logging.", defaultValue: false, required: false)
       			input("txtEnable", "bool", title: "Description text logging.", defaultValue: false, required: false)
                paragraph "<b>Device selection</b> options:"
    			input name: "filterHealthCheckOnly", type: "bool", title: "Show only devices that have 'Healtch Check' capability", submitOnChange: true, defaultValue: false
                paragraph "<b>Table</b> display options: rows filtering"
    			input name: "hideNotBatteryDevices", type: "bool", title: "Hide <b>not</b> battery-powered devices", submitOnChange: true, defaultValue: false
    			input name: "hideNoHealthStatusAttributeDevices", type: "bool", title: "Hide devices without healthStatus attribute", submitOnChange: true, defaultValue: false
                paragraph "<b>Table</b> display options: columns filtering"
    			input name: "hidePowerSourceColumn", type: "bool", title: "Hide powerSource column", submitOnChange: true, defaultValue: false
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
    		"<th>battery</th>"  +
             (settings?.hidePowerSourceColumn != true ? "<th>powerSource</th>" : "") +  
    		"<th>model</th>"  +
    		"<th>manufacturer</th>"  +
    		"<th>type</th>"  +
    		"<th>driver</th>"  +
        "</tr></thead>"
    
	devices.sort{it.displayName.toLowerCase()}.each {dev ->
        def devData = dev.getData()
        def devType = dev.getTypeName()
        if (settings?.hideNotBatteryDevices == true && state.devices["$dev.id"].hasBattery == false) {
            //logDebug "SKIPPING dev.id=${dev.id} hasBattery = ${state.devices["$dev.id"].hasBattery}"
        }
        else if (settings?.hideNoHealthStatusAttributeDevices == true && state.devices["$dev.id"].healthStatus == null) {
            //logDebug "SKIPPING dev.id=${dev.id} hasBattery = ${state.devices["$dev.id"].hasBattery}"
        }
        else {
    		String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
            def healthColor = dev.currentHealthStatus == null ? "black" : dev.currentHealthStatus == "online" ? "green" : "red"
            def healthStatus = dev.currentHealthStatus ?: "n/a"
                
    		str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>" +
    			"<td style='color:${healthColor}'>$healthStatus</td>" +
                "<td style='color:${black}'>${dev.currentBattery ?: "n/a"}</td>" +
                (settings?.hidePowerSourceColumn != true ? "<td style='color:${black}'>${dev.currentpowerSource ?: "n/a"}</td>"  : "") +  
                "<td style='color:${black}'>${devData.model ?: "n/a"}</td>" +
                "<td style='color:${black}'>${devData.manufacturer ?: "n/a"}</td>" +
                "<td style='color:${black}'>${dev.controllerType ?: "n/a"}</td>" +
                "<td style='color:${black}'>${devType ?: "n/a"}</td>" // +
        }
	} // for each device
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

private void updateTableOnEvent() {
    logDebug "updateTableOnEvent"
}

void healthStatusOnlineHandler(evt) {
    logDebug "healthStatusOnlineHandler evt.name=${evt.name} evt.value=${evt.value}"
    runIn(1, 'updateTableOnEvent'/*,  [overwrite: true, data: evt]*/)
}

void healthStatusOfflineHandler(evt) {
    logDebug "healthStatusOfflineHandler evt.name=${evt.name} evt.value=${evt.value}"
    runIn(1, 'updateTableOnEvent'/*,  [overwrite: true, data: evt]*/)
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp().split(" ")[0]}

def logDebug(msg) { if (logEnable) log.debug(msg) }
def logWarn(msg)  { if (logEnable) log.warn(msg) }
def logInfo(msg)  { if (txtEnable) log.info(msg) }

def updated() {
    logDebug "updated()"
	unsubscribe()
	initialize()
}

def installed() {
    logInfo "installed()"
}

void initialize() {
    logDebug "initialize()"
	subscribe(devices, "healthStatus.online", healthStatusOnlineHandler)
	subscribe(devices, "healthStatus.offline", healthStatusOfflineHandler)
}

