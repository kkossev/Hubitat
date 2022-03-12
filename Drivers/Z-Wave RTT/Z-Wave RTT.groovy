/*
 *	Z-Wave RTT
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 * 
 * ver. 1.0.0 2022-03-12 kkossev  - Inital test version
 *
*/

import groovy.transform.Field
import java.text.SimpleDateFormat

metadata {
    definition (name: "Z-Wave RTT", namespace: "kkossev", author: "Krassimir Kossev") {

    capability "Actuator"
    capability "Polling" 
        
    attribute "RTT", "number"        
    }
}


void parse(String description) {
    hubitat.zwave.Command cmd = zwave.parse(description,[0x85:1,0x86:2])
    if (cmd) {
        zwaveEvent(cmd)
    }
}

//Z-Wave versionv2.VersionReport
void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
    def now = new Date().getTime()
    def timeRunning = now.toInteger() -  state.cmdSentTime.toInteger()
    log.debug "${device.displayName} RTT (ms) : ${timeRunning}"    
    sendEvent(name: "RTT", value: timeRunning, unit: "ms")    
}


void zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
    log.info "CommandClassReport- class:${ "0x${intToHexStr(cmd.requestedCommandClass)}" }, version:${cmd.commandClassVersion}"		
}	

String zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand()
    if (encapCmd) {
		return zwaveEvent(encapCmd)
    } else {
        log.warn "Unable to extract encapsulated cmd from ${cmd}"
    }
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    log.debug "skip: ${cmd}"
}


def getVersionReport(){
	return secureCmd(zwave.versionV1.versionGet())		
}


def poll() {
    
    def now = new Date().getTime()
    state.cmdSentTime = now

    getVersionReport()
}

String secure(String cmd){
    return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd){
    return zwaveSecureEncap(cmd)
}

void installed() {}

void configure() {}

void updated() {}

String secureCmd(cmd) {
    if (getDataValue("zwaveSecurePairingComplete") == "true" && getDataValue("S2") == null) {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
		return secure(cmd)
    }	
}
