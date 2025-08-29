/**
 *  Human Activity Check
 *  
 *  Description: Monitor motion, contact, and acceleration sensors for inactivity.
 *               Send notifications and optionally control a virtual switch when no
 *               activity is detected for a configured period.
 *
 *  Author: GitHub Copilot
 *  Date: 2025-08-29
 *  Version: 1.0.0
 */

definition(
    name: "Human Activity Check",
    namespace: "user",
    author: "GitHub Copilot",
    description: "Monitor sensors for human activity and alert when no activity is detected for a specified period",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage", title: "Human Activity Check", install: true, uninstall: true) {
        section("Device Selection") {
            input "motionSensors", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false
            input "contactSensors", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: false
            input "accelerationSensors", "capability.accelerationSensor", title: "Acceleration Sensors", multiple: true, required: false
            input "buttons", "capability.pushableButton", title: "Buttons", multiple: true, required: false
            input "switches", "capability.switch", title: "Switches", multiple: true, required: false
            input "locks", "capability.lock", title: "Locks", multiple: true, required: false
        }
        
        section("Timing Settings") {
            input "inactivityThreshold", "number", title: "Inactivity Threshold (minutes)", required: true, defaultValue: 60, range: "1..1440"
        }
        
        section("Notifications") {
            input "notificationDevices", "capability.notification", title: "Notification Devices", multiple: true, required: false
            input "notifyOnce", "bool", title: "Notify only once per alarm cycle (re-arms on activity)", defaultValue: true
        }
        
        section("Optional Virtual Switch") {
            input "virtualSwitch", "capability.switch", title: "Virtual Switch (turns on during alarm)", required: false
        }
        
        section("Testing") {
            input "testAlarm", "button", title: "Test Alarm"
            input "refreshStates", "button", title: "Refresh Device States"
        }
        
        section("Status") {
            if (state.lastCheck) {
                paragraph getStatusTable()
            } else {
                paragraph "No status data available yet. Install the app and wait for the first check cycle."
            }
        }
    }
}

def installed() {
    log.info "Human Activity Check installed"
    initialize()
}

def updated() {
    log.info "Human Activity Check updated"
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    log.info "Human Activity Check uninstalled"
    unsubscribe()
    unschedule()
}

def initialize() {
    log.info "Initializing Human Activity Check"
    
    // Initialize state if needed
    if (!state.lastChange) state.lastChange = [:]
    if (!state.lastStatus) state.lastStatus = [:]
    if (!state.alarmActive) state.alarmActive = false
    
    // Get all selected devices
    def allDevices = getAllSelectedDevices()
    
    if (!allDevices) {
        log.warn "No devices selected. Please select at least one sensor."
        return
    }
    
    // Subscribe to device events
    subscribeToDevices()
    
    // Initialize current device states
    initializeDeviceStates()
    
    // Schedule periodic checks every 5 minutes
    runEvery5Minutes("checkActivity")
    
    log.info "Initialized with ${allDevices.size()} devices"
}

def subscribeToDevices() {
    // Subscribe to motion sensors
    if (motionSensors) {
        subscribe(motionSensors, "motion", deviceEventHandler)
    }
    
    // Subscribe to contact sensors  
    if (contactSensors) {
        subscribe(contactSensors, "contact", deviceEventHandler)
    }
    
    // Subscribe to acceleration sensors
    if (accelerationSensors) {
        subscribe(accelerationSensors, "acceleration", deviceEventHandler)
    }
    
    // Subscribe to buttons
    if (buttons) {
        subscribe(buttons, "pushed", deviceEventHandler)
    }
    
    // Subscribe to switches
    if (switches) {
        subscribe(switches, "switch", deviceEventHandler)
    }
    
    // Subscribe to locks
    if (locks) {
        subscribe(locks, "lock", deviceEventHandler)
    }
}

