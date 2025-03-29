/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Motion Library', name: 'motionLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/motionLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-motionLib',
    version: '3.2.1'
)
/*  Zigbee Motion Library
 *
 *  Licensed Virtual the Apache License, Version 2.0
 *
 * ver. 3.2.0  2024-07-06 kkossev  - added motionLib.groovy; added [digital] [physical] to the descriptionText
 * ver. 3.2.1  2025-03-24 kkossev  - (dev.branch) documentation
 *
 *                                   TODO:
*/

static String motionLibVersion()   { '3.2.1' }
static String motionLibStamp() { '2025/03/06 12:52 PM' }

metadata {
    capability 'MotionSensor'
    // no custom attributes
    command 'setMotion', [[name: 'setMotion', type: 'ENUM', constraints: ['No selection', 'active', 'inactive'], description: 'Force motion active/inactive (for tests)']]
    preferences {
        if (device) {
            if (('motionReset' in DEVICE?.preferences) && (DEVICE?.preferences.motionReset == true)) {
                input(name: 'motionReset', type: 'bool', title: '<b>Reset Motion to Inactive</b>', description: 'Software Reset Motion to Inactive after timeout. Recommended value is <b>false</b>', defaultValue: false)
                if (settings?.motionReset?.value == true) {
                    input('motionResetTimer', 'number', title: '<b>Motion Reset Timer</b>', description: 'After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 60 seconds', range: '0..7200', defaultValue: 60)
                }
            }
            if (advancedOptions == true) {
                if ('invertMotion' in DEVICE?.preferences) {
                    input(name: 'invertMotion', type: 'bool', title: '<b>Invert Motion Active/Not Active</b>', description: 'Some Tuya motion sensors may report the motion active/inactive inverted...', defaultValue: false)
                }
            }
        }
    }
}

public void handleMotion(final boolean motionActive, final boolean isDigital=false) {
    boolean motionActiveCopy = motionActive

    if (settings.invertMotion == true) {    // patch!! fix it!
        motionActiveCopy = !motionActiveCopy
    }

    //log.trace "handleMotion: motionActive=${motionActiveCopy}, isDigital=${isDigital}"
    if (motionActiveCopy) {
        int timeout = settings?.motionResetTimer ?: 0
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code
        if (settings?.motionReset == true && timeout != 0) {
            runIn(timeout, 'resetToMotionInactive', [overwrite: true])
        }
        if (device.currentState('motion')?.value != 'active') {
            state.motionStarted = unix2formattedDate(now())
        }
    }
    else {
        if (device.currentState('motion')?.value == 'inactive') {
            logDebug "ignored motion inactive event after ${getSecondsInactive()}s"
            return      // do not process a second motion inactive event!
        }
    }
    sendMotionEvent(motionActiveCopy, isDigital)
}

public void sendMotionEvent(final boolean motionActive, boolean isDigital=false) {
    String descriptionText = 'Detected motion'
    if (motionActive) {
        descriptionText = device.currentValue('motion') == 'active' ? "Motion is active ${getSecondsInactive()}s" : 'Detected motion'
    }
    else {
        descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s"
    }
    if (isDigital) { descriptionText += ' [digital]' }
    logInfo "${descriptionText}"
    sendEvent(
            name            : 'motion',
            value            : motionActive ? 'active' : 'inactive',
            type            : isDigital == true ? 'digital' : 'physical',
            descriptionText : descriptionText
    )
    //runIn(1, formatAttrib, [overwrite: true])
}

public void resetToMotionInactive() {
    if (device.currentState('motion')?.value == 'active') {
        String descText = "Motion reset to inactive after ${getSecondsInactive()}s (software timeout)"
        sendEvent(
            name : 'motion',
            value : 'inactive',
            isStateChange : true,
            type:  'digital',
            descriptionText : descText
        )
        logInfo "${descText}"
    }
    else {
        logDebug "ignored resetToMotionInactive (software timeout) after ${getSecondsInactive()}s"
    }
}

public void setMotion(String mode) {
    if (mode == 'active') {
        handleMotion(motionActive = true, isDigital = true)
    } else if (mode == 'inactive') {
        handleMotion(motionActive = false, isDigital = true)
    } else {
        if (settings?.txtEnable) {
            log.warn "${device.displayName} please select motion action"
        }
    }
}

public int getSecondsInactive() {
    Long unixTime = 0
    try { unixTime = formattedDate2unix(state.motionStarted) } catch (Exception e) { logWarn "getSecondsInactive: ${e}" }
    if (unixTime) { return Math.round((now() - unixTime) / 1000) as int }
    return settings?.motionResetTimer ?: 0
}

public List<String> refreshAllMotion() {
    logDebug 'refreshAllMotion()'
    List<String> cmds = []
    return cmds
}

public void motionInitializeVars( boolean fullInit = false ) {
    logDebug "motionInitializeVars()... fullInit = ${fullInit}"
    if (device.hasCapability('MotionSensor')) {
        if (fullInit == true || settings.motionReset == null) { device.updateSetting('motionReset', false) }
        if (fullInit == true || settings.invertMotion == null) { device.updateSetting('invertMotion', false) }
        if (fullInit == true || settings.motionResetTimer == null) { device.updateSetting('motionResetTimer', 60) }
    }
}
