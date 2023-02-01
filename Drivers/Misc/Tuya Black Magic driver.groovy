/**
 *  Tuya Black Magic test driver for Hubitat
 *
 *  https://community.hubitat.com/t/help-needed-zigbee-devices-that-do-not-pair-correctly-in-he/109510/20?u=kkossev
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
 * ver. 1.0.0 2023-02-01 kkossev  - inital version
 *
*/

metadata {
    definition (name: "Tuya Black Magic driver", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Misc/Tuya%20%Black%20Magic%20driver.groovy") {
        capability "Actuator"
        capability "Outlet"
        capability "Configuration"
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0006,0003,0004,0005,E001,0B04,0702", model:"TS011F", manufacturer:"_TZ3000_okaz9tjs"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0003,0004,0005,0006,0702,0B04,E000,E001,0000", outClusters:"0019,000A", model:"TS011F", manufacturer:"_TZ3000_cehuw1lw"
    }
}

def parse(String description) {
    def descMap = [:]
    def event = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)        
        event = zigbee.getEvent(description)
    }
    catch ( e ) {
        log.warn "parse: exception caught while parsing description: ${description}"
    }
    if (event) {
        sendEvent(event)
    }
    log.debug "${device.displayName} parse returned descMap: $descMap"
}

def on() {
    zigbee.on()
}

def off() {
    zigbee.off()
}

def configure()
{
    log.warn "running configure in 5 seconds..."
    pauseExecution(5000)
    return zigbee.readAttribute(0x0000, [0x0004, 0x0000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=150)
}