def initializeDeviceStates() {
    log.debug "Initializing device states..."
    
    def allDevices = getAllSelectedDevices()
    def now = now()
    
    allDevices.each { device ->
        try {
            def currentValue = null
            def attributeName = null
            
            // Determine the attribute to read based on device capabilities
            if (device.hasCapability("MotionSensor")) {
                attributeName = "motion"
                currentValue = device.currentValue("motion")
            } else if (device.hasCapability("ContactSensor")) {
                attributeName = "contact"
                currentValue = device.currentValue("contact")
            } else if (device.hasCapability("AccelerationSensor")) {
                attributeName = "acceleration"
                currentValue = device.currentValue("acceleration")
            } else if (device.hasCapability("PushableButton")) {
                attributeName = "pushed"
                // For buttons, we don't read current state as they're event-based
                currentValue = "ready"
            } else if (device.hasCapability("Switch")) {
                attributeName = "switch"
                currentValue = device.currentValue("switch")
            } else if (device.hasCapability("Lock")) {
                attributeName = "lock"
                currentValue = device.currentValue("lock")
            }
            
            if (currentValue != null && currentValue != "null") {
                // Only update if we don't already have data for this device
                if (!state.lastStatus[device.id]) {
                    state.lastStatus[device.id] = currentValue.toString()
                    state.lastChange[device.id] = now
                    log.debug "Initialized ${device.displayName}: ${attributeName} = ${currentValue}"
                }
            } else {
                log.warn "Could not read current state for ${device.displayName} (${attributeName})"
            }
        } catch (Exception e) {
            log.error "Error initializing state for ${device.displayName}: ${e.message}"
        }
    }
}

def deviceEventHandler(evt) {
    log.debug "Device event: ${evt.device.displayName} - ${evt.name}: ${evt.value}"
    
    // Update last change timestamp and status
    state.lastChange[evt.device.id] = now()
    state.lastStatus[evt.device.id] = evt.value
    
    // Check if this activity should clear an active alarm
    if (state.alarmActive && isActivityEvent(evt)) {
        clearAlarm("Activity detected: ${evt.device.displayName} - ${evt.name}: ${evt.value}")
    }
}

def isActivityEvent(evt) {
    // Define what constitutes "activity" for each sensor type
    switch(evt.name) {
        case "motion":
            return evt.value == "active"
        case "contact":
            return evt.value == "open"
        case "acceleration":
            return evt.value == "active"
        case "pushed":
            return true // Any button push is activity
        case "switch":
            return true // Any switch change (on/off) is activity
        case "lock":
            return evt.value == "unlocked" // Unlocking is activity (someone accessing)
        default:
            return false
    }
}

def checkActivity() {
    log.debug "Checking activity..."
    
    def allDevices = getAllSelectedDevices()
    if (!allDevices) {
        log.warn "No devices to check"
        return
    }
    
    def now = now()
    def thresholdMs = (inactivityThreshold ?: 60) * 60 * 1000
    def mostRecentActivity = 0
    def inactiveDevices = []
    def activeDevices = []
    def devicesWithData = 0
    
    // Check each device's last activity using actual device event times
    allDevices.each { device ->
        def lastEventTime = getDeviceLastEventTime(device)
        
        if (lastEventTime > 0) {
            devicesWithData++
            if (lastEventTime > mostRecentActivity) {
                mostRecentActivity = lastEventTime
            }
            
            def inactiveTime = now - lastEventTime
            if (inactiveTime > thresholdMs) {
                inactiveDevices << [
                    device: device,
                    lastChange: lastEventTime,
                    inactiveTime: inactiveTime
                ]
            } else {
                // Device has recent activity (within threshold)
                activeDevices << [
                    device: device,
                    lastChange: lastEventTime,
                    inactiveTime: inactiveTime
                ]
            }
        }
    }
    
    def timeSinceLastActivity = mostRecentActivity > 0 ? now - mostRecentActivity : 0
    state.lastCheck = now
    
    // Only trigger alarm if ALL devices with data are inactive beyond threshold
    // If ANY device has recent activity, no alarm should be triggered
    def shouldTriggerAlarm = devicesWithData > 0 && activeDevices.size() == 0 && inactiveDevices.size() == devicesWithData
    
    if (shouldTriggerAlarm && !state.alarmActive) {
        triggerAlarm(timeSinceLastActivity, inactiveDevices, allDevices)
    } else if (state.alarmActive && activeDevices.size() > 0) {
        // Clear alarm if any device has recent activity
        def recentDevice = activeDevices[0].device
        clearAlarm("Recent activity detected: ${recentDevice.displayName} (${formatDuration(activeDevices[0].inactiveTime)} ago)")
    }
    
    log.debug "Activity check complete. Devices with data: ${devicesWithData}/${allDevices.size()}, Active devices: ${activeDevices.size()}, Inactive devices: ${inactiveDevices.size()}, Most recent activity: ${mostRecentActivity > 0 ? formatDuration(timeSinceLastActivity) + ' ago' : 'No data yet'}"
}

