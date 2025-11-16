/**
 *  Thermostats Sync
 *
 *  Description: Synchronizes the main attributes of two thermostats. When an attribute 
 *  changes in one thermostat, the same attribute is automatically set on the other 
 *  thermostat. Includes protection against infinite loops.
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
 *  ver. 1.0.0  2025-08-19 kkossev  - Initial version
 *  ver. 1.0.1  2025-10-20 kkossev  - added support for temperature and operating state sync; added importUrl; 
 *  ver. 1.0.2  2025-11-09 kkossev  - app is singleThreaded; Implemented 2-second global sync flag clearing across all sync methods and fixed sync counter accumulation by clearing both flags AND counters together to prevent "Maximum sync attempts reached" errors with rapid TRVZB device events.
 *                                    added Battery and Health Status sync support;
 *  ver. 1.0.3  2025-11-09 kkossev  - fixed an accidental UTF-8 with BOM encoding that caused issues with HPM
 * 
 *              TODO:
 *
 */

import groovy.transform.Field
@Field static final String VERSION = "1.0.3"
@Field static final String COMPILE_TIME = '2025/11/16 9:07 PM'

definition(
    name: "Thermostats Sync",
    namespace: "kkossev",
    author: "Krassimir Kossev",
    description: "Synchronizes the main attributes of two thermostats bidirectionally",
    category: "Utility",
    //iconUrl: "",
    //iconX2Url: "",
    //iconX3Url: "",
    importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Apps/Thermostats%20Sync/Thermostats_Sync.groovy",
    singleInstance: false//,
    //singleThreaded: true
)

preferences {
    page(name: "mainPage", title: "Thermostats Sync Configuration", install: true, uninstall: true) {
        section("Select Thermostats to Synchronize") {
            input "thermostat1", "capability.thermostat", title: "First Thermostat", required: true, submitOnChange: true
            input "thermostat2", "capability.thermostat", title: "Second Thermostat", required: true, submitOnChange: true
        }
        
        section("Synchronization Options") {
            input "syncThermostatMode", "bool", title: "Synchronize Thermostat Mode (off/heat/cool/auto)", defaultValue: true, submitOnChange: true
            input "syncHeatingSetpoint", "bool", title: "Synchronize Heating Setpoint", defaultValue: true, submitOnChange: true
            input "syncCoolingSetpoint", "bool", title: "Synchronize Cooling Setpoint", defaultValue: true, submitOnChange: true
            input "syncFanMode", "bool", title: "Synchronize Fan Mode", defaultValue: true, submitOnChange: true
            input "syncTemperature", "bool", title: "Synchronize Temperature (using setTemperature command)", defaultValue: true, submitOnChange: true
            input "syncOperatingState", "bool", title: "Synchronize Operating State (using setThermostatOperatingState command)", defaultValue: true, submitOnChange: true
            input "syncBattery", "bool", title: "Synchronize Battery Level (using setBattery command)", defaultValue: false, submitOnChange: true
            input "syncHealthStatus", "bool", title: "Synchronize Health Status (using setHealthStatus command)", defaultValue: false, submitOnChange: true
        }
        
        section("Loop Prevention") {
            input "syncDelay", "number", title: "Synchronization Delay (milliseconds)", description: "Delay before applying changes to prevent loops", defaultValue: 500, range: "100..5000"
            input "maxSyncAttempts", "number", title: "Maximum Sync Attempts per Change", description: "Prevent runaway synchronization", defaultValue: 3, range: "1..10"
        }
        
        section("Logging") {
            input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "traceEnable", "bool", title: "Enable Trace Logging", defaultValue: true
        }
        
        section("Instance Settings") {
            input "appLabel", "text", title: "App Instance Name", 
                  description: "Custom name for this app instance (leave blank for default)", 
                  required: false, submitOnChange: true
        }
        
        if (thermostat1 && thermostat2) {
            section("Current Status") {
                paragraph "<b>First Thermostat (${thermostat1.displayName}):</b><br>" +
                         "Mode: ${thermostat1.currentValue('thermostatMode')}<br>" +
                         "Heating Setpoint: ${thermostat1.currentValue('heatingSetpoint')}°<br>" +
                         "Cooling Setpoint: ${thermostat1.currentValue('coolingSetpoint')}°<br>" +
                         "Fan Mode: ${thermostat1.currentValue('thermostatFanMode')}<br>" +
                         "Temperature: ${thermostat1.currentValue('temperature')}°<br>" +
                         "Operating State: ${thermostat1.currentValue('thermostatOperatingState')}<br>" +
                         "Battery: ${thermostat1.currentValue('battery')}%<br>" +
                         "Health Status: ${thermostat1.currentValue('healthStatus')}"
                         
                paragraph "<b>Second Thermostat (${thermostat2.displayName}):</b><br>" +
                         "Mode: ${thermostat2.currentValue('thermostatMode')}<br>" +
                         "Heating Setpoint: ${thermostat2.currentValue('heatingSetpoint')}°<br>" +
                         "Cooling Setpoint: ${thermostat2.currentValue('coolingSetpoint')}°<br>" +
                         "Fan Mode: ${thermostat2.currentValue('thermostatFanMode')}<br>" +
                         "Temperature: ${thermostat2.currentValue('temperature')}°<br>" +
                         "Operating State: ${thermostat2.currentValue('thermostatOperatingState')}<br>" +
                         "Battery: ${thermostat2.currentValue('battery')}%<br>" +
                         "Health Status: ${thermostat2.currentValue('healthStatus')}"
            }
        }
        
        if (thermostat1 && thermostat2) {
            section("Manual Sync Controls") {
                input "syncT1toT2", "button", title: "Sync Thermostat1 → Thermostat2"
                input "syncT2toT1", "button", title: "Sync Thermostat2 → Thermostat1"
                input "startAutoSync", "button", title: "Start AutoSync"
                input "stopAutoSync", "button", title: "Stop AutoSync"
                input "clearSyncState", "button", title: "Clear All Sync Flags (Debug)"
                
                paragraph "<b>AutoSync Status:</b> ${atomicState.autoSyncEnabled ? 'Enabled' : 'Disabled'}"
            }
        }
        
        section("App Information") {
            paragraph "<b>Thermostats Sync App</b><br>" +
                     "Version: ${VERSION}<br>" +
                     "Compiled: ${COMPILE_TIME}<br>" 
        }
    }
}

