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
 */

import groovy.transform.Field

def version() { "1.0.0" }
def timeStamp() {"2023/02/03 9:52 AM"}

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
	dynamicPage(name: "mainPage", title: "Device Health Status Control Table", uninstall: true, install: true) {
		section {
			input "devices", "capability.healthCheck", title: "Select devices w/ <b>Health Status</b> attribute", multiple: true, submitOnChange: true, width: 4
			devices.each {dev ->
				if(!state.devices["$dev.id"]) {
					state.devices["$dev.id"] = [
                        healthStatus: dev.currentValue("healthStatus"), 
                        total: 0, 
                        var: ""
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
				updated()
				paragraph displayTable()
				if(state.newVar) {
					List vars = getAllGlobalVars().findAll{it.value.type == "string"}.keySet().collect().sort{it.capitalize()}
					input "newVar", "enum", title: "Select Variable", submitOnChange: true, width: 4, options: vars, newLineAfter: true
					if(newVar) {
						state.devices[state.newVar].var = newVar
						state.remove("newVar")
						app.removeSetting("newVar")
						paragraph "<script>{changeSubmit(this)}</script>"
					}
				} else if(state.remVar) {
					state.devices[state.remVar].var = ""
					state.remove("remVar")
					paragraph "<script>{changeSubmit(this)}</script>"
				}
				input "refresh", "button", title: "Refresh Table", width: 2
				//input "reset", "button", title: "Reset Table", width: 2
			}
		}
	}
}

String displayTable() {
    /*
	if(state.reset) {
		def dev = devices.find{"$it.id" == state.reset}
		state.devices[state.reset].start = dev.currentSwitch == "on" ? now() : 0
		state.devices[state.reset].total = 0
		state.remove("reset")
	}
    */
	String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
        
		"<thead><tr style='border-bottom:2px solid black'><th style='border-right:2px solid black'>Device</th>" +
        
		"<th>healthStatus</th>"  +
        
		//"<th>Reset</th>" +
        
		//"<th>Variable</th>
        "</tr></thead>"
    
	devices.sort{it.displayName.toLowerCase()}.each {dev ->
		int total = state.devices["$dev.id"].total / 1000
		String thisVar = state.devices["$dev.id"].var
		int hours = total / 3600
		total = total % 3600
		int mins = total / 60
		int secs = total % 60
		String time = "$hours:${mins < 10 ? "0" : ""}$mins:${secs < 10 ? "0" : ""}$secs"
		if(thisVar) setGlobalVar(thisVar, time)
        
		String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
		//String reset = buttonLink("d$dev.id", "<iconify-icon icon='bx:reset'></iconify-icon>", "black", "20px")
		String var = thisVar ? buttonLink("r$dev.id", thisVar, "purple") : buttonLink("n$dev.id", "Select", "green")
        def healthColor = dev.currentHealthStatus == null ? "black" : dev.currentHealthStatus == "online" ? "green" : "red"
        def healthStatus = dev.currentHealthStatus ?: "n/a"
            
		str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>" +
			"<td style='color:${healthColor}'>$healthStatus</td>" // +
			//"<td title='Reset Total for $dev' style='padding:0px 0px'>$reset</td>" +
			//"<td title='${thisVar ? "Deselect $thisVar" : "Select String Hub Variable"}'>$var</td></tr>"
	}
	str += "</table></div>"
	str
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

void appButtonHandler(btn) {
    /*
	if(btn == "reset") state.devices.each{k, v ->
		def dev = devices.find{"$it.id" == k}
		state.devices[k].start = dev.currentSwitch == "on" ? now() : 0
		state.devices[k].total = 0
	} 
    else 
    */
    if(btn == "refresh") state.devices.each{k, v ->
		def dev = devices.find{"$it.id" == k}
		if(dev.currentHealthStatus == "online") {
			//state.devices[k].total += now() - state.devices[k].start
			//state.devices[k].start = now()
		}
	} else if(btn.startsWith("n")) state.newVar = btn.minus("n")
	else if(btn.startsWith("r")) state.remVar = btn.minus("r")
	//else state.reset = btn.minus("d")
}

def updated() {
	unsubscribe()
	initialize()
}

def installed() {
}

void initialize() {
	subscribe(devices, "switch.on", onHandler)
	subscribe(devices, "switch.off", offHandler)
}

void onHandler(evt) {
	//state.devices[evt.device.id].start = now()
}

void offHandler(evt) {
/*    
	state.devices[evt.device.id].total += now() - state.devices[evt.device.id].start
	String thisVar = state.devices[evt.device.id].var
	if(thisVar) {
		int total = state.devices[evt.device.id].total / 1000
		int hours = total / 3600
		total = total % 3600
		int mins = total / 60
		int secs = total % 60
		setGlobalVar(thisVar, "$hours:${mins < 10 ? "0" : ""}$mins:${secs < 10 ? "0" : ""}$secs")
	}
*/
}