def triggerAlarm(timeSinceLastActivity, inactiveDevices, allDevices) {
    log.warn "Triggering inactivity alarm - ${formatDuration(timeSinceLastActivity)} since last activity"
    
    state.alarmActive = true
    
    // Turn on virtual switch if configured
    if (virtualSwitch) {
        virtualSwitch.on()
        log.info "Turned on virtual switch: ${virtualSwitch.displayName}"
    }
    
    // Send notifications if configured and not in "notify once" mode or first notification
    if (notificationDevices && (!notifyOnce || !state.lastNotificationSent)) {
        def message = buildAlarmMessage(timeSinceLastActivity, inactiveDevices, allDevices)
        sendNotifications(message)
        state.lastNotificationSent = now()
    }
}

def clearAlarm(reason) {
    log.info "Clearing alarm: ${reason}"
    
    state.alarmActive = false
    state.lastNotificationSent = null
    
    // Turn off virtual switch if configured
    if (virtualSwitch) {
        virtualSwitch.off()
        log.info "Turned off virtual switch: ${virtualSwitch.displayName}"
    }
}

def buildAlarmMessage(timeSinceLastActivity, inactiveDevices, allDevices) {
    def threshold = formatDuration((inactivityThreshold ?: 60) * 60 * 1000)
    def actualTime = formatDuration(timeSinceLastActivity)
    
    def message = "🚨 Human Activity Alert\n"
    message += "No activity for ${actualTime} (limit: ${threshold})\n"
    
    // Get all devices with their last event times and sort by most recent activity
    def now = now()
    def devicesWithActivity = []
    
    allDevices.each { device ->
        def lastEventTime = getDeviceLastEventTime(device)
        if (lastEventTime > 0) {
            devicesWithActivity << [
                device: device,
                lastEventTime: lastEventTime,
                inactiveTime: now - lastEventTime
            ]
        }
    }
    
    if (devicesWithActivity) {
        // Sort by most recent activity (smallest inactiveTime first)
        def sortedByActivity = devicesWithActivity.sort { it.inactiveTime }
        def mostRecentDevices = sortedByActivity.take(3)
        
        message += "\nLast active devices:\n"
        mostRecentDevices.each { deviceInfo ->
            def room = deviceInfo.device.getRoomName() ?: "Unknown"
            def duration = formatDuration(deviceInfo.inactiveTime)
            def deviceName = deviceInfo.device.displayName
            message += "• ${deviceName} (${room}): ${duration} ago\n"
        }
        
        // Remove the trailing newline
        message = message.trim()
    }
    
    return message
}

def sendNotifications(message) {
    log.info "Sending notification: ${message}"
    
    notificationDevices.each { device ->
        try {
            device.deviceNotification(message)
        } catch (Exception e) {
            log.error "Failed to send notification to ${device.displayName}: ${e.message}"
        }
    }
}

