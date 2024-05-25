/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Energy Library', name: 'energyLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/energyLib.groovy', documentationLink: '',
    version: '3.0.0'
   
)
/*
 *  Zigbee Energy Library
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
 * ver. 3.2.0  2024-05-24 kkossev  - CommonLib 3.2.0 allignment
 *
 *                                   TODO:
*/

static String energyLibVersion()   { '3.2.0' }
static String energyLibStamp() { '2024/05/24 10:59 PM' }

//import groovy.json.*
//import groovy.transform.Field
//import hubitat.zigbee.clusters.iaszone.ZoneStatus
//import hubitat.zigbee.zcl.DataType
//import java.util.concurrent.ConcurrentHashMap

//import groovy.transform.CompileStatic

metadata {
    capability 'PowerMeter'
    capability 'EnergyMeter'
    capability 'VoltageMeasurement'
    capability 'CurrentMeter'
    // no attributes
    // no commands
    preferences {
        // no prefrences
    }
}

@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602
@Field static final int AC_FREQUENCY_ID = 0x0300
@Field static final int AC_POWER_DIVISOR_ID = 0x0605
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600
@Field static final int ACTIVE_POWER_ID = 0x050B
@Field static final int POWER_ON_OFF_ID = 0x0000
@Field static final int POWER_RESTORE_ID = 0x4003
@Field static final int RMS_CURRENT_ID = 0x0508
@Field static final int RMS_VOLTAGE_ID = 0x0505


void sendVoltageEvent(BigDecimal voltage, boolean isDigital=false) {
    Map map = [:]
    map.name = 'voltage'
    map.value = voltage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP)
    map.unit = 'V'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastVoltage = device.currentValue('voltage') ?: 0.0
    final BigDecimal  voltageThreshold = DEFAULT_DELTA
    if (Math.abs(voltage - lastVoltage) >= voltageThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastVoltage} is less than ${voltageThreshold} V)"
    }
}

void sendAmperageEvent(BigDecimal amperage, boolean isDigital=false) {
    Map map = [:]
    map.name = 'amperage'
    map.value = amperage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP)
    map.unit = 'A'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh  == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastAmperage = device.currentValue('amperage') ?: 0.0
    final BigDecimal amperageThreshold = DEFAULT_DELTA
    if (Math.abs(amperage - lastAmperage ) >= amperageThreshold || state.states.isRefresh  == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastAmperage} is less than ${amperageThreshold} mA)"
    }
}

void sendPowerEvent(BigDecimal power, boolean isDigital=false) {
    Map map = [:]
    map.name = 'power'
    map.value = power.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP)
    map.unit = 'W'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastPower = device.currentValue('power') ?: 0.0
    final BigDecimal powerThreshold = DEFAULT_DELTA
    if (power  > MAX_POWER_LIMIT) {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (exceeds maximum power cap ${MAX_POWER_LIMIT} W)"
        return
    }
    if (Math.abs(power - lastPower ) >= powerThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastPower} is less than ${powerThreshold} W)"
    }
}

void sendFrequencyEvent(BigDecimal frequency, boolean isDigital=false) {
    Map map = [:]
    map.name = 'frequency'
    map.value = frequency.setScale(1, BigDecimal.ROUND_HALF_UP)
    map.unit = 'Hz'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastFrequency = device.currentValue('frequency') ?: 0.0
    final BigDecimal frequencyThreshold = 0.1
    if (Math.abs(frequency - lastFrequency) >= frequencyThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${frequencyThreshold} Hz)"
    }
}

void sendPowerFactorEvent(BigDecimal pf, boolean isDigital=false) {
    Map map = [:]
    map.name = 'powerFactor'
    map.value = pf.setScale(2, BigDecimal.ROUND_HALF_UP)
    map.unit = '%'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastPF = device.currentValue('powerFactor') ?: 0.0
    final BigDecimal powerFactorThreshold = 0.01
    if (Math.abs(pf - lastPF) >= powerFactorThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${powerFactorThreshold} %)"
    }
}
