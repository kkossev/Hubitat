/* groovylint-disable DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods */
/**
 *  LinknLink eMotion Air-6D55 BT - Driver for Hubitat Elevation
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *     in compliance with the License. You may obtain a copy of the License at:
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *     on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *     for the specific language governing permissions and limitations under the License.
 *
 * Bluetooth (BTHome v2) driver for the LinknLink eMotion Air mmWave presence multi-sensor,
 * paired via the hub's built-in Bluetooth Integration app. Requires hub model C-8 Pro.
 *
 * The hub does the BLE scanning and BTHome decoding; this driver receives the already-decoded
 * Map in parse(Map) and turns it into Hubitat events. Derived from the probe findings in
 * 'Drivers/Misc/tests/Bluetooth Methods Dumper.groovy', which remains the diagnostic tool for
 * dumping the raw payload and its runtime types.
 *
 * This folder is named for the device, not the protocol: the eMotion Air can also run in Zigbee
 * mode, and a separate Zigbee driver for the same hardware is intended to live here alongside
 * this one. Keep anything BT-specific in this file rather than in shared helpers.
 *
 * BLE IS RECEIVE-ONLY. Hub-verified: hubitat.device.Protocol has no BLUETOOTH member, and no
 * bluetooth/ble helper object is exposed to drivers, so there is NO way to send anything to the
 * device - PIR sensitivity, motion timeout and illuminance thresholds cannot be configured from
 * Hubitat in BT mode. Use the LinknLink app, or the device's Zigbee/Matter mode, for that.
 *
 * ver. 1.0.0  2026-08-17 kkossev  - first version; decodes temperature, humidity, illuminance,
 *                                   battery, motion, button and light_level from BTHome v2.
 *
 *                                   TODO: resolve what light_level actually means (see notes below)
 *                                   TODO: optional PresenceSensor with a stale-data timeout
 */

static String version() { '1.0.0' }
static String timeStamp() { '2026/08/17 7:50 PM' }

import java.math.RoundingMode

metadata {
    definition(
        name: 'LinknLink eMotion Air-6D55 BT',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true) {
        capability 'Sensor'
        capability 'Initialize'
        capability 'MotionSensor'                //  motion       - ENUM ['active', 'inactive']
        capability 'IlluminanceMeasurement'      //  illuminance  - NUMBER, unit:lx
        capability 'TemperatureMeasurement'      //  temperature  - NUMBER, unit:°C or °F
        capability 'RelativeHumidityMeasurement' //  humidity     - NUMBER, unit:%
        capability 'Battery'                     //  battery      - NUMBER, unit:%
        capability 'PushableButton'              //  pushed       - NUMBER
        capability 'DoubleTapableButton'         //  doubleTapped - NUMBER
        capability 'HoldableButton'              //  held         - NUMBER

        attribute 'rssi', 'number'
        attribute 'advertisedName', 'string'
        // Deliberately NOT named 'lightLevel'. It does respond to light (reads 0 in the dark),
        // but it is NOT a fixed lux threshold: the same illuminance produced 2 in one window and
        // 1 later (23 lx and 30 lx each observed as both). Either the mapping is adaptive, the
        // thresholds are device-configurable, or illuminance/light_level are sampled at different
        // instants within one advertisement. Kept raw until a controlled dark/dim/bright sweep
        // settles it - naming it 'lightLevel' would assert a meaning the data does not support.
        attribute 'lightLevelRaw', 'number'
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>',
              description: 'Log each decoded sensor value.', defaultValue: true
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>',
              description: 'Logs unrecognised BTHome fields. Turn off in normal use.', defaultValue: false
        input name: 'reportRssi', type: 'bool', title: '<b>Report rssi as an attribute</b>',
              description: 'The device re-sends signal strength in every advertisement; off keeps the event log readable.',
              defaultValue: false
    }
}

/* ------------------------------------------------------------------------------------------------
 * Lifecycle
 * ------------------------------------------------------------------------------------------------ */

void installed() {
    logInfo "installed: driver ${version()}"
    initButtons()
}

void updated() {
    logInfo "updated: txtEnable=${settings.txtEnable}, logEnable=${settings.logEnable}, reportRssi=${settings.reportRssi}"
    initButtons()
}