def getAllSelectedDevices() {
    def devices = []
    if (motionSensors) devices.addAll(motionSensors)
    if (contactSensors) devices.addAll(contactSensors)
    if (accelerationSensors) devices.addAll(accelerationSensors)
    if (buttons) devices.addAll(buttons)
    if (switches) devices.addAll(switches)
    if (locks) devices.addAll(locks)
    return devices
}

def getStatusTable() {
    def allDevices = getAllSelectedDevices()
    if (!allDevices) {
        return "<p>No devices selected.</p>"
    }
    
    def now = now()
    def html = """
    <style>
        .activity-table { border-collapse: collapse; width: 100%; margin: 10px 0; }
        .activity-table th, .activity-table td { 
            border: 1px solid #ddd; 
            padding: 8px; 
            text-align: left; 
        }
        .activity-table th { background-color: #f2f2f2; }
        .activity-table tr:nth-child(even) { background-color: #f9f9f9; }
        .status-active { color: green; font-weight: bold; }
        .status-inactive { color: red; }
        .status-unknown { color: gray; }
    </style>
    <table class="activity-table">
        <tr>
            <th>Device</th>
            <th>Room</th>
            <th>Type</th>
            <th>Status</th>
            <th>Last Change</th>
            <th>Age</th>
        </tr>
    """
    
    allDevices.sort { it.displayName }.each { device ->
        def deviceType = getDeviceType(device)
        def room = device.getRoomName() ?: "—"
        
        // Get the actual last event timestamp from the device
        def lastEventTime = getDeviceLastEventTime(device)
        def status = state.lastStatus[device.id] ?: "unknown"
        def statusClass = getStatusClass(deviceType, status)
        
        def lastChangeStr = lastEventTime > 0 ? 
            new Date(lastEventTime).format("MM/dd HH:mm:ss") : "—"
        def ageStr = lastEventTime > 0 ? 
            formatDuration(now - lastEventTime) : "—"
        
        html += """
        <tr>
            <td>${device.displayName}</td>
            <td>${room}</td>
            <td>${deviceType}</td>
            <td class="${statusClass}">${status}</td>
            <td>${lastChangeStr}</td>
            <td>${ageStr}</td>
        </tr>
        """
    }
    
    html += "</table>"
    
    def lastCheckStr = state.lastCheck ? 
        new Date(state.lastCheck).format("MM/dd/yyyy HH:mm:ss") : "Never"
    
    def devicesWithData = allDevices.count { device -> 
        state.lastChange[device.id] != null && state.lastChange[device.id] > 0 
    }
    
    html += "<p><small>Last check: ${lastCheckStr} | "
    html += "Alarm status: ${state.alarmActive ? 'ACTIVE' : 'Clear'} | "
    html += "Devices with data: ${devicesWithData}/${allDevices.size()}</small></p>"
    
    return html
}

def getDeviceType(device) {
    if (device.hasCapability("MotionSensor")) return "Motion"
    if (device.hasCapability("ContactSensor")) return "Contact"  
    if (device.hasCapability("AccelerationSensor")) return "Acceleration"
    if (device.hasCapability("PushableButton")) return "Button"
    if (device.hasCapability("Switch")) return "Switch"
    if (device.hasCapability("Lock")) return "Lock"
    return "Unknown"
}

def getStatusClass(deviceType, status) {
    switch(deviceType) {
        case "Motion":
            return status == "active" ? "status-active" : "status-inactive"
        case "Contact":
            return status == "open" ? "status-active" : "status-inactive"
        case "Acceleration":
            return status == "active" ? "status-active" : "status-inactive"
        case "Button":
            return "status-inactive" // Buttons don't have persistent active state
        case "Switch":
            return status == "on" ? "status-active" : "status-inactive"
        case "Lock":
            return status == "unlocked" ? "status-active" : "status-inactive"
        default:
            return "status-unknown"
    }
}

