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
 *
 */

import groovy.transform.Field
@Field static final String VERSION = "1.0.0"
@Field static final String COMPILE_TIME = '2025/08/19 8:56 PM'

definition(
    name: "Thermostats Sync",
    namespace: "kkossev",
    author: "Krassimir Kossev",
    description: "Synchronizes the main attributes of two thermostats bidirectionally",
    category: "Climate Control",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: false
)

preferences {
    page(name: "mainPage", title: "Thermostats Sync Configuration", install: true, uninstall: true) {
        section("Select Thermostats to Synchronize") {
            input "thermostat1", "capability.thermostat", title: "First Thermostat", required: true, submitOnChange: true
            input "thermostat2", "capability.thermostat", title: "Second Thermostat", required: true, submitOnChange: true
        }
        
        section("Synchronization Options") {
            input "syncThermostatMode", "bool", title: "Synchronize Thermostat Mode (off/heat/cool/auto)", defaultValue: true
            input "syncHeatingSetpoint", "bool", title: "Synchronize Heating Setpoint", defaultValue: true
            input "syncCoolingSetpoint", "bool", title: "Synchronize Cooling Setpoint", defaultValue: true
            input "syncFanMode", "bool", title: "Synchronize Fan Mode", defaultValue: true
        }
        
        section("Loop Prevention") {
            input "syncDelay", "number", title: "Synchronization Delay (milliseconds)", description: "Delay before applying changes to prevent loops", defaultValue: 500, range: "100..5000"
            input "maxSyncAttempts", "number", title: "Maximum Sync Attempts per Change", description: "Prevent runaway synchronization", defaultValue: 3, range: "1..10"
        }
        
        section("Logging") {
            input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
        }
        
        if (thermostat1 && thermostat2) {
            section("Current Status") {
                paragraph "<b>First Thermostat (${thermostat1.displayName}):</b><br>" +
                         "Mode: ${thermostat1.currentValue('thermostatMode')}<br>" +
                         "Heating Setpoint: ${thermostat1.currentValue('heatingSetpoint')}°<br>" +
                         "Cooling Setpoint: ${thermostat1.currentValue('coolingSetpoint')}°<br>" +
                         "Fan Mode: ${thermostat1.currentValue('thermostatFanMode')}<br>" +
                         "Operating State: ${thermostat1.currentValue('thermostatOperatingState')}"
                         
                paragraph "<b>Second Thermostat (${thermostat2.displayName}):</b><br>" +
                         "Mode: ${thermostat2.currentValue('thermostatMode')}<br>" +
                         "Heating Setpoint: ${thermostat2.currentValue('heatingSetpoint')}°<br>" +
                         "Cooling Setpoint: ${thermostat2.currentValue('coolingSetpoint')}°<br>" +
                         "Fan Mode: ${thermostat2.currentValue('thermostatFanMode')}<br>" +
                         "Operating State: ${thermostat2.currentValue('thermostatOperatingState')}"
            }
        }
        
        if (thermostat1 && thermostat2) {
            section("Manual Sync Controls") {
                input "syncT1toT2", "button", title: "Sync Thermostat1 → Thermostat2"
                input "syncT2toT1", "button", title: "Sync Thermostat2 → Thermostat1"
                input "startAutoSync", "button", title: "Start AutoSync"
                input "stopAutoSync", "button", title: "Stop AutoSync"
                
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
    initialize()
}

def updated() {
    logDebug "Thermostats Sync v${VERSION} updated (compiled: ${COMPILE_TIME})"
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
    }
}

def initialize() {
    logDebug "Initializing Thermostats Sync v${VERSION}"
    
    // Initialize sync state tracking
    atomicState.syncInProgress = [:]
    atomicState.syncCounter = [:]
    
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
    
    try {
        // Sync thermostat mode if enabled
        if (syncThermostatMode) {
            def mode = sourceDevice.currentValue('thermostatMode')
            if (mode) {
                logInfo "Syncing thermostat mode: ${mode}"
                targetDevice.setThermostatMode(mode)
            }
        }
        
        // Sync heating setpoint if enabled
        if (syncHeatingSetpoint) {
            def heatingSetpoint = sourceDevice.currentValue('heatingSetpoint')
            if (heatingSetpoint) {
                logInfo "Syncing heating setpoint: ${heatingSetpoint}°"
                targetDevice.setHeatingSetpoint(heatingSetpoint as BigDecimal)
            }
        }
        
        // Sync cooling setpoint if enabled
        if (syncCoolingSetpoint) {
            def coolingSetpoint = sourceDevice.currentValue('coolingSetpoint')
            if (coolingSetpoint) {
                logInfo "Syncing cooling setpoint: ${coolingSetpoint}°"
                targetDevice.setCoolingSetpoint(coolingSetpoint as BigDecimal)
            }
        }
        
        // Sync fan mode if enabled
        if (syncFanMode) {
            def fanMode = sourceDevice.currentValue('thermostatFanMode')
            if (fanMode) {
                logInfo "Syncing fan mode: ${fanMode}"
                targetDevice.setThermostatFanMode(fanMode)
            }
        }
        
        logInfo "Manual sync completed successfully"
        
    } catch (Exception e) {
        logWarn "Manual sync failed: ${e.message}"
    }
}

// Event Handlers
def thermostatModeHandler(evt) {
    if (!syncThermostatMode || !atomicState.autoSyncEnabled) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value
    
    logDebug "Thermostat mode changed on ${sourceDevice.displayName}: ${newValue}"
    
    if (shouldSync("thermostatMode", sourceDevice.deviceId, newValue)) {
        // Set sync flag immediately to prevent additional syncs
        setSyncInProgress("thermostatMode", sourceDevice.deviceId, true)
        runInMillis(syncDelay ?: 500, "syncThermostatMode", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def heatingSetpointHandler(evt) {
    if (!syncHeatingSetpoint || !atomicState.autoSyncEnabled) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value
    
    logDebug "Heating setpoint changed on ${sourceDevice.displayName}: ${newValue}"
    
    if (shouldSync("heatingSetpoint", sourceDevice.deviceId, newValue)) {
        // Set sync flag immediately to prevent additional syncs
        setSyncInProgress("heatingSetpoint", sourceDevice.deviceId, true)
        runInMillis(syncDelay ?: 500, "syncHeatingSetpoint", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def coolingSetpointHandler(evt) {
    if (!syncCoolingSetpoint || !atomicState.autoSyncEnabled) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value
    
    logDebug "Cooling setpoint changed on ${sourceDevice.displayName}: ${newValue}"
    
    if (shouldSync("coolingSetpoint", sourceDevice.deviceId, newValue)) {
        // Set sync flag immediately to prevent additional syncs
        setSyncInProgress("coolingSetpoint", sourceDevice.deviceId, true)
        runInMillis(syncDelay ?: 500, "syncCoolingSetpoint", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

def fanModeHandler(evt) {
    if (!syncFanMode || !atomicState.autoSyncEnabled) return
    
    def sourceDevice = evt.device
    def targetDevice = (sourceDevice.deviceId == thermostat1.deviceId) ? thermostat2 : thermostat1
    def newValue = evt.value
    
    logDebug "Fan mode changed on ${sourceDevice.displayName}: ${newValue}"
    
    if (shouldSync("thermostatFanMode", sourceDevice.deviceId, newValue)) {
        // Set sync flag immediately to prevent additional syncs
        setSyncInProgress("thermostatFanMode", sourceDevice.deviceId, true)
        runInMillis(syncDelay ?: 500, "syncFanMode", [data: [target: targetDevice.deviceId, value: newValue, source: sourceDevice.deviceId]])
    }
}

// Synchronization Methods
def syncThermostatMode(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        setSyncInProgress("thermostatMode", data.target, true)
        
        try {
            logInfo "Syncing thermostat mode: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value}"
            targetDevice.setThermostatMode(data.value)
        } catch (Exception e) {
            logWarn "Failed to sync thermostat mode: ${e.message}"
        } finally {
            // Clear sync flags for both source and target devices
            setSyncInProgress("thermostatMode", data.source, false)
            setSyncInProgress("thermostatMode", data.target, false)
        }
    }
}

def syncHeatingSetpoint(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        setSyncInProgress("heatingSetpoint", data.target, true)
        
        try {
            logInfo "Syncing heating setpoint: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value}°"
            targetDevice.setHeatingSetpoint(data.value as BigDecimal)
        } catch (Exception e) {
            logWarn "Failed to sync heating setpoint: ${e.message}"
        } finally {
            // Clear sync flags for both source and target devices
            setSyncInProgress("heatingSetpoint", data.source, false)
            setSyncInProgress("heatingSetpoint", data.target, false)
        }
    }
}

def syncCoolingSetpoint(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        setSyncInProgress("coolingSetpoint", data.target, true)
        
        try {
            logInfo "Syncing cooling setpoint: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value}°"
            targetDevice.setCoolingSetpoint(data.value as BigDecimal)
        } catch (Exception e) {
            logWarn "Failed to sync cooling setpoint: ${e.message}"
        } finally {
            // Clear sync flags for both source and target devices
            setSyncInProgress("coolingSetpoint", data.source, false)
            setSyncInProgress("coolingSetpoint", data.target, false)
        }
    }
}

def syncFanMode(data) {
    def targetDevice = getDeviceById(data.target)
    def sourceDevice = getDeviceById(data.source)
    
    if (targetDevice && sourceDevice) {
        setSyncInProgress("thermostatFanMode", data.target, true)
        
        try {
            logInfo "Syncing fan mode: ${sourceDevice.displayName} → ${targetDevice.displayName} = ${data.value}"
            targetDevice.setThermostatFanMode(data.value)
        } catch (Exception e) {
            logWarn "Failed to sync fan mode: ${e.message}"
        } finally {
            // Clear sync flags for both source and target devices
            setSyncInProgress("thermostatFanMode", data.source, false)
            setSyncInProgress("thermostatFanMode", data.target, false)
        }
    }
}

// Loop Prevention Logic
def shouldSync(attribute, sourceDeviceId, newValue) {
    def key = "${attribute}_${sourceDeviceId}"
    
    // Check if sync is already in progress for this attribute on either device
    def targetDeviceId = (sourceDeviceId == thermostat1.deviceId) ? thermostat2.deviceId : thermostat1.deviceId
    
    // Check if sync is in progress on target device (being written to)
    if (isSyncInProgress(attribute, targetDeviceId)) {
        logDebug "Sync already in progress for ${attribute} on target device, skipping"
        return false
    }
    
    // Check if sync is in progress on source device (to prevent rapid-fire changes)
    if (isSyncInProgress(attribute, sourceDeviceId)) {
        logDebug "Sync already in progress for ${attribute} on source device, skipping"
        return false
    }
    
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
    return syncState[key] ?: false
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

def logWarn(msg) {
    log.warn msg
}

// Auto-disable debug logging after 30 minutes
def logsOff() {
    logWarn "Debug logging disabled"
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

// Schedule logs off
if (logEnable) runIn(1800, logsOff)

