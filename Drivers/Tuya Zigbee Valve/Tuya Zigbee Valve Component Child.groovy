/**
 *  Component child driver for the SONOFF SWV-ZF2 valve channels.
 *
 *  Licensed under the Apache License, Version 2.0.
 */

metadata {
    definition(
        name: 'Tuya Zigbee Valve Component Child',
        namespace: 'kkossev',
        author: 'Krassimir Kossev',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Valve/Tuya%20Zigbee%20Valve%20Component%20Child.groovy'
    ) {
        capability 'Actuator'
        capability 'Valve'
        capability 'Switch'
        capability 'Refresh'

        attribute 'manualIrrigationDuration', 'number'
        attribute 'manualIrrigationMode', 'enum', ['duration', 'capacity']
        attribute 'manualIrrigationAmountUnit', 'enum', ['US gallon', 'liter']
        attribute 'manualIrrigationAmount', 'number'
        attribute 'manualFailSafe', 'number'

        command 'setManualIrrigationDuration', [[name:'duration', type:'NUMBER', constraints:['1..719']]]
        command 'setManualIrrigationAmount', [[name:'amount', type:'NUMBER', constraints:['0..10000']]]
    }

    preferences {
        input(name: 'txtEnable', type: 'bool', title: '<b>Description text logging</b>', description: 'Display child command activity in Hubitat logs.', defaultValue: true)
        input(name: 'logEnable', type: 'bool', title: '<b>Debug logging</b>', description: 'Detailed child-driver diagnostics. Automatically disables after 24 hours.', defaultValue: true)
        input(name: 'manualAmountUnitPreference', type: 'enum', title: '<b>Manual irrigation amount unit</b>', description: 'Shared by both SWV-ZF2 valve children. Used by the simple manual-irrigation commands.', options: ['US gallon', 'liter'], defaultValue: 'liter', required: true)
        input(name: 'manualFailSafePreference', type: 'number', title: '<b>Manual irrigation fail-safe</b>', description: 'Shared by both SWV-ZF2 valve children. Safety timeout in minutes (0..719).', range: '0..719', defaultValue: 0, required: true)
    }
}

void installed() {
    log.info "${device.displayName} installed"
    sendEvent(name: 'valve', value: 'unknown')
    sendEvent(name: 'switch', value: 'unknown')
    sendEvent(name: 'manualIrrigationDuration', value: 'unknown')
    sendEvent(name: 'manualIrrigationMode', value: 'unknown')
    sendEvent(name: 'manualIrrigationAmountUnit', value: 'unknown')
    sendEvent(name: 'manualIrrigationAmount', value: 'unknown')
    sendEvent(name: 'manualFailSafe', value: 'unknown')
    state.manualAmountUnitPreference = getManualAmountUnitPreference()
    state.manualFailSafePreference = getManualFailSafePreference()
}

void updated() {
    logInfo "preferences updated; description logging=${settings?.txtEnable == true}, debug logging=${settings?.logEnable == true}"
    if (settings?.logEnable == true) {
        runIn(86400, 'logsOff', [overwrite:true])
        logDebug 'debug logging will be disabled automatically after 24 hours'
    } else {
        unschedule('logsOff')
    }
    String amountUnit = getManualAmountUnitPreference()
    BigDecimal failSafe = getManualFailSafePreference()
    String reportedUnit = device.currentValue('manualIrrigationAmountUnit') in ['US gallon', 'liter'] ? device.currentValue('manualIrrigationAmountUnit') : null
    BigDecimal reportedFailSafe = getReportedManualFailSafe()
    String previousUnit = state.manualAmountUnitPreference ?: reportedUnit ?: 'liter'
    BigDecimal previousFailSafe = state.manualFailSafePreference != null ? state.manualFailSafePreference as BigDecimal : (reportedFailSafe ?: 0)
    state.manualAmountUnitPreference = amountUnit
    state.manualFailSafePreference = failSafe
    if (amountUnit != previousUnit || failSafe != previousFailSafe) {
        logInfo "manual irrigation preferences changed; applying unit=${amountUnit}, fail-safe=${failSafe} min"
        parent?.componentApplyManualIrrigationPreferences(device, amountUnit, failSafe)
    }
}

void parse(List<Map> events) {
    String previousSwitch = device.currentValue('switch')
    events.each { Map event ->
        logDebug "event ${event}"
        sendEvent(event)
    }
    Map switchEvent = events.find { it.name == 'switch' }
    if (switchEvent != null && switchEvent.value != previousSwitch) {
        logInfo "valve is ${switchEvent.value}"
    }
    Map amountUnitEvent = events.find { it.name == 'manualIrrigationAmountUnit' }
    Map failSafeEvent = events.find { it.name == 'manualFailSafe' }
    if (amountUnitEvent != null) {
        state.manualAmountUnitPreference = amountUnitEvent.value
        device.updateSetting('manualAmountUnitPreference', [value:amountUnitEvent.value, type:'enum'])
    }
    if (failSafeEvent != null) {
        state.manualFailSafePreference = failSafeEvent.value as BigDecimal
        device.updateSetting('manualFailSafePreference', [value:failSafeEvent.value, type:'number'])
    }
}

void open() {
    logInfo 'requesting valve open'
    parent?.componentOpen(device)
}

void close() {
    logInfo 'requesting valve close'
    parent?.componentClose(device)
}

void on() {
    logDebug 'on() delegates to the parent component handler'
    parent?.componentOn(device)
}

void off() {
    logDebug 'off() delegates to the parent component handler'
    parent?.componentOff(device)
}

void refresh() {
    logDebug 'requesting parent device refresh'
    parent?.componentRefresh(device)
}

String getManualAmountUnitPreference() {
    return settings?.manualAmountUnitPreference ?: device.currentValue('manualIrrigationAmountUnit') ?: 'liter'
}

BigDecimal getManualFailSafePreference() {
    return (settings?.manualFailSafePreference ?: device.currentValue('manualFailSafe') ?: 0) as BigDecimal
}

BigDecimal getReportedManualFailSafe() {
    try {
        return device.currentValue('manualFailSafe') as BigDecimal
    } catch (ignored) {
        return null
    }
}

void setManualIrrigationDuration(BigDecimal duration) {
    String amountUnit = getManualAmountUnitPreference()
    BigDecimal failSafe = getManualFailSafePreference()
    logInfo "setting shared manual irrigation duration to ${duration} min (unit=${amountUnit}, fail-safe=${failSafe} min)"
    parent?.componentSetManualIrrigationDuration(device, duration, amountUnit, failSafe)
}

void setManualIrrigationAmount(BigDecimal amount) {
    String amountUnit = getManualAmountUnitPreference()
    BigDecimal failSafe = getManualFailSafePreference()
    logInfo "setting shared manual irrigation amount to ${amount} ${amountUnit} (fail-safe=${failSafe} min)"
    parent?.componentSetManualIrrigationAmount(device, amount, amountUnit, failSafe)
}

void logsOff() {
    log.warn "${device.displayName} debug logging disabled automatically"
    device.updateSetting('logEnable', [value:false, type:'bool'])
}

void logDebug(String message) {
    if (settings?.logEnable == true) { log.debug "${device.displayName} ${message}" }
}

void logInfo(String message) {
    if (settings?.txtEnable != false) { log.info "${device.displayName} ${message}" }
}