// Called on hub startup because capability 'Initialize' is declared.
void initialize() {
    logInfo "initialize: driver ${version()}"
    initButtons()
}

// The eMotion Air has a single physical button.
private void initButtons() {
    if (device.currentValue('numberOfButtons') != 1) {
        sendEvent(name: 'numberOfButtons', value: 1)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Parsing
 *
 * Payload contract (hub-verified, platform 2.5.1.156):
 *
 *   data.address           String   'E0:4B:41:02:6D:55'
 *   data.advertised_name   String   often 'Unknown'
 *   data.model             String   'BTHome sensor'
 *   data.manufacturer      null
 *   data.binary_values     List of [device: String, value: Boolean]
 *   data.events            List of [device, name, properties, type]
 *   data.sensors           List of [device: String, unit: String|null, value: Number]
 *
 * Three traps this decoder works around deliberately:
 *   - Numeric types are MIXED: illuminance/temperature arrive as BigDecimal, while
 *     battery/humidity/light_level/signal_strength are Integer. Never assume one type.
 *   - binary_values carry a real Boolean, so a `value == 1` test would never match.
 *   - `data.class` is a Groovy MAP KEY LOOKUP, not the runtime type - it returns null.
 *     Use instanceof for type checks on the payload. (Also note the sandbox rejects
 *     .getClass(), Class.forName() and classLoader.loadClass() outright.)
 * ------------------------------------------------------------------------------------------------ */

void parse(Map data) {
    if (data == null) { return }
    logDebug "parse: ${data}"

    if (data.advertised_name && data.advertised_name != 'Unknown') {
        sendIfChanged('advertisedName', data.advertised_name, null, null)
    }

    parseSensors(data.sensors)
    parseBinaryValues(data.binary_values)
    parseEvents(data.events)
}

// Defensive: the BT integration delivers parse(Map). If a String ever arrives, say so
// rather than failing silently in a method the driver does not implement.
void parse(String description) {
    logWarn "parse(String) is not used by the Bluetooth integration - received: ${description}"
}

// Everything is routed by the 'device' key rather than list position, because nothing
// guarantees field ordering between advertisements.
private void parseSensors(List sensors) {
    (sensors ?: []).each { Object entry ->
        if (!(entry instanceof Map)) { return }
        Map s = (Map) entry
        String dev = s.device as String
        Object val = s.value
        if (dev == null || val == null) { return }

        switch (dev) {
            case 'battery':
                Integer b = toInt(val)
                sendIfChanged('battery', b, '%', "battery is ${b}%")
                break
            case 'illuminance':
                Integer lux = toInt(val)          // BigDecimal in practice
                sendIfChanged('illuminance', lux, 'lx', "illuminance is ${lux} lx")
                break
            case 'humidity':
                Integer rh = toInt(val)
                sendIfChanged('humidity', rh, '%', "humidity is ${rh}%")
                break
            case 'temperature':
                sendTemperature(val)
                break
            case 'light_level':
                Integer ll = toInt(val)
                sendIfChanged('lightLevelRaw', ll, null, "lightLevelRaw is ${ll} (meaning unverified)")
                break
            case 'signal_strength':
                if (settings.reportRssi) {
                    Integer r = toInt(val)
                    sendIfChanged('rssi', r, 'dBm', "rssi is ${r} dBm")
                }
                break
            default:
                logDebug "unhandled sensor '${dev}' = ${val} ${s.unit ?: ''} (${val?.class?.simpleName})"
        }
    }
}

private void parseBinaryValues(List binaryValues) {
    (binaryValues ?: []).each { Object entry ->
        if (!(entry instanceof Map)) { return }
        Map b = (Map) entry
        String dev = b.device as String
        Object val = b.value
        if (dev == null || val == null) { return }

        // Hub-verified as a real Boolean; 1/'true' accepted defensively in case a future
        // firmware or another BTHome device encodes it differently.
        boolean active = (val instanceof Boolean) ? ((Boolean) val).booleanValue()
                                                  : (val.toString() in ['1', 'true'])
        switch (dev) {
            case 'motion':
                String m = active ? 'active' : 'inactive'
                sendIfChanged('motion', m, null, "motion is ${m}")
                break
            default:
                logDebug "unhandled binary '${dev}' = ${val} (${val?.class?.simpleName})"
        }
    }
}

private void parseEvents(List events) {
    (events ?: []).each { Object entry ->
        if (!(entry instanceof Map)) { return }
        Map e = (Map) entry
        if ((e.device as String) != 'button') {
            logDebug "unhandled event ${e}"
            return
        }
        String type = (e.type ?: '') as String
        switch (type) {
            case 'press':
                sendButtonEvent('pushed', 1, 'button 1 was pushed')
                break
            case 'double_press':
                sendButtonEvent('doubleTapped', 1, 'button 1 was double-tapped')
                break
            case 'long_press':
            case 'hold_press':
                sendButtonEvent('held', 1, 'button 1 was held')
                break
            default:
                // triple_press has no matching Hubitat capability. Surface it rather than
                // quietly aliasing it onto a plain push, which would fire rules wrongly.
                logWarn "button event type '${type}' has no Hubitat mapping - raw event: ${e}"
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Commands
 *
 * Required by the button capabilities. The device cannot be actuated - there is no outbound BLE
 * path at all - so these only simulate an event, which is useful when testing rules.
 * ------------------------------------------------------------------------------------------------ */

void push(Object buttonNumber = 1) {
    sendButtonEvent('pushed', toInt(buttonNumber), "button ${toInt(buttonNumber)} was pushed (simulated)")
}

void doubleTap(Object buttonNumber = 1) {
    sendButtonEvent('doubleTapped', toInt(buttonNumber), "button ${toInt(buttonNumber)} was double-tapped (simulated)")
}

void hold(Object buttonNumber = 1) {
    sendButtonEvent('held', toInt(buttonNumber), "button ${toInt(buttonNumber)} was held (simulated)")
}

/* ------------------------------------------------------------------------------------------------
 * Event helpers
 * ------------------------------------------------------------------------------------------------ */

// The device reports Celsius; honour the hub's temperature scale.
private void sendTemperature(Object rawCelsius) {
    BigDecimal c = toBigDecimal(rawCelsius)
    boolean fahrenheit = (location?.temperatureScale == 'F')
    BigDecimal t = fahrenheit ? ((c * 9 / 5) + 32) : c
    t = t.setScale(1, RoundingMode.HALF_UP)
    String unit = fahrenheit ? 'F' : 'C'
    sendIfChanged('temperature', t, unit, "temperature is ${t}°${unit}")
}

/* Only emit when the value actually changed. The device re-advertises its full sensor set in
 * every packet, so an unconditional sendEvent would flood the event log with six events per
 * advertisement. */
private void sendIfChanged(String name, Object value, String unit, String text) {
    Object current = device.currentValue(name)
    if (current != null && current.toString() == value.toString()) { return }
    Map evt = [name: name, value: value, descriptionText: text ?: "${name} is ${value}"]
    if (unit) { evt.unit = unit }
    sendEvent(evt)
    logInfo evt.descriptionText as String
}

/* Button events are momentary: always isStateChange, so a second identical press is never
 * deduplicated away. */
private void sendButtonEvent(String name, Integer buttonNumber, String text) {
    sendEvent(name: name, value: buttonNumber, descriptionText: text, isStateChange: true)
    logInfo text
}

/* Numeric coercion: the payload mixes Integer and BigDecimal, so never rely on the
 * incoming type. */
private Integer toInt(Object v) {
    if (v instanceof Number) { return ((Number) v).intValue() }
    return (v?.toString() ?: '0').toBigDecimal().intValue()
}

private BigDecimal toBigDecimal(Object v) {
    if (v instanceof BigDecimal) { return (BigDecimal) v }
    if (v instanceof Number) { return new BigDecimal(v.toString()) }
    return (v?.toString() ?: '0').toBigDecimal()
}

/* ------------------------------------------------------------------------------------------------
 * Logging
 * ------------------------------------------------------------------------------------------------ */

private void logInfo(String msg)  { if (settings.txtEnable != false) { log.info  "${device.displayName} ${msg}" } }
private void logDebug(String msg) { if (settings.logEnable) { log.debug "${device.displayName} ${msg}" } }
private void logWarn(String msg)  { log.warn "${device.displayName} ${msg}" }