def installed() {
    logDebug "Thermostats Sync v${VERSION} installed (compiled: ${COMPILE_TIME})"
    
    // Update app label if custom name is provided
    if (appLabel && appLabel.trim() != "") {
        app.updateLabel(appLabel.trim())
    }
    
    // Schedule logs off after 30 minutes
    if (logEnable || traceEnable) runIn(1800, logsOff)
    
    initialize()
}

def updated() {
    logDebug "Thermostats Sync v${VERSION} updated (compiled: ${COMPILE_TIME})"
    
    // Update app label if custom name is provided
    if (appLabel && appLabel.trim() != "") {
        app.updateLabel(appLabel.trim())
    } else {
        // Reset to default name if field is cleared
        app.updateLabel("Thermostats Sync")
    }
    
    // Schedule logs off after 30 minutes
    if (logEnable || traceEnable) runIn(1800, logsOff)
    
    unsubscribe()
    initialize()
}

def appButtonHandler(btn) {
    switch(btn) {
        case "syncT1toT2":
            logInfo "Manual sync requested: ${thermostat1.displayName} → ${thermostat2.displayName}"
            performManualSync(thermostat1, thermostat2)
            break
        case "syncT2toT1":
            logInfo "Manual sync requested: ${thermostat2.displayName} → ${thermostat1.displayName}"
            performManualSync(thermostat2, thermostat1)
            break
        case "startAutoSync":
            logInfo "Starting AutoSync"
            atomicState.autoSyncEnabled = true
            initialize()
            break
        case "stopAutoSync":
            logInfo "Stopping AutoSync"
            atomicState.autoSyncEnabled = false
            unsubscribe()
            break
        case "clearSyncState":
            logInfo "Clearing all sync flags and counters"
            clearAllSyncFlags()
            atomicState.syncCounter = [:]
            atomicState.recentEvents = [:]
            // Cancel any pending flag clearing tasks
            unschedule("clearSyncFlags")
            unschedule("clearSpecificSyncFlag")
            logInfo "Cleared all sync state and cancelled pending tasks"
            break
    }
}

