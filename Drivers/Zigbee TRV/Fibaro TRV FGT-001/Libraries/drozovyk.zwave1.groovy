library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Common helpers",
    name: "zwave1",
    namespace: "drozovyk"   
)

// Generic inbound data to Z-Wave responsibility chain
void parse(String description) {
    logDebug("Z-Wave packet: ${description}")        
    
    // Device may provide version restriction list (optionally)
    if(commandClassVersions == null) {
        commandClassVersions = [:]
    }
    
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
        if (cmd instanceof hubitat.zwave.commands.schedulev1.ScheduleSupportedReport) {
            def props = getProperties(description)
            cmd.numberOfSupportedScheduleId = props.payload[0]
            cmd.supportEnabledisable = (props.payload[1] & 0x80) > 0
            cmd.fallbackSupport = (props.payload[1] & 0x40) > 0
            cmd.startTimeSupport = props.payload[1] & 0x0F // MSB->LSB: "weekdays", "calendar time", "start hour and minute", "start now"
            cmd.numberOfSupportedCc = props.payload[2]
            cmd.overrideSupport = (props.payload[3 + 2 * cmd.numberOfSupportedCc] & 0x80) > 0
            cmd.supportedOverrideTypes = props.payload[3 + 2 * cmd.numberOfSupportedCc] & 0x03 // MSB->LSB: "run forever", "advance"
        }
        if(cmd instanceof hubitat.zwave.commands.schedulev1.CommandScheduleReport) {
            logDebug("Schedule V1 command report: ${cmd}")
        }
        if (cmd instanceof hubitat.zwave.commands.schedulev1.ScheduleStateReport) {
            def props = getProperties(description)
            // Command is missing proper fields
            // Requires a List<Short> instead of cmd.activeId3 !!
            cmd.numberOfSupportedScheduleId = props.payload[0]
            cmd.override = (props.payload[1] & 0x01) > 0
            cmd.reportsToFollow = (props.payload[1] & 0xFE) >> 1
            
            cmd.activeId1 = (props.payload[2] & 0x0F)
            cmd.activeId2 = (props.payload[2] & 0xF0) >> 4
            if(null != props.payload[3]) {
                cmd.activeId3 = (props.payload[3] & 0x0F)
            }
            //cmd.activeId4 = (props.payload[3] & 0xF0) >> 4
            //cmd.activeId5 = (props.payload[4] & 0x0F)
            //cmd.activeId6 = (props.payload[4] & 0xF0) >> 4
            //cmd.activeId7 = (props.payload[5] & 0x0F)
            if(null != props.payload[5]) {
                cmd.activeIdN = (props.payload[5] & 0xF0) >> 4
            }
            
            /*
                0x00 Not used The Schedule ID is not used. (not set/configured) or unsupported
                0x01 [DEPRECATED] Redirect to 0x00
                0x02 Not Active The Schedule ID is used, enabled and currently not active.
                0x03 Active The Schedule ID is used, enabled and currently active.
                0x04 Disabled The Schedule ID is used and disabled.
                0x05 Override + Active The Schedule ID is used, enabled and should currently be active but it is suspended by the Override Schedule
                0x06 [DEPRECATED] Redirect to 0x02
                0x07 [DEPRECATED] Redirect to 0x04
            */
        }

        logDebug("Report: ${cmd}")

        zwaveEvent(cmd)
    }
    else {
        def props = getProperties(description)

        // hubitat.zwave.commands.schedulev1.CommandScheduleReport
        // Somehow it is not recognized
        if(props.command[0] == 0x53 && props.command[1] == 0x05) {
            cmd = new hubitat.zwave.commands.schedulev1.CommandScheduleReport()
            cmd.userIdentifier = 0 // ?                
            cmd.scheduleId = props.payload[0]
            cmd.startYear = props.payload[2]
            cmd.activeId = (props.payload[3] >> 4) & 15
            cmd.startMonth = props.payload[3] & 15
            cmd.startDayOfMonth = props.payload[4] & 31
            cmd.res51 = (props.payload[5] & 128) > 0
            cmd.startWeekday = props.payload[5] & 127
            cmd.durationType = (props.payload[6] >> 5) & 7
            cmd.startHour = props.payload[6] & 31
            cmd.startMinute = props.payload[7] & 63
            cmd.durationByte = props.payload[8] * 256 + props.payload[9] // ?
            cmd.reportsToFollow = props.payload[10]
            cmd.numberOfCmdToFollow = props.payload[11]

            zwaveEvent(cmd)
        }
        else {
            logDebug("Unrecognized z-wave report: ${props}")
        }
    }
}
