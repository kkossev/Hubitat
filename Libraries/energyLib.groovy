/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Energy Library', name: 'energyLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/energyLib.groovy', documentationLink: '',
    version: '3.3.0'

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
 * ver. 3.3.0  2024-06-09 kkossev  - added energy, power, voltage, current events parsing
 *
 *                                   TODO: add energyRefresh()
*/

static String energyLibVersion()   { '3.3.0' }
static String energyLibStamp() { '2024/06/09 6:53 PM' }

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
@Field static final int CURRENT_SUMMATION_DELIVERED = 0x0000 // Energy

@Field static  int    DEFAULT_REPORTING_TIME = 30
@Field static  int    DEFAULT_PRECISION = 3           // 3 decimal places
@Field static  BigDecimal DEFAULT_DELTA = 0.001
@Field static  int    MAX_POWER_LIMIT = 999

void sendVoltageEvent(BigDecimal voltage, boolean isDigital=false) {
    Map map = [:]
    map.name = 'voltage'
    map.value = voltage.setScale((settings?.defaultPrecision ?: DEFAULT_PRECISION) as int, BigDecimal.ROUND_HALF_UP)
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
    map.value = amperage.setScale((settings?.defaultPrecision ?: DEFAULT_PRECISION) as int, BigDecimal.ROUND_HALF_UP)
    map.unit = 'A'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh  == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastAmperage = device.currentValue('amperage') ?: 0.00000001
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
    map.value = power.setScale((settings?.defaultPrecision ?: DEFAULT_PRECISION) as int, BigDecimal.ROUND_HALF_UP)
    map.unit = 'W'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastPower = device.currentValue('power') ?: 0.00000001
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
    final BigDecimal lastFrequency = device.currentValue('frequency') ?: 0.00000001
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
    final BigDecimal lastPF = device.currentValue('powerFactor') ?: 0.00000001
    final BigDecimal powerFactorThreshold = 0.01
    if (Math.abs(pf - lastPF) >= powerFactorThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${powerFactorThreshold} %)"
    }
}

void sendEnergyEvent(BigDecimal energy_total, boolean isDigital=false) {
    BigDecimal energy = energy_total
    Map map = [:]
    logDebug "energy_total=${energy_total}"
    map.name = 'energy'
    map.value = energy
    map.unit = 'kWh'
    map.type = isDigital == true ? 'digital' : 'physical'
    if (isDigital == true) { map.isStateChange = true  }
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefreshRequest == true) { map.descriptionText += ' (refresh)' }
    BigDecimal lastEnergy = device.currentValue('energy') ?: 0.00000001
    if (lastEnergy  != energy || state.states.isRefreshRequest == true || isDigital == true) {
        sendEvent(map)
        logInfo "${map.descriptionText}"
    }
    else {
        logDebug "${device.displayName} ${map.name} is ${map.value} ${map.unit} (no change)"
    }
}

// parse the electrical measurement cluster 0x0B04
boolean standardParseElectricalMeasureCluster(Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return true } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    int attributeInt = hexStrToUnsignedInt(descMap.attrId)
    logTrace "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
    switch (attributeInt) {
        case ACTIVE_POWER_ID:   // 0x050B
            BigDecimal power = new BigDecimal(value).divide(new BigDecimal(1/*0*/))
            sendPowerEvent(power)
            break
        case RMS_CURRENT_ID:    // 0x0508
            BigDecimal current = new BigDecimal(value).divide(new BigDecimal(1000))
            sendAmperageEvent(current)
            break
        case RMS_VOLTAGE_ID:    // 0x0505
            BigDecimal voltage = new BigDecimal(value).divide(new BigDecimal(10))
            sendVoltageEvent(voltage)
            break
        case AC_FREQUENCY_ID:   // 0x0300
            BigDecimal frequency = new BigDecimal(value).divide(new BigDecimal(10))
            sendFrequencyEvent(frequency)
            break
        case 0x0800:    // AC Alarms Mask
            logDebug "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} AC Alarms Mask value=${value}"
            break
        case 0x0802:    // AC Current Overload
            logDebug "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} AC Current Overload value=${value / 1000} (raw: ${value})"
            break
        case [AC_VOLTAGE_MULTIPLIER_ID, AC_VOLTAGE_DIVISOR_ID, AC_CURRENT_MULTIPLIER_ID, AC_CURRENT_DIVISOR_ID, AC_POWER_MULTIPLIER_ID, AC_POWER_DIVISOR_ID].contains(descMap.attrId):
            logDebug "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
            break
        default:
            logDebug "standardParseElectricalMeasureCluster: (0x0B04) <b>not parsed</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
            return false    // not parsed
    }
    return true // parsed and processed
}

// parse the metering cluster 0x0702
boolean standardParseMeteringCluster(Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return true } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    int attributeInt = hexStrToUnsignedInt(descMap.attrId)
    logTrace "standardParseMeteringCluster: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
    switch (attributeInt) {
        case CURRENT_SUMMATION_DELIVERED:   // 0x0000
            BigDecimal energyScaled = new BigDecimal(value).divide(new BigDecimal(10/*00*/))
            sendEnergyEvent(energyScaled)
            break
        default:
            logWarn "standardParseMeteringCluster: (0x0702) <b>not parsed</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
            return false    // not parsed
    }
    return true // parsed and processed
}

void energyInitializeVars( boolean fullInit = false ) {
    logDebug "energyInitializeVars()... fullInit = ${fullInit}"
    if (fullInit || settings?.defaultPrecision == null) { device.updateSetting('defaultPrecision', [value: DEFAULT_PRECISION, type: 'number']) }
}