def initialize() {
    logDebug "Initializing Thermostats Sync v${VERSION}"
    
    // Initialize sync state tracking - clear all previous state
    atomicState.syncInProgress = [:]
    atomicState.syncCounter = [:]
    atomicState.recentEvents = [:]
    
    // Initialize autoSync state if not set
    if (atomicState.autoSyncEnabled == null) {
        atomicState.autoSyncEnabled = true
    }
    
    if (!thermostat1 || !thermostat2) {
        logWarn "Both thermostats must be selected"
        return
    }
    
    if (thermostat1.deviceId == thermostat2.deviceId) {
        logWarn "Cannot sync a thermostat with itself"
        return
    }
    
    // Only subscribe to events if autoSync is enabled
    if (atomicState.autoSyncEnabled) {
        // Subscribe to events based on user preferences
        if (syncThermostatMode) {
            subscribe(thermostat1, "thermostatMode", thermostatModeHandler)
            subscribe(thermostat2, "thermostatMode", thermostatModeHandler)
        }
        
        if (syncHeatingSetpoint) {
            subscribe(thermostat1, "heatingSetpoint", heatingSetpointHandler)
            subscribe(thermostat2, "heatingSetpoint", heatingSetpointHandler)
        }
        
        if (syncCoolingSetpoint) {
            subscribe(thermostat1, "coolingSetpoint", coolingSetpointHandler)
            subscribe(thermostat2, "coolingSetpoint", coolingSetpointHandler)
        }
        
        if (syncFanMode) {
            subscribe(thermostat1, "thermostatFanMode", fanModeHandler)
            subscribe(thermostat2, "thermostatFanMode", fanModeHandler)
        }
        
        if (syncTemperature) {
            subscribe(thermostat1, "temperature", temperatureHandler)
            subscribe(thermostat2, "temperature", temperatureHandler)
        }
        
        if (syncOperatingState) {
            subscribe(thermostat1, "thermostatOperatingState", operatingStateHandler)
            subscribe(thermostat2, "thermostatOperatingState", operatingStateHandler)
        }
        
        if (syncBattery) {
            subscribe(thermostat1, "battery", batteryHandler)
            subscribe(thermostat2, "battery", batteryHandler)
        }
        
        if (syncHealthStatus) {
            subscribe(thermostat1, "healthStatus", healthStatusHandler)
            subscribe(thermostat2, "healthStatus", healthStatusHandler)
        }
        
        logInfo "Thermostats Sync initialized for ${thermostat1.displayName} ↔ ${thermostat2.displayName} (AutoSync: ON)"
    } else {
        logInfo "Thermostats Sync initialized for ${thermostat1.displayName} ↔ ${thermostat2.displayName} (AutoSync: OFF)"
    }
}

// Manual Sync Method
def performManualSync(sourceDevice, targetDevice) {
    if (!sourceDevice || !targetDevice) {
        logWarn "Cannot perform manual sync: missing source or target device"
        return
    }
    
    logInfo "Performing manual sync: ${sourceDevice.displayName} → ${targetDevice.displayName}"
    
    // Sync thermostat mode if enabled
    if (syncThermostatMode) {
        def mode = sourceDevice.currentValue('thermostatMode')
        if (mode) {
            syncThermostatMode([target: targetDevice.deviceId, value: mode, source: sourceDevice.deviceId])
        }
    }
    
    // Sync heating setpoint if enabled
    if (syncHeatingSetpoint) {
        def heatingSetpoint = sourceDevice.currentValue('heatingSetpoint')
        if (heatingSetpoint) {
            syncHeatingSetpoint([target: targetDevice.deviceId, value: heatingSetpoint, source: sourceDevice.deviceId])
        }
    }
    
    // Sync cooling setpoint if enabled
    if (syncCoolingSetpoint) {
        def coolingSetpoint = sourceDevice.currentValue('coolingSetpoint')
        if (coolingSetpoint) {
            syncCoolingSetpoint([target: targetDevice.deviceId, value: coolingSetpoint, source: sourceDevice.deviceId])
        }
    }
    
    // Sync fan mode if enabled
    if (syncFanMode) {
        def fanMode = sourceDevice.currentValue('thermostatFanMode')
        if (fanMode) {
            syncFanMode([target: targetDevice.deviceId, value: fanMode, source: sourceDevice.deviceId])
        }
    }
    
    // Sync temperature if enabled
    if (syncTemperature) {
        def temperature = sourceDevice.currentValue('temperature')
        if (temperature) {
            syncTemperature([target: targetDevice.deviceId, value: temperature, source: sourceDevice.deviceId])
        }
    }
    
    // Sync operating state if enabled
    if (syncOperatingState) {
        def operatingState = sourceDevice.currentValue('thermostatOperatingState')
        if (operatingState) {
            syncOperatingState([target: targetDevice.deviceId, value: operatingState, source: sourceDevice.deviceId])
        }
    }
    
    // Sync battery if enabled
    if (syncBattery) {
        def battery = sourceDevice.currentValue('battery')
        if (battery != null) {
            syncBattery([target: targetDevice.deviceId, value: battery, source: sourceDevice.deviceId])
        }
    }
    
    // Sync health status if enabled
    if (syncHealthStatus) {
        def healthStatus = sourceDevice.currentValue('healthStatus')
        if (healthStatus) {
            syncHealthStatus([target: targetDevice.deviceId, value: healthStatus, source: sourceDevice.deviceId])
        }
    }
    
    logInfo "Manual sync completed"
}

