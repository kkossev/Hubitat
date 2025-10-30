library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Version helpers (based on https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/basicZWaveTool.groovy)",
    name: "version1",
    namespace: "drozovyk"   
)

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
    logInfo("Version Report V1 - ProtocolVersion: ${protocolVersion}")
    device.updateDataValue("protocolVersion", "${protocolVersion}")
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
    Double firmware0Version = cmd.firmware0Version + (cmd.firmware0SubVersion / 100)
    Double protocolVersion = cmd.zWaveProtocolVersion + (cmd.zWaveProtocolSubVersion / 100)
    logInfo("Version Report V2 - FirmwareVersion: ${firmware0Version}, ProtocolVersion: ${protocolVersion}, HardwareVersion: ${cmd.hardwareVersion}")
    device.updateDataValue("firmwareVersion", "${firmware0Version}")
    device.updateDataValue("protocolVersion", "${protocolVersion}")
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
    if (cmd.firmwareTargets > 0) {
        cmd.targetVersions.each { target ->
            Double targetVersion = target.version + (target.subVersion / 100)
            device.updateDataValue("firmware${target.target}Version", "${targetVersion}")
        }
    }
}

private String getVersionReportCommand(){
	return encapsulate(zwave.versionV2.versionGet())		
}

private void genericVersionEvent(String fwVer, String prVer, String hwVer) {
    logInfo("Version Report Generic - FirmwareVersion: ${fwVer}, ProtocolVersion: ${prVer}, HardwareVersion: ${hwVer}")
    device.updateDataValue("firmwareVersion", fwVer)
    device.updateDataValue("protocolVersion", prVer)
    device.updateDataValue("hardwareVersion", hwVer)
}