def getDeviceLastEventTime(device) {
    def lastEventTime = 0
    
    try {
        // Get the relevant attribute name based on device type
        def attributeName = null
        if (device.hasCapability("MotionSensor")) {
            attributeName = "motion"
        } else if (device.hasCapability("ContactSensor")) {
            attributeName = "contact"
        } else if (device.hasCapability("AccelerationSensor")) {
            attributeName = "acceleration"
        } else if (device.hasCapability("PushableButton")) {
            attributeName = "pushed"
        } else if (device.hasCapability("Switch")) {
            attributeName = "switch"
        } else if (device.hasCapability("Lock")) {
            attributeName = "lock"
        }
        
        if (attributeName) {
            // Get the last event for this attribute
            def events = device.eventsSince(new Date(now() - (30 * 24 * 60 * 60 * 1000)), [max: 1]) // Last 30 days, max 1 event
            def relevantEvent = events.find { it.name == attributeName }
            
            if (relevantEvent) {
                lastEventTime = relevantEvent.date.time
            } else {
                // Fallback: try to get any recent event for this attribute
                def allEvents = device.events([max: 100]) // Get more events if needed
                relevantEvent = allEvents.find { it.name == attributeName }
                if (relevantEvent) {
                    lastEventTime = relevantEvent.date.time
                }
            }
        }
    } catch (Exception e) {
        log.warn "Error getting last event time for ${device.displayName}: ${e.message}"
    }
    
    return lastEventTime
}

def formatDuration(milliseconds) {
    if (milliseconds <= 0) return "0s"
    
    def totalSeconds = Math.floor(milliseconds / 1000) as int
    def totalMinutes = Math.floor(totalSeconds / 60) as int
    def totalHours = Math.floor(totalMinutes / 60) as int
    def days = Math.floor(totalHours / 24) as int
    
    def hours = totalHours % 24
    def minutes = totalMinutes % 60
    def seconds = totalSeconds % 60
    
    def parts = []
    
    if (days > 0) {
        parts << "${days}d"
    }
    if (hours > 0) {
        parts << "${hours}h"
    }
    if (minutes > 0) {
        parts << "${minutes}m"
    }
    if (seconds > 0 && parts.size() < 2) {
        parts << "${seconds}s"
    }
    
    return parts.take(2).join(" ")
}

// Button handler for test alarm
def appButtonHandler(btn) {
    switch(btn) {
        case "testAlarm":
            testAlarmAction()
            break
        case "refreshStates":
            refreshDeviceStatesAction()
            break
    }
}

def testAlarmAction() {
    log.info "Test alarm triggered by user"
    
    def testInactiveDevices = []
    def allDevices = getAllSelectedDevices()
    
    allDevices.each { device ->
        testInactiveDevices << [
            device: device,
            lastChange: now() - (2 * 60 * 60 * 1000), // 2 hours ago
            inactiveTime: 2 * 60 * 60 * 1000
        ]
    }
    
    def message = buildAlarmMessage(2 * 60 * 60 * 1000, testInactiveDevices, allDevices)
    message = "[TEST] " + message
    
    if (notificationDevices) {
        sendNotifications(message)
    }
    
    if (virtualSwitch) {
        virtualSwitch.on()
        runIn(10, "turnOffTestSwitch") // Turn off after 10 seconds for test
    }
    
    log.info "Test alarm completed"
}

def refreshDeviceStatesAction() {
    log.info "Refreshing device states by user request"
    
    // Clear existing state and reinitialize
    state.lastChange = [:]
    state.lastStatus = [:]
    
    // Reinitialize device states
    initializeDeviceStates()
    
    // Run immediate activity check
    checkActivity()
    
    log.info "Device states refreshed"
}

def turnOffTestSwitch() {
    if (virtualSwitch && !state.alarmActive) {
        virtualSwitch.off()
        log.info "Test alarm: turned off virtual switch"
    }
}
