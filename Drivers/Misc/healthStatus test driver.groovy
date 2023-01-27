/**
 *  healthStatus test driver for Hubitat
 *
 *  https://community.hubitat.com/t/devicepresent-capability/89774/18?u=kkossev
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
 * ver. 1.0.0 2023-01-27 kkossev  - inital version
 *
*/

metadata {
    definition (name: "healthStatus test driver", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Misc/healthStatus%20test%20driver.groovy") {
        capability "Sensor"

        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "offline", [[name: "Set healthStatus offline"]]
        command "online",  [[name: "Set healthStatus online" ]]
    }

    preferences {
    }
}

def parse(String description) {
    log.debug "${device.displayName} parse: description is $description"
}

def offline() {
    sendEvent(name: "healthStatus", value: "offline", type: "digital", isStateChange: true )
}

def online() {
    sendEvent(name: "healthStatus", value: "online", type: "digital", isStateChange: true )
}
