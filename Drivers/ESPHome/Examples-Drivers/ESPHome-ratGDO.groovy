/**
 *  MIT License
 *  Copyright 2022 Jonathan Bradshaw (jb@nrgup.net)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
import com.hubitat.app.DeviceWrapper

metadata {
    definition(
        name: 'ESPHome ratGDO',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw, Adrian Caramaliu',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-ratGDO.groovy') {

        capability 'Actuator'
        capability 'Sensor'
        capability 'Refresh'
        capability 'Initialize'
        capability 'Signal Strength'
        capability 'Door Control'
        capability 'Garage Door Control'
        capability "Contact Sensor"
        capability 'Switch'
        capability 'Lock'
        capability 'MotionSensor'
        capability 'Pushable Button'

        // attribute populated by ESPHome API Library automatically
        attribute 'dryContactLight', 'enum', [ 'open', 'closed' ]
        attribute 'dryContactOpen', 'enum', [ 'open', 'closed' ]
        attribute 'dryContactClose', 'enum', [ 'open', 'closed' ]
        attribute 'learn', 'enum', [ 'on', 'off' ]
        attribute 'motor', 'enum', [ 'idle', 'running' ]
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]
        attribute 'obstruction', 'enum', [ 'present', 'not present' ]
        attribute 'openings', 'number'
        attribute 'position', 'number'      
        
        command 'learnOn'
        command 'learnOff'
        command 'restart'
        command 'stop'
        command 'sync'
        command 'toggle'
    }

    preferences {
        input name: 'ipAddress',    // required setting for API library
                type: 'text',
                title: 'Device IP Address',
                required: true

        input name: 'password',     // optional setting for API library
                type: 'text',
                title: 'Device Password <i>(if required)</i>',
                required: false
        
        input name: 'childrenEnable',
            type: 'bool',
            title: "<b>Enable Child Devices?</b>",
            defaultValue: false,
            required: false


        input name: 'logEnable',    // if enabled the library will log debug details
            type: 'bool',
            title: 'Enable Debug Logging',
            required: false,
            defaultValue: false

        input name: 'logTextEnable',
            type: 'bool',
            title: 'Enable descriptionText logging',
            required: false,
            defaultValue: true
    }
}

public void initialize() {
    // API library command to open socket to device, it will automatically reconnect if needed
    openSocket()

    if (logEnable) {
        runIn(1800, 'logsOff')
    }
}

public void installed() {
    log.info "${device} driver installed"
}

public void logsOff() {
    espHomeSubscribeLogs(LOG_LEVEL_INFO, false) // disable device logging
    device.updateSetting('logEnable', false)
    log.info "${device} debug logging disabled"
}

public void updated() {
    if( !settings.childrenEnable ){
        getChildDevices().each{
            log.info "Children disabled, deleting ${ it.deviceNetworkId }"
            deleteChildDevice( it.deviceNetworkId )
        }
    }
    log.info "${device} driver configuration updated"
    initialize()
}

public void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}


// the parse method is invoked by the API library when messages are received
public void parse(Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }
    switch (message.type) {
        case 'device':
            // Device information
            break

        case 'entity':
            doParseEntity(message)
            break

        case 'state':
            doParseState(message)
            break
    }
}

private void doParseEntity(Map message) {
    if (message.platform == 'cover') {
        if (message.name == "Door") {
            state.doorKey = message.key as Long
        }
        return
    }
    if (message.platform == 'binary') {
        if (message.name == "Motion") {
            state.motionKey = message.key as Long
            getMotionDevice(message.key)
        }
        if (message.name == "Obstruction") {
            state.obstructionKey = message.key as Long
            getObstructionDevice(message.key)
        }
        if (message.name == "Button") {
            state.buttonKey = message.key as Long
            getButtonDevice(message.key)
        }
        if (message.name == "Motor") {
            state.motorKey = message.key as Long
        }
        if (message.name == "Dry contact light") {
            state.dryContactLightKey = message.key as Long
            getDryContactLightDevice(message.key)
        }
        if (message.name == "Dry contact open") {
            state.dryContactOpenKey = message.key as Long
            getDryContactOpenDevice(message.key)
        }
        if (message.name == "Dry contact close") {
            state.dryContactOpenKey = message.key as Long
            getDryContactCloseDevice(message.key)
        }
        return
    }

    if (message.platform == 'light') {
        if (message.name == "Light") {
            state.lightKey = message.key as Long
            getLightDevice(message.key)
        }
        return
    }

    if (message.platform == 'switch') {
        if (message.name == "Learn") {
            state.learnKey = message.key as Long
            getLearnDevice(message.key)
        }
        return
    }

    if (message.platform == 'lock') {
        if (message.name == "Lock remotes") {
            state.lockKey = message.key as Long
            getLockDevice(message.key)
        }
        return
    }

    if (message.platform == 'sensor') {
        if (message.name == "Openings") {
            state.openingsKey = message.key as Long
        }
        if (message.deviceClass == 'signal_strength') {
            state.signalStrengthKey = message.key
        }
        return
    }

    if (message.platform == 'button') {
        if (message.name == "Restart") {
            state.restartKey = message.key
        }
        if (message.name == "Sync") {
            state.syncKey = message.key
        }
        if (message.name == "Toggle door") {
            state.toggleKey = message.key
        }
        return
    }
}

private void doParseState(Map message) {
    String type = message.isDigital ? 'digital' : 'physical'
    // Check if the entity key matches the message entity key received to update device state
    if (state.doorKey as Long == message.key) {
        if (message.position == null) {
            return
        }
        String value
        String contact
        switch (message.currentOperation) {
            case COVER_OPERATION_IDLE:
                value = message.position > 0 ? 'open' : 'closed'
            contact = value
            break
            case COVER_OPERATION_IS_OPENING:
                value = 'opening'
            break
            case COVER_OPERATION_IS_CLOSING:
                value = 'closing'
            break
        }
        sendDeviceEvent("door", value, type, "Door")
        sendDeviceEvent("contact", contact, type, "Contact")
        int position = Math.round(message.position * 100) as int
        sendDeviceEvent("position", position, type, "Position")
        return
    }

    if (state.motionKey as Long == message.key) {
        String value = message.state ? "active" : "inactive"
        sendDeviceEvent("motion", value, type, "Motion", getMotionDevice(message.key))
        return
    }

    if (state.obstructionKey as Long == message.key) {
        String value = message.state ? "present" : "not present"
        sendDeviceEvent("obstruction", value, type, "Obstruction", getObstructionDevice(message.key), "presence")
        return
    }

    if (state.motorKey as Long == message.key) {
        String value = message.state ? "running" : "idle"
        sendDeviceEvent("motor", value, type, "Motor")
        return
    }

    if (state.learnKey as Long == message.key) {
        String value = message.state ? "on" : "off"
        sendDeviceEvent("learn", value, type, "Learn", getLearnDevice(message.key), "switch")
        return
    }

    if (state.lightKey as Long == message.key) {
        String value = message.state ? "on" : "off"
        sendDeviceEvent("switch", value, type, "Light", getLightDevice(message.key))
        return
    }

    if (state.lockKey as Long == message.key) {
        String value = message.state == 1 ? "locked" : "unlocked"
        sendDeviceEvent("lock", value, type, "Remotes lock", getLockDevice(message.key))
        return
    }

    if (state.openingsKey as Long == message.key) {
        int value = message.state as int
        sendDeviceEvent("openings", value, type, "Openings")
        return
    }

    if (state.dryContactLightKey as Long == message.key) {
        String value = message.state == 1 ? "closed" : "open"
        sendDeviceEvent("dryContactLight", value, type, "Dry Contact Light", getDryContactLightDevice(message.key))
        return
    }

    if (state.dryContactOpenKey as Long == message.key) {
        String value = message.state == 1 ? "closed" : "open"
        sendDeviceEvent("dryContactOpen", value, type, "Dry Contact Open", getDryContactOpenDevice(message.key))
        return
    }

    if (state.dryContactCloseKey as Long == message.key) {
        String value = message.state == 1 ? "closed" : "open"
        sendDeviceEvent("dryContactClose", value, type, "Dry Contact Close", getDryContactCloseDevice(message.key))
        return
    }

    if (state.buttonKey as Long == message.key && message.hasState) {
        if (message.state) {
            sendDeviceEvent("pushed", 1, type, "Button", getButtonDevice(message.key), null, true)
        }
        return
    }

    if (state.signalStrengthKey as Long == message.key && message.hasState) {
        Integer rssi = Math.round(message.state as Float)
        String unit = "dBm"
        if (device.currentValue("rssi") != rssi) {
            descriptionText = "${device} rssi is ${rssi}"
            sendEvent(name: "rssi", value: rssi, unit: unit, type: type, descriptionText: descriptionText)
            if (logTextEnable) { log.info descriptionText }
        }
        return
    }
}


// child device management
private void sendDeviceEvent(name, value, type, description, child = null, childEventName = null, isStateChange = null) {
    String descriptionText = "${description} is ${value} (${type})"
    def event = [name: name, value: value, type: type, descriptionText: descriptionText]
    if (isStateChange) {
        event.isStateChange = true
    }
    if (isStateChange || device.currentValue(name) != value) {
        if (logTextEnable) { log.info descriptionText }
        sendEvent(event)
    }
    if (child) {
        if (isStateChange || child.currentValue(name) != value) {
            if (childEventName) {
                event.name = childEventName
            }
            child.parse([event])
        }
    }

}
                               
private DeviceWrapper getOrCreateDevice(key, deviceType, label = null) {
    if (key == null || !settings.childrenEnable) {
        return null
    }
    String dni = "${device.id}-${key}" as String
    def d = getChildDevice(dni)
    if (!d) {
        d = addChildDevice(
            "hubitat",
            "Generic Component ${deviceType}",
            dni                            
        )
        d.name = "${device.label} ${label?:deviceType}"
        d.label = "${device.label} ${label?:deviceType}"
    }
    return d
}

private DeviceWrapper getMotionDevice(key) {
    return getOrCreateDevice(key, "Motion Sensor")
}

private DeviceWrapper getButtonDevice(key) {
    return getOrCreateDevice(key, "Button Controller", "Button")
}

private DeviceWrapper getLearnDevice(key) {
    return getOrCreateDevice(key, "Switch", "Learn")
}

private DeviceWrapper getLightDevice(key) {
    return getOrCreateDevice(key, "Switch", "Light")
}

private DeviceWrapper getLockDevice(key) {
    return getOrCreateDevice(key, "Lock")
}

private DeviceWrapper getObstructionDevice(key) {
    return getOrCreateDevice(key, "Presence Sensor", "Obstruction")
}

private DeviceWrapper getDryContactLightDevice(key) {
    return getOrCreateDevice(key, "Contact Sensor", "Light Dry Contact")
}

private DeviceWrapper getDryContactOpenDevice(key) {
    return getOrCreateDevice(key, "Contact Sensor", "Open Dry Contact")
}

private DeviceWrapper getDryContactCloseDevice(key) {
    return getOrCreateDevice(key, "Contact Sensor", "Close Dry Contact")
}


// driver commands
public void open() {
    if (state.doorKey) {
        // API library cover command, entity key for the cover is required
        if (logTextEnable) { log.info "${device} open" }
        espHomeCoverCommand(key: state.doorKey as Long, position: 1.0)
    }
}

public void close() {
    if (state.doorKey) {
        // API library cover command, entity key for the cover is required
        if (logTextEnable) { log.info "${device} close" }
        espHomeCoverCommand(key: state.doorKey as Long, position: 0.0)
    }
}

public void stop() {
    if (state.doorKey) {
        // API library cover command, entity key for the cover is required
        if (logTextEnable) { log.info "${device} close" }
        espHomeCoverCommand(key: state.doorKey as Long, stop: 1)
    }
}

public void on() {
    if (state.lightKey) {
        if (logTextEnable) { log.info "${device} on" }
        espHomeLightCommand(key: state.lightKey as Long, state: true)
    }
}

public void off() {
    if (state.lightKey) {
        if (logTextEnable) { log.info "${device} off" }
        espHomeLightCommand(key: state.lightKey as Long, state: false)
    }
}

public void lock() {
    if (state.lockKey) {           
        if (logTextEnable) { log.info "${device} locked" }
        espHomeLockCommand(key: state.lockKey as Long, lockCommand: LOCK_LOCK)
    }
}

public void unlock() {
    if (state.lockKey) {
        if (logTextEnable) { log.info "${device} unlocked" }
        espHomeLockCommand(key: state.lockKey as Long, lockCommand: LOCK_UNLOCK)
    }
}

public void learnOn() {
    if (logTextEnable) { log.info "${device} on" }
    espHomeSwitchCommand(key: settings.learn as Long, state: true)
}

public void learnOff() {
    if (logTextEnable) { log.info "${device} off" }
    espHomeSwitchCommand(key: settings.learn as Long, state: false)
}


public void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

public void restart() {
    if (state.restartKey) {
        log.info "${device} restart"
        espHomeButtonCommand(key: state.restartKey as Long)
    }
}

public void sync() {
    if (state.syncKey) {
        log.info "${device} sync"
        espHomeButtonCommand(key: state.syncKey as Long)
    }
}

public void toggle() {
    if (state.toggleKey) {
        log.info "${device} toggle"
        espHomeButtonCommand(key: state.toggleKey as Long)
    }
}


// child component commands
public void componentOn(DeviceWrapper dw) {
    Long key = dw.getDeviceNetworkId().minus("${device.id}-") as Long
    if (state.lightKey as Long == key) {
        on()
    }
    if (state.learnKey as Long == key) {
        learnOn()
    }
}

public void componentOff(DeviceWrapper dw) {
    Long key = dw.getDeviceNetworkId().minus("${device.id}-") as Long
    if (state.lightKey as Long == key) {
        off()
    }
    if (state.learnKey as Long == key) {
        learnOff()
    }
}

public void componentLock(DeviceWrapper dw) {
    Long key = dw.getDeviceNetworkId().minus("${device.id}-") as Long
    if (state.lockKey as Long == key) {
        lock()
    }
}

public void componentUnlock(DeviceWrapper dw) {
    Long key = dw.getDeviceNetworkId().minus("${device.id}-") as Long
    if (state.lockKey as Long == key) {
        unlock()
    }
}

public void componentRefresh(DeviceWrapper dw) {
    refresh()
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