// Event Handlers
def thermostatModeHandler(evt) {
    if (!syncThermostatMode || !atomicState.autoSyncEnabled) return
    
    // Filter duplicate events
    if (isDuplicateEvent(evt)) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value

    logTrace "Thermostat mode changed on ${sourceDevice.displayName}: ${newValue}"

    if (shouldSync("thermostatMode", sourceDevice.deviceId, newValue)) {
        runInMillis(syncDelay ?: 500, "syncThermostatMode", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def heatingSetpointHandler(evt) {
    if (!syncHeatingSetpoint || !atomicState.autoSyncEnabled) return
    
    // Filter duplicate events
    if (isDuplicateEvent(evt)) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value

    logTrace "Heating setpoint changed on ${sourceDevice.displayName}: ${newValue}"

    if (shouldSync("heatingSetpoint", sourceDevice.deviceId, newValue)) {
        runInMillis(syncDelay ?: 500, "syncHeatingSetpoint", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def coolingSetpointHandler(evt) {
    if (!syncCoolingSetpoint || !atomicState.autoSyncEnabled) return
    
    // Filter duplicate events
    if (isDuplicateEvent(evt)) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value

    logTrace "Cooling setpoint changed on ${sourceDevice.displayName}: ${newValue}"

    if (shouldSync("coolingSetpoint", sourceDevice.deviceId, newValue)) {
        runInMillis(syncDelay ?: 500, "syncCoolingSetpoint", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def fanModeHandler(evt) {
    if (!syncFanMode || !atomicState.autoSyncEnabled) return
    
    // Filter duplicate events
    if (isDuplicateEvent(evt)) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value

    logTrace "Fan mode changed on ${sourceDevice.displayName}: ${newValue}"

    if (shouldSync("thermostatFanMode", sourceDevice.deviceId, newValue)) {
        runInMillis(syncDelay ?: 500, "syncFanMode", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def temperatureHandler(evt) {
    if (!syncTemperature || !atomicState.autoSyncEnabled) return
    
    // Filter duplicate events
    if (isDuplicateEvent(evt)) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value

    logTrace "Temperature changed on ${sourceDevice.displayName}: ${newValue}"

    // Check if target device supports setTemperature command before proceeding
    if (!targetDevice.hasCommand('setTemperature')) {
        logDebug "Target device ${targetDevice.displayName} does not support setTemperature command, skipping sync"
        return
    }
    
    if (shouldSync("temperature", sourceDevice.deviceId, newValue)) {
        runInMillis(syncDelay ?: 500, "syncTemperature", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def operatingStateHandler(evt) {
    if (!syncOperatingState || !atomicState.autoSyncEnabled) return
    
    // Filter duplicate events
    if (isDuplicateEvent(evt)) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value

    logTrace "Operating state changed on ${sourceDevice.displayName}: ${newValue}"

    // Check if target device supports setThermostatOperatingState command before proceeding
    if (!targetDevice.hasCommand('setThermostatOperatingState')) {
        logDebug "Target device ${targetDevice.displayName} does not support setThermostatOperatingState command, skipping sync"
        return
    }
    
    if (shouldSync("thermostatOperatingState", sourceDevice.deviceId, newValue)) {
        runInMillis(syncDelay ?: 500, "syncOperatingState", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def batteryHandler(evt) {
    if (!syncBattery || !atomicState.autoSyncEnabled) return
    
    // Filter duplicate events
    if (isDuplicateEvent(evt)) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value

    logTrace "Battery level changed on ${sourceDevice.displayName}: ${newValue}%"

    // Check if target device supports setBattery command before proceeding
    if (!targetDevice.hasCommand('setBattery')) {
        logDebug "Target device ${targetDevice.displayName} does not support setBattery command, skipping sync"
        return
    }
    
    if (shouldSync("battery", sourceDevice.deviceId, newValue)) {
        runInMillis(syncDelay ?: 500, "syncBattery", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def healthStatusHandler(evt) {
    if (!syncHealthStatus || !atomicState.autoSyncEnabled) return
    
    // Filter duplicate events
    if (isDuplicateEvent(evt)) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value

    logTrace "Health status changed on ${sourceDevice.displayName}: ${newValue}"

    // Check if target device supports setHealthStatus command before proceeding
    if (!targetDevice.hasCommand('setHealthStatus')) {
        logDebug "Target device ${targetDevice.displayName} does not support setHealthStatus command, skipping sync"
        return
    }
    
    if (shouldSync("healthStatus", sourceDevice.deviceId, newValue)) {
        runInMillis(syncDelay ?: 500, "syncHealthStatus", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

// Synchronization Methods
def syncThermostatMode(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        // Set both source and target flags at the start of actual sync
        setSyncInProgress("thermostatMode", data.source, true)
        setSyncInProgress("thermostatMode", data.target, true)
        
        try {
            def oldValue = targetDevice.currentValue('thermostatMode')
            logInfo "Syncing thermostat mode: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value} (was ${oldValue})"
            targetDevice.setThermostatMode(data.value)
            
            // Schedule clearing of ALL sync flags after 2 seconds - allows everything to sync again
            unschedule("clearAllSyncFlagsDelayed")  // Cancel any previous timer
            runInMillis(2000, "clearAllSyncFlagsDelayed")
            logTrace "Scheduled ALL sync flags clearing in 2000ms"
        } catch (Exception e) {
            logWarn "Failed to sync thermostat mode: ${e.message}"
            // Clear flags immediately on error
            setSyncInProgress("thermostatMode", data.source, false)
            setSyncInProgress("thermostatMode", data.target, false)
        }
    }
}

def syncHeatingSetpoint(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        // Set both source and target flags at the start of actual sync
        setSyncInProgress("heatingSetpoint", data.source, true)
        setSyncInProgress("heatingSetpoint", data.target, true)
        
        try {
            def oldValue = targetDevice.currentValue('heatingSetpoint')
            logInfo "Syncing heating setpoint: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value}° (was ${oldValue}°)"
            targetDevice.setHeatingSetpoint(data.value as BigDecimal)
            
            // Schedule clearing of ALL sync flags after 2 seconds - allows everything to sync again
            unschedule("clearAllSyncFlagsDelayed")  // Cancel any previous timer
            runInMillis(2000, "clearAllSyncFlagsDelayed")
            logTrace "Scheduled ALL sync flags clearing in 2000ms"
        } catch (Exception e) {
            logWarn "Failed to sync heating setpoint: ${e.message}"
            // Clear flags immediately on error
            setSyncInProgress("heatingSetpoint", data.source, false)
            setSyncInProgress("heatingSetpoint", data.target, false)
        }
    }
}

def syncCoolingSetpoint(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        // Set both source and target flags at the start of actual sync
        setSyncInProgress("coolingSetpoint", data.source, true)
        setSyncInProgress("coolingSetpoint", data.target, true)
        
        try {
            def oldValue = targetDevice.currentValue('coolingSetpoint')
            logInfo "Syncing cooling setpoint: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value}° (was ${oldValue}°)"
            targetDevice.setCoolingSetpoint(data.value as BigDecimal)
            
            // Schedule clearing of ALL sync flags after 2 seconds - allows everything to sync again
            unschedule("clearAllSyncFlagsDelayed")  // Cancel any previous timer
            runInMillis(2000, "clearAllSyncFlagsDelayed")
            logTrace "Scheduled ALL sync flags clearing in 2000ms"
        } catch (Exception e) {
            logWarn "Failed to sync cooling setpoint: ${e.message}"
            // Clear flags immediately on error
            setSyncInProgress("coolingSetpoint", data.source, false)
            setSyncInProgress("coolingSetpoint", data.target, false)
        }
    }
}

def syncFanMode(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        // Set both source and target flags at the start of actual sync
        setSyncInProgress("thermostatFanMode", data.source, true)
        setSyncInProgress("thermostatFanMode", data.target, true)
        
        try {
            def oldValue = targetDevice.currentValue('thermostatFanMode')
            logInfo "Syncing fan mode: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value} (was ${oldValue})"
            targetDevice.setThermostatFanMode(data.value)
            
            // Schedule clearing of ALL sync flags after 2 seconds - allows everything to sync again
            unschedule("clearAllSyncFlagsDelayed")  // Cancel any previous timer
            runInMillis(2000, "clearAllSyncFlagsDelayed")
            logTrace "Scheduled ALL sync flags clearing in 2000ms"
        } catch (Exception e) {
            logWarn "Failed to sync fan mode: ${e.message}"
            // Clear flags immediately on error
            setSyncInProgress("thermostatFanMode", data.source, false)
            setSyncInProgress("thermostatFanMode", data.target, false)
        }
    }
}

def syncTemperature(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        // Set both source and target flags at the start of actual sync
        setSyncInProgress("temperature", data.source, true)
        setSyncInProgress("temperature", data.target, true)
        
        try {
            if (targetDevice.hasCommand('setTemperature')) {
                def oldValue = targetDevice.currentValue('temperature')
                logInfo "Syncing temperature: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value}° (was ${oldValue}°)"
                targetDevice.setTemperature(data.value as BigDecimal)
                
                // Schedule clearing of ALL sync flags after 2 seconds - allows everything to sync again
                unschedule("clearAllSyncFlagsDelayed")  // Cancel any previous timer
                runInMillis(2000, "clearAllSyncFlagsDelayed")
                logTrace "Scheduled ALL sync flags clearing in 2000ms"
            } else {
                logWarn "Target device ${targetDevice.displayName} does not support setTemperature command"
                // Clear flags immediately if command not supported
                setSyncInProgress("temperature", data.source, false)
                setSyncInProgress("temperature", data.target, false)
            }
        } catch (Exception e) {
            logWarn "Failed to sync temperature: ${e.message}"
            // Clear flags immediately on error
            setSyncInProgress("temperature", data.source, false)
            setSyncInProgress("temperature", data.target, false)
        }
    }
}

def syncOperatingState(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        // Set both source and target flags at the start of actual sync
        setSyncInProgress("thermostatOperatingState", data.source, true)
        setSyncInProgress("thermostatOperatingState", data.target, true)
        
        try {
            if (targetDevice.hasCommand('setThermostatOperatingState')) {
                def oldValue = targetDevice.currentValue('thermostatOperatingState')
                logInfo "Syncing operating state: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value} (was ${oldValue})"
                targetDevice.setThermostatOperatingState(data.value)
                
                // Schedule clearing of ALL sync flags after 2 seconds - allows everything to sync again
                unschedule("clearAllSyncFlagsDelayed")  // Cancel any previous timer
                runInMillis(2000, "clearAllSyncFlagsDelayed")
                logTrace "Scheduled ALL sync flags clearing in 2000ms"
            } else {
                logWarn "Target device ${targetDevice.displayName} does not support setThermostatOperatingState command"
                // Clear flags immediately if command not supported
                setSyncInProgress("thermostatOperatingState", data.source, false)
                setSyncInProgress("thermostatOperatingState", data.target, false)
            }
        } catch (Exception e) {
            logWarn "Failed to sync operating state: ${e.message}"
            // Clear flags immediately on error
            setSyncInProgress("thermostatOperatingState", data.source, false)
            setSyncInProgress("thermostatOperatingState", data.target, false)
        }
    }
}

def syncBattery(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        // Set both source and target flags at the start of actual sync
        setSyncInProgress("battery", data.source, true)
        setSyncInProgress("battery", data.target, true)
        
        try {
            if (targetDevice.hasCommand('setBattery')) {
                def oldValue = targetDevice.currentValue('battery')
                logInfo "Syncing battery: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value}% (was ${oldValue}%)"
                targetDevice.setBattery(data.value as Integer)
                
                // Schedule clearing of ALL sync flags after 2 seconds - allows everything to sync again
                unschedule("clearAllSyncFlagsDelayed")  // Cancel any previous timer
                runInMillis(2000, "clearAllSyncFlagsDelayed")
                logTrace "Scheduled ALL sync flags clearing in 2000ms"
            } else {
                logWarn "Target device ${targetDevice.displayName} does not support setBattery command"
                // Clear flags immediately if command not supported
                setSyncInProgress("battery", data.source, false)
                setSyncInProgress("battery", data.target, false)
            }
        } catch (Exception e) {
            logWarn "Failed to sync battery: ${e.message}"
            // Clear flags immediately on error
            setSyncInProgress("battery", data.source, false)
            setSyncInProgress("battery", data.target, false)
        }
    }
}

def syncHealthStatus(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        // Set both source and target flags at the start of actual sync
        setSyncInProgress("healthStatus", data.source, true)
        setSyncInProgress("healthStatus", data.target, true)
        
        try {
            if (targetDevice.hasCommand('setHealthStatus')) {
                def oldValue = targetDevice.currentValue('healthStatus')
                logInfo "Syncing health status: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value} (was ${oldValue})"
                targetDevice.setHealthStatus(data.value)
                
                // Schedule clearing of ALL sync flags after 2 seconds - allows everything to sync again
                unschedule("clearAllSyncFlagsDelayed")  // Cancel any previous timer
                runInMillis(2000, "clearAllSyncFlagsDelayed")
                logTrace "Scheduled ALL sync flags clearing in 2000ms"
            } else {
                logWarn "Target device ${targetDevice.displayName} does not support setHealthStatus command"
                // Clear flags immediately if command not supported
                setSyncInProgress("healthStatus", data.source, false)
                setSyncInProgress("healthStatus", data.target, false)
            }
        } catch (Exception e) {
            logWarn "Failed to sync health status: ${e.message}"
            // Clear flags immediately on error
            setSyncInProgress("healthStatus", data.source, false)
            setSyncInProgress("healthStatus", data.target, false)
        }
    }
}

// Loop Prevention Logic
def shouldSync(attribute, sourceDeviceId, newValue) {
    def key = "${attribute}_${sourceDeviceId}"
    
    // Check if sync is already in progress for this SPECIFIC attribute on either device
    def targetDeviceId = (sourceDeviceId == thermostat1.deviceId) ? thermostat2.deviceId : thermostat1.deviceId
    
    // Check if sync is in progress for this SAME attribute on target device (being written to)
    def targetInProgress = isSyncInProgress(attribute, targetDeviceId)
    if (targetInProgress) {
        logDebug "Sync already in progress for ${attribute} on target device (${targetDeviceId}), skipping"
        return false
    }
    
    // Check if sync is in progress for this SAME attribute on source device (to prevent rapid-fire changes)
    def sourceInProgress = isSyncInProgress(attribute, sourceDeviceId)
    if (sourceInProgress) {
        logDebug "Sync already in progress for ${attribute} on source device (${sourceDeviceId}), skipping"
        return false
    }
    
    logTrace "shouldSync check passed for ${attribute}: source ${sourceDeviceId} → target ${targetDeviceId}"
    
    // Check sync counter to prevent runaway loops
    def currentCount = getSyncCounter(key)
    if (currentCount >= (maxSyncAttempts ?: 3)) {
        logWarn "Maximum sync attempts reached for ${attribute}, resetting counter"
        resetSyncCounter([key: key])
        return false
    }
    
    incrementSyncCounter(key)
    
    // Reset counter after successful sync
    runInMillis(5000, "resetSyncCounter", [data: [key: key]])
    
    return true
}

def setSyncInProgress(attribute, deviceId, inProgress) {
    def syncState = atomicState.syncInProgress ?: [:]
    def key = "${attribute}_${deviceId}"
    syncState[key] = inProgress
    atomicState.syncInProgress = syncState
    logDebug "Set sync in progress: ${key} = ${inProgress}"
}

def isSyncInProgress(attribute, deviceId) {
    def syncState = atomicState.syncInProgress ?: [:]
    def key = "${attribute}_${deviceId}"
    def result = syncState[key] ?: false
    
    if (result) {
        logTrace "isSyncInProgress(${attribute}, ${deviceId}) = ${result} [key: ${key}]"
        logTrace "Current sync state: ${syncState}"
    }
    
    return result
}

def getSyncCounter(key) {
    def counters = atomicState.syncCounter ?: [:]
    return counters[key] ?: 0
}

def incrementSyncCounter(key) {
    def counters = atomicState.syncCounter ?: [:]
    counters[key] = (counters[key] ?: 0) + 1
    atomicState.syncCounter = counters
    logDebug "Incremented sync counter: ${key} = ${counters[key]}"
}

def resetSyncCounter(data = null) {
    if (data?.key) {
        def counters = atomicState.syncCounter ?: [:]
        counters[data.key] = 0
        atomicState.syncCounter = counters
        logDebug "Reset sync counter: ${data.key}"
    } else {
        atomicState.syncCounter = [:]
        logDebug "Reset all sync counters"
    }
}

def clearSyncFlags(data) {
    if (data?.attribute && data?.sourceId && data?.targetId) {
        setSyncInProgress(data.attribute, data.sourceId, false)
        setSyncInProgress(data.attribute, data.targetId, false)
        logDebug "Cleared sync flags for ${data.attribute}: source ${data.sourceId}, target ${data.targetId}"
    }
}

def clearSpecificSyncFlag(data) {
    if (data?.attribute && data?.deviceId) {
        setSyncInProgress(data.attribute, data.deviceId, false)
        logDebug "Cleared specific sync flag: ${data.attribute}_${data.deviceId}"
    }
}

def clearAllSyncFlagsDelayed() {
    // Clear all sync flags AND counters after a delay - allows everything to sync again
    atomicState.syncInProgress = [:]
    atomicState.syncCounter = [:]
    logDebug "Cleared ALL sync flags AND counters - ready for new syncs"
}

def clearAllSyncFlags() {
    atomicState.syncInProgress = [:]
    atomicState.syncCounter = [:]
    logDebug "Cleared all sync flags and counters"
}

// Event Deduplication Methods
def isDuplicateEvent(evt, timeWindowMs = 3000) {
    def deviceId = evt.device.deviceId
    def attributeName = evt.name
    def currentValue = evt.value?.toString() ?: "null"
    def currentTime = now()
    
    // Get the previous value for comparison
    def previousValue = evt.device.currentValue(attributeName)?.toString() ?: "unknown"
    
    // Create event signature including device, attribute, and value
    def eventSignature = "${deviceId}_${attributeName}_${currentValue}"
    
    // Get existing event timestamps
    def recentEvents = atomicState.recentEvents ?: [:]
    def lastEventTime = recentEvents[eventSignature] ?: 0
    
    // Check if this is a duplicate (same device, same attribute, same value) within the time window
    if (currentTime - lastEventTime < timeWindowMs) {
        logDebug "Duplicate event filtered: ${evt.device.displayName} ${attributeName} = ${currentValue} (${currentTime - lastEventTime}ms ago)"
        return true
    }
    
    // Store this event timestamp
    recentEvents[eventSignature] = currentTime
    
    // Clean up old events (older than 10 seconds to prevent memory buildup)
    def cleanupThreshold = currentTime - 10000
    recentEvents = recentEvents.findAll { key, timestamp -> timestamp > cleanupThreshold }
    
    atomicState.recentEvents = recentEvents
    
    logDebug "Event processed: ${evt.device.displayName} ${attributeName} = ${currentValue} (was: ${previousValue})"
    return false
}

// Helper Methods
def getDeviceById(deviceId) {
    if (thermostat1?.deviceId == deviceId) return thermostat1
    if (thermostat2?.deviceId == deviceId) return thermostat2
    return null
}

// Logging Methods
def logInfo(msg) {
    if (txtEnable) log.info msg
}

def logDebug(msg) {
    if (logEnable) log.debug msg
}

def logTrace(msg) {
    if (traceEnable) log.trace msg
}

def logWarn(msg) {
    log.warn msg
}

// Auto-disable debug logging after 30 minutes
def logsOff() {
    logWarn "Debug and trace logging disabled"
    app.updateSetting("logEnable", [value: "false", type: "bool"])
    app.updateSetting("traceEnable", [value: "false", type: "bool"])
}

