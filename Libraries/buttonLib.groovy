/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver',
    author: 'Krassimir Kossev',
    category: 'zigbee',
    description: 'Zigbee Button Library',
    name: 'buttonLib',
    namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy',
    version: '3.0.0',
    documentationLink: ''
)
/*
 *  Zigbee Button Library
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy
 *
 *                                   TODO:
*/

static String buttonLibVersion()   { '3.0.0' }
static String buttonLibStamp() { '2024/04/06 1:02 PM' }

//import groovy.json.*
//import groovy.transform.Field
//import hubitat.zigbee.clusters.iaszone.ZoneStatus
//import hubitat.zigbee.zcl.DataType
//import java.util.concurrent.ConcurrentHashMap

//import groovy.transform.CompileStatic

metadata {
    // no capabilities
    // no attributes
    // no commands
    preferences {
        // no prefrences
    }
}

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) {
    if (buttonState != 'unknown' && buttonNumber != 0) {
        String descriptionText = "button $buttonNumber was $buttonState"
        if (isDigital) { descriptionText += ' [digital]' }
        Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical']
        logInfo "$descriptionText"
        sendEvent(event)
    }
    else {
        logWarn "sendButtonEvent: UNHANDLED event for button ${buttonNumber}, buttonState=${buttonState}"
    }
}

void push() {                // Momentary capability
    logDebug 'push momentary'
    if (this.respondsTo('customPush')) { customPush(); return }
    logWarn "push() not implemented for ${(DEVICE_TYPE)}"
}

void push(BigDecimal buttonNumber) {    //pushableButton capability
    logDebug "push button $buttonNumber"
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return }
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true)
}

void doubleTap(BigDecimal buttonNumber) {
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true)
}

void hold(BigDecimal buttonNumber) {
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true)
}

void release(BigDecimal buttonNumber) {
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true)
}

void sendNumberOfButtonsEvent(int numberOfButtons) {
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital')
}

void sendSupportedButtonValuesEvent(supportedValues) {
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital')
}

