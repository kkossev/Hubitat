library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Protection helpers",
    name: "protection1",
    namespace: "drozovyk"   
)

@Field static List<String> protectionOptions =  ["inactive", "active"]

void zwaveEvent(hubitat.zwave.commands.protectionv1.ProtectionReport cmd, Short ep = 0) {
    /*
     Short protectionState
     static Short PROTECTION_STATE_NO_OPERATION_POSSIBLE = 2
     static Short PROTECTION_STATE_PROTECTION_BY_SEQUENCE = 1
     static Short PROTECTION_STATE_UNPROTECTED = 0
    */
    
    logInfo "(${ep}) Protection V1 report: ${cmd}"
    
    parse([
        [name: "protectionLocal", value: protectionOptions[(cmd.protectionState > 0) ? 1 : 0]]
    ]) 
}

void zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionReport cmd, Short ep = 0) {
    logInfo "(${ep}) Protection V2 report: ${cmd}"
    
    parse([
        [name: "protectionLocal", value: protectionOptions[(cmd.localProtectionState > 0) ? 1 : 0]],
        [name: "protectionRf", value: protectionOptions[(cmd.rfProtectionState > 0) ? 1 : 0]]
    ])    
}

private String getProtectionReportV1String() {
    return encapsulate(zwave.protectionV1.protectionGet())
}

private String getProtectionReportV2String() {
    return encapsulate(zwave.protectionV2.protectionGet())
}

private String setProtectionV1String(boolean lock) {
    return encapsulate(zwave.protectionV1.protectionSet(protectionState: lock ? 2 : 0))
}

private String setProtectionV2String(boolean lock) {
    return encapsulate(zwave.protectionV2.protectionSet(localProtectionState: lock ? 2 : 0, rfProtectionState: 0))
}
