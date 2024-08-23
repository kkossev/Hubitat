/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ElseBlockBraces, IfStatementBraces, Instanceof, LineLength, MethodCount, MethodParameterTypeRequired, MethodReturnTypeRequired, NoDef, ParameterReassignment, PublicMethodsBeforeNonPublicMethods, SpaceAroundOperator, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessarySetter, UnusedPrivateMethod, UnusedVariable, VariableName, VariableTypeRequired */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'RGB Library', name: 'rgbLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/rgbLib.groovy', documentationLink: '',
    version: '3.2.0'
)
/*
 *  Zigbee Button Dimmer -Library
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
 *  Credits: Ivar Holand for 'IKEA Tradfri RGBW Light HE v2' driver code
 *
 * ver. 1.0.0  2023-11-06 kkossev  - added rgbLib; musicMode;
 * ver. 1.0.1  2024-04-01 kkossev  - Groovy linting (all disabled)
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment
 *
 *                                   TODO:
*/

def rgbLibVersion()   { '3.2.0' }
def rgbLibStamp() { '2024/05/21 10:06 PM' }

/* groovylint-disable-next-line UnusedImport */
import hubitat.helper.ColorUtils

metadata {
    capability 'Actuator'
    capability 'Color Control'
    capability 'ColorMode'
    //capability 'Refresh'  // already defined in commonLib
    capability 'Switch'
    capability 'Light'

    preferences {
    }
}

//
// called from customUpdated() in the driver *Aqara_LED_Strip_T1.groovy*
void updatedRGB() {
    logDebug 'updatedBulb()...'
}

def colorControlRefresh() {
    def commands = []
    commands += zigbee.readAttribute(0x0300, 0x03, [:], 200) // currentColorX
    commands += zigbee.readAttribute(0x0300, 0x04, [:], 201) // currentColorY
    commands
}

def colorControlConfig(min, max, step) {
    def commands = []
    commands += zigbee.configureReporting(0x0300, 0x03, DataType.UINT16, min, max, step) // currentColorX
    commands += zigbee.configureReporting(0x0300, 0x04, DataType.UINT16, min, max, step) // currentColorY
    commands
}

// called from customRefresh() in the driver *Aqara_LED_Strip_T1.groovy*
List<String> refreshRGB() {
    List<String> cmds = []
    state.colorChanged = false
    state.colorXReported = false
    state.colorYReported = false
    state.cmds = []
    cmds =  zigbee.onOffRefresh(200) + zigbee.levelRefresh(201) + colorControlRefresh()
    cmds += zigbee.readAttribute(0x0300, [0x4001, 0x400a, 0x400b, 0x400c, 0x000f], [:], 204)    // colormode and color/capabilities
    cmds += zigbee.readAttribute(0x0008, [0x000f, 0x0010, 0x0011], [:], 204)                    // config/bri/execute_if_off
    cmds += zigbee.readAttribute(0xFCC0, [0x0515, 0x0516, 0x0517], [mfgCode:0x115F], 204)       // config/bri/min & max * startup
    cmds += zigbee.readAttribute(0xFCC0, [0x051B, 0x051c], [mfgCode:0x115F], 204)               // pixel count & musicMode
    if (cmds == []) { cmds = ['delay 299'] }
    logDebug "refreshRGB: ${cmds} "
    return cmds
}

// called from customConfigureDevice() in the driver *Aqara_LED_Strip_T1.groovy*
List<String> configureRGB() {
    List<String> cmds = []
    logDebug "configureRGB() : ${cmds}"
    cmds = refreshBulb() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig() + colorControlConfig(0, 300, 1)
    if (cmds == []) { cmds = ['delay 299'] }    // no ,
    return cmds
}

def initializeRGB() {
    List<String> cmds = []
    logDebug "initializeRGB() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299',] }
    return cmds
}

// called from customInitializeVars in the driver *Aqara_LED_Strip_T1.groovy*
void initVarsRGB(boolean fullInit=false) {
    state.colorChanged = false
    state.colorXReported = false
    state.colorYReported = false
    state.colorX = 0.9999
    state.colorY = 0.9999
    state.cmds = []
    logDebug "initVarsRGB(${fullInit})"
}

// called from customInitializeEvents in the driver *Aqara_LED_Strip_T1.groovy*
void initEventsBulb(boolean fullInit=false) {
    logDebug "initEventsBulb(${fullInit})"
    if ((device.currentState('saturation')?.value == null)) {
        sendEvent(name: 'saturation', value: 0)
    }
    if ((device.currentState('hue')?.value == null)) {
        sendEvent(name: 'hue', value: 0)
    }
    if ((device.currentState('level')?.value == null) || (device.currentState('level')?.value == 0)) {
        sendEvent(name: 'level', value: 100)
    }
}

def testT(par) {
    logWarn "testT(${par})"
}
