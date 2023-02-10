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
 *  ver. 1.0.3 2023-02-05 kkossev - importUrl; documentationLink; app version; debug and info logs options; added controller type, driver type; added an option to filter battery-powered only devices, hide poweSource column; filterHealthCheckOnly bug fix;
 *                                - added 'Last Activity Time'; last activity thresholds and color options; battery threshold option; catching some exceptions when a device is deleted from HE, but was present in the list; added device status
 *  ver. 1.0.4 2023-02-06 kkossev - added 'Device Status' red/green colors; added hideModelAndManufacturerColumns and hideVirtualAndUnknownDevices filtering options; app instance name can be changed; added Presence column
 *  ver. 1.0.5 2023-02-08 kkossev - added toggle "Show only offline (INACTIVE / not present) devices"
 *  ver. 1.0.6 2023-02-10 kkossev - IntelliJ lint;
 *
 *                                  TODO: 
 */

import groovy.transform.Field

def version() { "1.0.6" }

def timeStamp() { "2023/02/10 8:22 PM" }

@Field static final Boolean debug = false

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
    if (state.devices == null) state.devices = [:]
    if (state.devicesList == null) state.devicesList = []
    if (app.getInstallationState() == "COMPLETE") {
        hideDevices = true
    } else {
        hideDevices = false
    }

    dynamicPage(name: "mainPage", title: "<b>Device Health Status</b> (app ver. ${driverVersionAndTimeStamp()})", uninstall: true, install: true) {
        section("Device Selection", hideable: true, hidden: hideDevices) {
            input name: "devices", type: settings?.selectHealthCheckOnly == true ? "capability.healthCheck" : "capability.*", title: "Select devices", multiple: true, submitOnChange: true, width: 4
            logDebug "Device Selection : start"
            devices.each { dev ->
                if (!state.devices["$dev.id"]) {
                    //logDebug "Device Selection : new device ${state.devices["$dev.id"]}" 
                } else {
                    //logDebug "Device Selection : existing device ${state.devices["$dev.id"]}" 
                }
                try {
                    if (dev != null && dev?.status != null) {
                        //log.trace 'status = ${dev.status} (device ${state.devices["$dev.id"]})'
                        def hasBattery = dev.capabilities.find { it.toString().contains('Battery') } ? true : false
                        def hasPowerSource = dev.capabilities.find { it.toString().contains('PowerSource') } ? true : false
                        state.devices["$dev.id"] = [
                                healthStatus  : dev.currentValue("healthStatus"),
                                hasPowerSource: hasPowerSource,
                                hasBattery    : hasBattery
                        ]
                        state.devicesList += dev.id
                    } else {
                        logWarn "dev is null?  state.devices[dev.id] is ${state.devices["$dev.id"]}"
                    }
                }
                catch (e) {
                    logWarn "exception catched when procesing device ${dev.id}"
                }
            }

            if (devices) {
                if (devices.id.sort() != state.devicesList.sort()) { //something was removed
                    logDebug "Device Selection : something was changed"
                    state.devicesList = devices.id
                    Map newState = [:]
                    devices.each { d -> newState["$d.id"] = state.devices["$d.id"] }
                    state.devices = newState
                } else {
                    logDebug "Device Selection : no changes"
                }
            } else {
                logWarn "Device Selection : devices = ${devices}"
            }
            logDebug "Device Selection : start"
        } // section "Device Selection"

        if (hideDevices) {
            section {
                updated()
                paragraph ""
                input name: "showOfflineOnly", type: "bool", title: "Show only offline (INACTIVE / not present) devices", submitOnChange: true, defaultValue: false
                paragraph ""
                paragraph displayTable()
                input "refresh", "button", title: "Refresh Table", width: 2
            }
            section("Options", hideable: true, hidden: hideDevices) {
                label title: "Change this <b>Device Health Status</b> app instance name:", submitOnChange: true, required: false
                paragraph ""
                input("logEnable", "bool", title: "Debug logging.", defaultValue: false, required: false)
                input("txtEnable", "bool", title: "Description text logging.", defaultValue: false, required: false)
                paragraph ""
                paragraph "<b>Device selection</b> options:"
                input name: "selectHealthCheckOnly", type: "bool", title: "Select only devices that have 'Healtch Check' capability", submitOnChange: true, defaultValue: false
                paragraph ""
                paragraph "Table filtering options: <b>columns</b> :"
                input name: "hidePowerSourceColumn", type: "bool", title: "Hide powerSource column", submitOnChange: true, defaultValue: false
                input name: "hideLastActivityAtColumn", type: "bool", title: "Hide LastActivityAt column", submitOnChange: true, defaultValue: false
                input name: "hideModelAndManufacturerColumns", type: "bool", title: "Hide Model and Manufacturer columns", submitOnChange: true, defaultValue: false
                input name: "hidePresenceColumn", type: "bool", title: "Hide Presence column (the one that we are trying to depricate)", submitOnChange: true, defaultValue: true
                paragraph ""
                paragraph "Table filtering options: <b>rows</b> :"
                input name: "hideNotBatteryDevices", type: "bool", title: "Hide <b>not</b> battery-powered devices", submitOnChange: true, defaultValue: false
                input name: "hideNoHealthStatusAttributeDevices", type: "bool", title: "Hide devices without healthStatus attribute", submitOnChange: true, defaultValue: false
                input name: "hideVirtualAndUnknownDevices", type: "bool", title: "Hide virtual/unknown type devices", submitOnChange: true, defaultValue: false
                paragraph ""
                paragraph "<b>Thresholds</b> :"
                input name: "lastActivityGreen", type: "number", title: "Devices w/ lastActivity less than N hours will be shown in green", submitOnChange: true, defaultValue: 9
                input name: "lastActivityRed", type: "number", title: "Devices w/ lastActivity more than N hours will be shown in red", submitOnChange: true, defaultValue: 25
                input name: "batteryLowThreshold", type: "number", title: "Devices w/ Battery percentage below N % will be shown in red", submitOnChange: true, defaultValue: 33
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

            "<thead><tr style='border-bottom:2px solid black'><th style='border-right:2px solid black'><div>Device</div><div>Name</div></th>" +
            "<th><div>Health</div><div>Status</div></th>" +
            "<th><div>Battery</div><div>%</div></th>" +
            (settings?.hideLastActivityAtColumn != true ? "<th><div>Last</div><div>Activity</div></th>" : "") +
            "<th><div>HE</div><div>Status</div></th>" +
            (settings?.hidePresenceColumn != true ? "<th><div>Presence</div><div>Attr.</div></th>" : "") +
            (settings?.hidePowerSourceColumn != true ? "<th><div>Power</div><div>Source</div></th>" : "") +
            (settings?.hideModelAndManufacturerColumns != true ? "<th><div>Device</div><div>Model</div></th>" : "") +
            (settings?.hideModelAndManufacturerColumns != true ? "<th><div>Device</div><div>Manufacturer</div></th>" : "") +
            "<th><div>Device</div><div>Type</div></th>" +
            "<th><div>Driver</div><div>Name</div></th>" +
            "<th><div>Driver</div><div>Type</div></th>" +
            "</tr></thead>"


    def devicesSorted = devices
    try {
        devices.sort { it?.displayName.toLowerCase() }
    }
    catch (e) {
        logWarn "catched exception while sorting devices : ${e} "
        return "INTERNAL ERROR, please send the debug logs to the developer"
    }
    devices = devicesSorted
    devices.sort { it?.displayName.toLowerCase() }.each { dev ->
        def devData = dev.getData()
        def devType = dev.getTypeName()
        if (settings?.hideNotBatteryDevices == true && state.devices["$dev.id"].hasBattery == false) {
            //logDebug "SKIPPING dev.id=${dev.id} w/o Battery "
        } else if (settings?.hideNoHealthStatusAttributeDevices == true && state.devices["$dev.id"].healthStatus == null) {
            //logDebug "SKIPPING dev.id=${dev.id} w/o healthStatus"
        } else if (settings?.hideVirtualAndUnknownDevices == true && !(dev.controllerType in ["ZGB", "ZWV", "LNK"])) {
            //logDebug "SKIPPING dev.id=${dev.id} VirtualAndUnknownDevices ${dev.controllerType}"
        } else { //
            String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
            def healthColor = dev.currentHealthStatus == null ? "black" : dev.currentHealthStatus == "online" ? "green" : "red"
            def healthStatus = dev.currentHealthStatus ?: "n/a"
            def readableUTCDate = (dev.lastActivity ?: "n/a").toString().tokenize('+')[0]
            def lastActivity = "n/a"
            def lastActivityColor = "black"
            def batteryPercentageColor = "black"
            def statusColor = (dev.status ?: "n/a") == "INACTIVE" ? "red" : (dev.status ?: "n/a") == "ACTIVE" ? "green" : "black"
            def presenceColor = (dev.currentPresence ?: "n/a") == "not present" ? "red" : (dev.currentPresence ?: "n/a") == "present" ? "green" : "black"
            if (readableUTCDate != "n/a") {
                Date date = Date.parse('yyyy-MM-dd HH:mm:ss', readableUTCDate)
                lastActivity = new Date(date.getTime() + TimeZone.getDefault().getOffset(date.getTime()))
                def now = new Date()
                long diff = now.getTime() - date.getTime()
                long diffHours = diff / (60 * 60 * 1000)
                if (diffHours < settings?.lastActivityGreen && healthStatus != "offline") {
                    lastActivityColor = "green"
                } else if (diffHours >= settings?.lastActivityRed) {
                    lastActivityColor = "red"
                } else {
                    lastActivityColor = "black"
                }
            }
            if (dev.currentBattery == null && dev.currentPowerSource == "battery") {
                batteryPercentageColor = "red"
            } else if (healthStatus == "online" && lastActivityColor != "red" && dev.currentPowerSource == "battery" && (dev.currentBattery as int) >= settings?.batteryLowThreshold) {
                batteryPercentageColor = "green"
            } else if (healthStatus == "online" && lastActivityColor == "green" && dev.currentPowerSource == "battery" && (dev.currentBattery as int) < settings?.batteryLowThreshold) {
                batteryPercentageColor = "red"
            } else {
                batteryPercentageColor = "black"    // not sure if the battery percentage remaining is accurate ...
            }
            if (settings.showOfflineOnly == true && (healthStatus == "online" || dev.status == "ACTIVE")) {
                //logDebug "SKIPPING dev.id=${dev.id} offline"
            } else {
                //lastActivity = lastActivity.tokenize( '+' )[0]   batteryLowThreshold
                str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>" +
                        "<td style='color:${healthColor}'>$healthStatus</td>" +
                        "<td style='color:${batteryPercentageColor}'>${dev.currentBattery ?: "n/a"}</td>" +
                        (settings?.hideLastActivityAtColumn != true ? "<td style='color:${lastActivityColor}'>${lastActivity}</td>" : "") +
                        "<td style='color:${statusColor}'>${dev.status ?: "n/a"}</td>" +
                        (settings?.hidePresenceColumn != true ? "<td style='color:${presenceColor}'>${dev.currentPresence ?: "n/a"}</td>" : "") +
                        (settings?.hidePowerSourceColumn != true ? "<td style='color:${black}'>${dev.currentPowerSource ?: "n/a"}</td>" : "") +
                        (settings?.hideModelAndManufacturerColumns != true ? "<td style='color:${black}'>${devData.model ?: "n/a"}</td>" : "") +
                        (settings?.hideModelAndManufacturerColumns != true ? "<td style='color:${black}'>${devData.manufacturer ?: "n/a"}</td>" : "") +
                        "<td style='color:${black}'>${dev.controllerType ?: "n/a"}</td>" +
                        "<td style='color:${black}'>${devType ?: "n/a"}</td>" +
                        "<td style='color:${black}'>${dev.driverType ?: "n/a"}</td>" //+
            }
        }
    } // for each device
    str += "</table></div>"
    str
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
    "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

void appButtonHandler(btn) {
    logDebug "appButtonHandler(${btn} start)"
    List toBeDel = []
    if (btn == "refresh") state.devices.each { k, v ->
        try {
            def dev = devices.find { "$it.id" == k }
            //logDebug "checking state.devices[${k}]"
            if (dev.currentStatus ?: "unknown" == "ACTIVE") {
                //state.devices[k].refreshTime = now()
            }
        }
        catch (e) {
            logWarn "catched exception in appButtonHandler : ${e} "
            logWarn "problematic device has key=${k}"
            toBeDel += k
        }
    }
    toBeDel.each { k ->
        logDebug "TODO: delete ${toBeDel} from state.devices list .."
    }
    logDebug "appButtonHandler(${btn} exited)"
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

def driverVersionAndTimeStamp() { version() + ' ' + timeStamp().split(" ")[0] }

def logDebug(msg) { if (logEnable) log.debug(msg) }

def logWarn(msg) { if (logEnable) log.warn(msg) }

def logInfo(msg) { if (txtEnable) log.info(msg) }

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
    try {
        subscribe(devices, "healthStatus.online", healthStatusOnlineHandler)
        subscribe(devices, "healthStatus.offline", healthStatusOfflineHandler)
    }
    catch (e) {
        logWarn "catched exception while processing initialize() : ${e} "
    }
}

