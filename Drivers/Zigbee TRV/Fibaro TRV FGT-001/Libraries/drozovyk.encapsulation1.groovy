library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Encapsulation helpers",
    name: "encapsulation1",
    namespace: "drozovyk"   
)

private String encapsulate(command, endpoint = 0) {
    def encapsulatedCommand = command
        
    // Multi-channel encapsulation
    if(endpoint > 0) {
        encapsulatedCommand = zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint: 0, bitAddress: 0, res01: 0, destinationEndPoint: endpoint).encapsulate(encapsulatedCommand)
    }
    
    // And the last one is security encapsulation
    if (getDataValue("zwaveSecurePairingComplete") == "true" && getDataValue("S2")?.toInteger() == null) {
		encapsulatedCommand = zwave.securityV1.securityMessageEncapsulation().encapsulate(encapsulatedCommand).format()
    }
    else {
        // if((S2 & 0x04) != 0) {} //S2_ACCESS_CONTROL
        // if((S2 & 0x02) != 0) {} //S2_AUTHENTICATED
        // if((S2 & 0x01) != 0) {} //S2_UNAUTHENTICATED
        // if((S2 & 0x80) != 0) {} //S0 on C7
    	encapsulatedCommand = zwaveSecureEncap(encapsulatedCommand)
    }
    
    return encapsulatedCommand
}

private String encapsulateString(String command, endpoint = 0) {
    def encapsulatedCommand = command
    payload.each {
        encapsulatedCommand += it
    }    
    
    // Multi-channel encapsulation
    if(endpoint > 0) {
        encapsulatedCommand = zwave.multiChannelV4.multiChannelCmdEncap(sourceEndPoint: 0, bitAddress: 0, res01: 0, destinationEndPoint: endpoint).encapsulate(encapsulatedCommand)
    }
    
    // And the last one is security encapsulation
    if (getDataValue("zwaveSecurePairingComplete") == "true" && getDataValue("S2")?.toInteger() == null) {
		encapsulatedCommand = zwave.securityV1.securityMessageEncapsulation().encapsulate(encapsulatedCommand).format()
    }
    else {
        // if((S2 & 0x04) != 0) {} //S2_ACCESS_CONTROL
        // if((S2 & 0x02) != 0) {} //S2_AUTHENTICATED
        // if((S2 & 0x01) != 0) {} //S2_UNAUTHENTICATED
        // if((S2 & 0x80) != 0) {} //S0 on C7
    	encapsulatedCommand = zwaveSecureEncap(encapsulatedCommand)
    }
    
    return encapsulatedCommand
}

// Application status
void zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationBusy cmd) {
	def msg = ""
    if(cmd.status == 0) {        
        msg = "try again later"
    }
    else if(cmd.status == 1) {
        msg = "try again in ${cmd.waitTime} seconds"
    }
    else if(cmd.status == 2) {
        msg = "request queued"
    }
    else {
        msg = "sorry"
    }
    
    logWarn(msg)
}

// Multi channel
void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap command) {
    def encapsulatedCommand = command.encapsulatedCommand()    
    if (encapsulatedCommand) {
        logInfo("Multi-channel V3 encapsulated report: ${encapsulatedCommand}")        
        zwaveEvent(encapsulatedCommand, command.sourceEndPoint)
    } else {
        logWarn("Multi-channel V3: Unable to extract multi-channel command from ${command}")
    }
}

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap command) {
    def encapsulatedCommand = command.encapsulatedCommand()    
    if (encapsulatedCommand) {
        logInfo("Multi-channel V4 encapsulated report: ${encapsulatedCommand}")        
        zwaveEvent(encapsulatedCommand, command.sourceEndPoint)
    } else {
        logWarn("Multi-channel V4: Unable to extract multi-channel command from ${command}")
    }
}

// Security
void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation command) {
    hubitat.zwave.Command encapsulatedCommand = command.encapsulatedCommand()
    if (encapsulatedCommand) {
        logInfo("Security V1 encapsulated report: ${encapsulatedCommand}")        
		zwaveEvent(encapsulatedCommand)
    } else {
        logWarn("Security V1: Unable to extract encapsulated command from ${command}")
    }
}

// Supervision
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd) {
    logInfo("Unexpected supervision report ${cmd}")
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Short ep = 0) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        logInfo("Supervision V1 encapsulated report: ${encapsulatedCommand}")        
        zwaveEvent(encapsulatedCommand, ep)
    }
    
    sendHubCommand(new hubitat.device.HubAction(encapsulate(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0), ep), hubitat.device.Protocol.ZWAVE))    
}
