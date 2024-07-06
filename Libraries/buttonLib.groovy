/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Button Library', name: 'buttonLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy', documentationLink: '',
    version: '3.2.0'
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
 * ver. 3.2.0  2024-05-24 kkossev  - commonLib 3.2.0 allignment; added capability 'PushableButton' and 'Momentary'
 *
 *                                   TODO:
*/

static String buttonLibVersion()   { '3.2.0' }
static String buttonLibStamp() { '2024/05/24 12:48 PM' }

metadata {
    capability 'PushableButton'
    capability 'Momentary'
    // the other capabilities must be declared in the custom driver, if applicable for the particular device!
    // the custom driver must allso call sendNumberOfButtonsEvent() and sendSupportedButtonValuesEvent()!
    // capability 'DoubleTapableButton'
    // capability 'HoldableButton'
    // capability 'ReleasableButton'

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

/*
void push(BigDecimal buttonNumber) {    //pushableButton capability
    logDebug "push button $buttonNumber"
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return }
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true)
}
*/

void push(Object bn) {    //pushableButton capability
    Integer buttonNumber = bn.toInteger()
    logDebug "push button $buttonNumber"
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return }
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true)
}

void doubleTap(Object bn) {
    Integer buttonNumber = bn.toInteger()
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true)
}

void hold(Object bn) {
    Integer buttonNumber = bn.toInteger()
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true)
}

void release(Object bn) {
    Integer buttonNumber = bn.toInteger()
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true)
}

// must be called from the custom driver!
void sendNumberOfButtonsEvent(int numberOfButtons) {
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital')
}
// must be called from the custom driver!
void sendSupportedButtonValuesEvent(List<String> supportedValues) {
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital')
}

