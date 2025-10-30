library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Notification helpers",
    name: "notification1",
    namespace: "drozovyk"   
)

@Field static Map          notificationTypeEvents = [
    (0x00): [
            label: "Disabled",
            name: "Disabled",
            events: [
                (0x00): "Idle",
                (0xFF): "Any"]
            ],
    (0x01): [
            label: "Smoke Alarm",
            name: "SmokeAlarm",
            events: [
                (0x00): "Idle",
                (0x01): "Alarm (location provided)",
                (0x02): "Alarm",
                (0x03): "Alarm test",
                (0x04): "Replacement required",
                (0x05): "Replacement required; End-of-life",
                (0x06): "Alarm silenced",
                (0x07): "Maintenance required (periodic)",
                (0x08): "Maintenance required (dust in device)",
                (0xFF): "Any"]
            ],
    (0x02): [
            label: "CO Alarm",
            name: "COAlarm",
            events: [
                (0x00): "Idle",
                (0x01): "Alarm (location provided)",
                (0x02): "Alarm",
                (0x03): "Alarm test",
                (0x04): "Replacement required",
                (0x05): "Replacement required; End-of-life",
                (0x06): "Alarm silenced",
                (0x07): "Maintenance required (periodic)",
                (0xFF): "Any"]
            ],
    (0x03): [
            label: "CO₂ Alarm",
            name: "CO2Alarm",
            events: [
                (0x00): "Idle",
                (0x01): "Alarm (location provided)",
                (0x02): "Alarm",
                (0x03): "Alarm test",
                (0x04): "Replacement required",
                (0x05): "Replacement required; End-of-life",
                (0x06): "Alarm silenced",
                (0x07): "Maintenance required (periodic)",
                (0xFF): "Any"]
            ],
    (0x04): [
            label: "Heat Alarm",
            name: "HeatAlarm",
            events: [
                (0x00): "Idle",
                (0x01): "Overheat alarm (location provided)",
                (0x02): "Overheat alarm",
                (0x03): "Rapid temperature rise (location provided)",
                (0x04): "Rapid temperature rise",
                (0x05): "Under heat (location provided)",
                (0x06): "Under heat",
                (0x07): "Alarm test",
                (0x08): "Replacement required; End-of-life",
                (0x09): "Alarm silenced",
                (0x0A): "Maintenance required (dust in device)",
                (0x0B): "Maintenance required (periodic)",
                (0x0C): "Rapid temperature fall (location provided)",
                (0x0D): "Rapid temperature fall",
                (0xFF): "Any"]
            ],
    (0x05): [
            label: "Water Alarm",
            name: "WaterAlarm",
            events: [
                (0x00): "Idle",
                (0x01): "Water leak alarm (location provided)",
                (0x02): "Water leak alarm",
                (0x03): "Water level dropped (location provided)",
                (0x04): "Water level dropped",
                (0x05): "Replace filter",
                (0x06): "Water flow alarm",
                (0x07): "Water pressure alarm",
                (0x08): "Water temperature alarm",
                (0x09): "Water level alarm",
                (0x0A): "Sump pump active",
                (0x0B): "Sump pump failure",
                (0xFF): "Any"]
            ],
    (0x06): [
            label: "Access Control",
            name: "AccessControl",
            events: [
                (0x00): "Idle",
                (0x01): "Manual lock operation",
                (0x02): "Manual unlock operation",
                (0x03): "RF lock operation",
                (0x04): "RF unlock operation",
                (0x05): "Keypad lock operation",
                (0x06): "Keypad unlock operation",
                (0x07): "Manual not fully locked operation",
                (0x08): "RF not fully locked operation",
                (0x09): "Auto lock locked operation",
                (0x0A): "Auto lock not fully locked operation",
                (0x0B): "Lock jammed",        
                (0x0C): "All user codes deleted",
                (0x0D): "Single user code deleted",
                (0x0E): "New user code added",
                (0x0F): "New user code not added (duplicate)",
                (0x10): "Keypad temporary disabled",
                (0x11): "Keypad busy",
                (0x12): "New program code entered: unique code for lock configuration",
                (0x13): "Manually enter user access code exceeds code limit",
                (0x14): "Unlock by RF with invalid user code",
                (0x15): "Locked by RF with invalid user code",
                (0x16): "Window/door is open",
                (0x17): "Window/door is closed",
                (0x18): "Window/door handle is open",
                (0x19): "Window/door handle is closed",        
                (0x20): "Messaging User Code entered via keypad",        
                (0x40): "Barrier performing initialization process",
                (0x41): "Barrier operation (open/close) force has been exceeded",
                (0x42): "Barrier motor has exceeded manufacturer's operational time limit",
                (0x43): "Barrier operation has exceeded physical mechanical limits",
                (0x44): "Barrier unable to perform requested operation due to UL requirements",
                (0x45): "Barrier unattended operation has been disabled per UL requirements",
                (0x46): "Barrier failed to perform requested operation, device malfunction",
                (0x47): "Barrier vacation mode",
                (0x48): "Barrier safety beam obstacle",
                (0x49): "Barrier sensor not detected / supervisory error",
                (0x4A): "Barrier sensor low battery warning",
                (0x4B): "Barrier detected short in wall station",
                (0x4C): "Barrier associated with non-Z-Wave remote control",        
                (0xFF): "Any"]
            ],
    (0x07): [
            label: "Home Security",
            name: "HomeSecurity",
            events: [
                (0x00): "Idle",
                (0x01): "Intrusion (location provided)",
                (0x02): "Intrusion",
                (0x03): "Tampering; product cover removed",
                (0x04): "Tampering; invalid code",
                (0x05): "Glass breakage (location provided)",
                (0x06): "Glass breakage",
                (0x07): "Motion detection (location provided)",
                (0x08): "Motion detection",
                (0x09): "Tampering; product removed",
                (0x0A): "Impact detected",
                (0x0B): "Magnetic field interference detected",        
                (0x0C): "RF jamming detected",
                (0xFF): "Any"]
            ],
    (0x08): [
            label: "Power Management",
            name: "PowerManagement",
            events: [
                (0x00): "Idle",
                (0x01): "Power has been applied",
                (0x02): "AC mains disconnected",
                (0x03): "AC mains re-connected",
                (0x04): "Surge detected",
                (0x05): "Voltage drop/drift",
                (0x06): "Over-current detected",
                (0x07): "Over-voltage detected",
                (0x08): "Over-load detected",
                (0x09): "Load error",
                (0x0A): "Replace battery soon",
                (0x0B): "Replace battery now",
                (0x0C): "Battery is charging",
                (0x0D): "Battery is fully charged",
                (0x0E): "Charge battery soon",
                (0x0F): "Charge battery now",
                (0x10): "Back-up battery is low",
                (0x11): "Battery fluid is low",
                (0x12): "Back-up battery disconnected",
                (0xFF): "Any"]
            ],
    (0x09): [
            label: "System",
            name: "System",
            events: [
                (0x00): "Idle",
                (0x01): "System hardware failure",
                (0x02): "System software failure",
                (0x03): "System hardware failure (manufacturer proprietary failure code provided)",
                (0x04): "System software failure (manufacturer proprietary failure code provided)",
                (0x05): "Heartbeat",
                (0x06): "Tampering, product cover removed",
                (0x07): "Emergency shutoff",
                (0x09): "Digital input high state",
                (0x0A): "Digital input low state",
                (0x0B): "Digital input open",
                (0xFF): "Any"]
            ],
    (0x0A): [
            label: "Emergency Alarm",
            name: "EmergencyAlarm",
            events: [
                (0x00): "Idle",
                (0x01): "Contact police",
                (0x02): "Contact fire service",
                (0x03): "Contact medical service",
                (0x04): "Panic alert",
                (0xFF): "Any"]
            ],
    (0x0B): [
            label: "Clock",
            name: "Clock",
            events: [
                (0x00): "Idle",
                (0x01): "Wake up alert",
                (0x02): "Timer ended",
                (0x03): "Time remaining",
                (0xFF): "Any"]
            ],
    (0x0C): [
            label: "Appliance",
            name: "Appliance",
            events: [
                (0x00): "Idle",
                (0x01): "Program started",
                (0x02): "Program in progress",
                (0x03): "Program completed",
                (0x04): "Replace main filter",
                (0x05): "Failure to set target temperature",
                (0x06): "Supplying water",
                (0x07): "Water supply failure",
                (0x08): "Boiling",
                (0x09): "Boiling failure",
                (0x0A): "Washing",
                (0x0B): "Washing failure",        
                (0x0C): "Rinsing",
                (0x0D): "Rinsing failure",
                (0x0E): "Draining",
                (0x0F): "Draining failure",
                (0x10): "Spinning",
                (0x11): "Spinning failure",
                (0x12): "Drying",
                (0x13): "Drying failure",
                (0x14): "Fan failure",
                (0x15): "Compressor failure",
                (0xFF): "Any"]
            ],
    (0x0D): [
            label: "Home Health",
            name: "HomeHealth",
            events: [
                (0x00): "Idle",
                (0x01): "Leaving bed",
                (0x02): "Sitting on bed",
                (0x03): "Lying on bed",
                (0x04): "Posture changed",
                (0x05): "Sitting on bed edge",
                (0x06): "Volatile Organic Compound level",
                (0x07): "Sleep apnea detected",
                (0x08): "Sleep stage 0 detected(Dreaming/REM)",
                (0x09): "Sleep stage 1 detected(Light sleep, non-REM 1)",
                (0x0A): "Sleep stage 2 detected(Medium sleep, non-REM 2)",
                (0x0B): "Sleep stage 3 detected(Deep sleep, non-REM 3)",        
                (0x0C): "Fall detected ",
                (0xFF): "Any"]
            ],
    (0x0E): [
            label: "Siren",
            name: "Siren",
            events: [
                (0x00): "Idle",
                (0x01): "Siren active",            
                (0xFF): "Any"]
            ],
    (0x0F): [
            label: "Water Valve",
            name: "WaterValve",
            events: [
                (0x00): "Idle",
                (0x01): "Valve operation",
                (0x02): "Master valve operation",
                (0x03): "Valve short circuit",
                (0x04): "Master valve short circuit",
                (0x05): "Valve current alarm",
                (0x06): "Master valve current alarm",
                (0xFF): "Any"]
            ],
    (0x10): [
            label: "Weather Alarm",
            name: "WeatherAlarm",
            events: [
                (0x00): "Idle",
                (0x01): "Rain alarm",
                (0x02): "Moisture alarm",
                (0x03): "Freeze alarm",
                (0xFF): "Any"]
            ],
    (0x11): [
            label: "Irrigation",
            name: "Irrigation",
            events: [
                (0x00): "Idle",
                (0x01): "Schedule started",
                (0x02): "Schedule finished",
                (0x03): "Valve table run started",
                (0x04): "Valve table run finished",
                (0x05): "Device is not configured",
                (0xFF): "Any"]
            ],
    (0x12): [
            label: "Gas Alarm",
            name: "GasAlarm",
            events: [
                (0x00): "Idle",
                (0x01): "Combustible gas detected (location provided)",
                (0x02): "Combustible gas detected",
                (0x03): "Toxic gas detected (location provided)",
                (0x04): "Toxic gas detected",
                (0x05): "Gas alarm test",
                (0x06): "Replacement required",
                (0xFF): "Any"]
            ],
    (0x13): [
            label: "Pest Control",
            name: "PestControl",
            events: [
                (0x00): "Idle",
                (0x01): "Trap armed (location provided)",
                (0x02): "Trap armed",
                (0x03): "Trap re-arm required (location provided)",
                (0x04): "Trap re-arm required",
                (0x05): "Pest detected (location provided)",
                (0x06): "Pest detected",
                (0x07): "Pest exterminated (location provided)",
                (0x08): "Pest exterminated",
                (0xFF): "Any"]
            ],
    (0x14): [
            label: "Light Sensor",
            name: "LightSensor",
            events: [
                (0x00): "Idle",
                (0x01): "Light detected",
                (0x02): "Light color transition detected",        
                (0xFF): "Any"]
            ],
    (0x15): [
            label: "Water Quality Monitoring",
            name: "WaterQualityMonitoring",
            events: [
                (0x00): "Idle",
                (0x01): "Chlorine alarm",
                (0x02): "Acidity (pH) alarm",
                (0x03): "Water Oxidation alarm",
                (0x04): "Chlorine empty",
                (0x05): "Acidity (pH) empty",
                (0x06): "Waterflow measuring station shortage detected",
                (0x07): "Waterflow clear water shortage detected",
                (0x08): "Disinfection system error detected",
                (0x09): "Filter cleaning ongoing",
                (0x0A): "Heating operation ongoing",
                (0x0B): "Filter pump operation ongoing",        
                (0x0C): "Freshwater operation ongoing",
                (0x0D): "Dry protection operation active",
                (0x0E): "Water tank is empty",
                (0x0F): "Water tank level is unknown",
                (0x10): "Water tank is full",
                (0x11): "Collective disorder",
                (0xFF): "Any"]
            ],
    (0x16): [
            label: "Home Monitoring",
            name: "HomeMonitoring",
            events: [
                (0x00): "Idle",
                (0x01): "Home occupied (location provided)",
                (0x02): "Home occupied",        
                (0xFF): "Any"]
            ]
]

static def getNotificationLabelMap() {
    return notificationTypeEvents.collectEntries({ key, type -> 
        [(key): type.label] 
    })
}

static def getNotificationTypeByLabel(def type) {
    return notificationTypeEvents.find({ it.value.label == type })?.key
}

static def getNotificationTypeByName(def type) {
    return notificationTypeEvents.find({ it.value.name == type })?.key
}

static def getNotificationEvent(def type, def event) {
    return notificationTypeEvents[type]?.events.find({ it.value == event })?.key
}

private void zwaveNotificationEvent(String version, def cmd, Short ep = 0) {
    logInfo("(${ep}) Notification ${version} report: ${notificationTypeEvents[cmd.notificationType as Integer].label}/${notificationTypeEvents[cmd.notificationType as Integer].events[cmd.event as Integer]}")
}
