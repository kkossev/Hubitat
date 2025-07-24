# Aqara Climate Sensor W100 Driver for Hubitat Elevation

This custom driver connects your **Aqara Climate Sensor W100** [(Amazon link)](https://geni.us/aqaraW100) directly to your Hubitat Elevation hub via **Zigbee** protocol. No additional hubs, bridges, or cloud dependencies required - just pure local control.

The driver can be installed manually from [GitHub](https://github.com/kkossev/Hubitat) ~~or using the community [Hubitat Package Manager](https://community.hubitat.com/t/release-hubitat-package-manager-hpm-hubitatcommunity/94471/1) app.~~ (TODO)

• 🚫 **No Aqara Hub Needed**: Direct Zigbee connection to your Hubitat hub  
• 🚫 **No Cloud Dependencies**: Everything runs locally on your network  
• ⚡ **Easy Setup**: Install driver, pair device via Zigbee, configure preferences - done  
• 🔧 **Full Feature Support**: All device capabilities work out-of-the-box  
• 🏠 **Native Hubitat Integration**: Full support for Hubitat automations, dashboards, and apps  
• 🔋 **Battery Powered**: Long-lasting battery operation with voltage monitoring  
• 📱 **Button Controls**: Three physical buttons with multiple action types  

---

## [Aqara Climate Sensor W100](https://geni.us/aqaraW100)

| | |
|---|---|
| [![Aqara Climate Sensor W100](https://m.media-amazon.com/images/I/61VK0FifIdL._SX522_.jpg)](https://geni.us/aqaraW100) | **Aqara Climate Sensor W100 Features:**<br/>• Zigbee 3.0 wireless protocol with local control<br/>• Battery-powered operation with long life<br/>• **Temperature**: High-precision measurement with alerts<br/>• **Humidity**: Relative humidity monitoring with thresholds<br/>• **E-ink Display**: Clear readings with auto-off feature<br/>• **Button Controls**: Plus, Center, Minus buttons with multiple actions<br/>• **Temperature Range**: -20°C to +60°C (-4°F to +140°F)<br/>• **Humidity Range**: 0-98% RH, no condensation<br/>• **Advanced Configuration**: Customizable reporting modes and intervals |
| **Climate Monitoring Features:**<br/>• Real-time temperature and humidity monitoring<br/>• High/low temperature and humidity alerts<br/>• Configurable reporting thresholds and periods<br/>• Internal and external sensor support<br/>• Power outage detection and counting<br/>• Device health monitoring with diagnostics<br/>• Battery voltage reporting and percentage<br/>• Multiple sampling rate options | [![W100 Dimensions](https://m.media-amazon.com/images/I/51EVPpupQ-L._SL1500_.jpg)](https://geni.us/aqaraW100) |
| [![W100 Button Interface](https://m.media-amazon.com/images/I/61FO-z87XxL._SL1500_.jpg)](https://geni.us/aqaraW100) | **Multi-Function Button Controls:**<br/>• **Plus Button**: Upper button for interactions<br/>• **Center Button**: Main control button<br/>• **Minus Button**: Lower button for adjustments<br/>• **Action Types**: Push, Hold, Double-tap, Release<br/>• **Hubitat Integration**: Full button capability support<br/>• **Automation Ready**: Use buttons for scene control and triggers |
| **Advanced Features:**<br/>• Configurable sampling rates for optimal battery life<br/>• External sensor support for specialized monitoring<br/>• Power outage detection and counting<br/>• Comprehensive device health diagnostics<br/>• Local Zigbee control without cloud dependencies<br/>• Full Hubitat dashboard and automation integration | [![W100 Advanced Features](https://m.media-amazon.com/images/I/61bFBROuf0L._SL1500_.jpg)](https://geni.us/aqaraW100) | 


---

## Driver Description

The Aqara Climate Sensor W100 Zigbee driver is a work in progress. The basic functionalities (temperature & humidity reporting and the three keys) are working, but adding the external sensor readings to the display and HVAC control is not available (yet).

### Main Attributes

**Core Climate Monitoring:**
• `temperature`: Current temperature reading in °C or °F  
• `humidity`: Relative humidity percentage (0-100% RH)  
• `healthStatus`: Device health status ['online', 'offline']  
• `powerSource`: Power source type ['battery']  

**Display and Device Settings:**
• `displayOff`: E-ink display auto-off setting ['disabled', 'enabled']  
• `sensor`: Active sensor mode ['internal', 'external']  
• `sampling`: Sensor sampling rate ['low', 'standard', 'high', 'custom']  
• `period`: Custom sampling period in seconds  
• `Status`: Device operational status  

**Temperature Configuration:**
• `highTemperature`: High temperature alert threshold  
• `lowTemperature`: Low temperature alert threshold  
• `tempReportMode`: Temperature reporting mode ['no', 'threshold', 'period', 'threshold_period']  
• `tempPeriod`: Temperature reporting period  
• `tempThreshold`: Temperature change threshold for reporting  

**Humidity Configuration:**
• `highHumidity`: High humidity alert threshold  
• `lowHumidity`: Low humidity alert threshold  
• `humiReportMode`: Humidity reporting mode ['no', 'threshold', 'period', 'threshold_period']  
• `humiPeriod`: Humidity reporting period  
• `humiThreshold`: Humidity change threshold for reporting  

**Button Interface:**
• `numberOfButtons`: Button count (3 buttons available)  
• `pushed`: Last pushed button number  
• `held`: Last held button number  
• `doubleTapped`: Last double-tapped button number  
• `released`: Last released button number  

**Network and Diagnostics:**
• `rtt`: Network round-trip time in milliseconds  
• `powerOutageCount`: Power outage detection counter (if available)  

### Advanced Configuration Attributes

These additional attributes provide specialized functionality and can be configured through the device preferences:

**External Sensor Support:**
• `externalTemperature`: External temperature sensor reading (when in external mode)  
• `externalHumidity`: External humidity sensor reading (when in external mode)  

**Battery and Power Management:**
• Battery monitoring attributes are currently work-in-progress (W.I.P.)  
• `powerSource`: Shows 'battery' as the power source  

**Device Health and Diagnostics:**
• `healthStatus`: Real-time device connectivity status  
• `rtt`: Network latency measurement for troubleshooting  
• `Status`: Overall device operational status  

**Note:** All temperature and humidity configuration attributes (thresholds, reporting modes, periods) are now exposed as main attributes and can be directly monitored and configured through the device interface.  

### Commands

**Device Management:**
• `configure()` - Configure device settings and initialize Zigbee attributes  
• `refresh()` - Refresh all device attributes and current readings  
• `ping()` - Test network connectivity and measure round-trip time  

**Button Commands:**
• `push(buttonNumber)` - Simulate a button push event (button number 1-3)  
• `hold(buttonNumber)` - Simulate a button hold event (button number 1-3)  
• `doubleTap(buttonNumber)` - Simulate a button double-tap event (button number 1-3)  
• `release(buttonNumber)` - Simulate a button release event (button number 1-3)  

**Configuration Commands:**
• `*** LOAD ALL DEFAULTS ***` - Load all default settings and preferences  
• `Configure the device` - Configure device settings and initialize Zigbee attributes  
• `Reset Statistics` - Reset device statistics and counters  
• `Delete All Preferences` - Remove all stored preferences (reset to factory defaults)  
• `Delete All Current States` - Clear all current device state information  
• `Delete All Scheduled Jobs` - Cancel all scheduled device tasks  
• `Delete All State Variables` - Clear all internal state variables  
• `Delete All Child Devices` - Remove any associated child devices  

**Note:** Button commands accept button numbers 1, 2, or 3 corresponding to the Plus, Center, and Minus buttons respectively. These commands can be used for testing or automation purposes to simulate physical button presses.

### Preferences

#### Basic Settings

• `txtEnable` (true) - Enable descriptive text logging  
• `logEnable` (true) - Enable debug logging for troubleshooting  
• `forcedProfile` - Manually select device profile if auto-detection fails  

#### Climate Configuration

**Temperature Settings:**
• `highTemperature` (26.0-60.0°C) - High temperature alert threshold  
• `lowTemperature` (-20.0-20.0°C) - Low temperature alert threshold  
• `tempReportMode` - How temperature changes are reported  
• `tempPeriod` (1.0-10.0 sec) - Temperature reporting interval  
• `tempThreshold` (0-3°C) - Minimum temperature change to trigger report  

**Humidity Settings:**
• `highHumidity` (65.0-100.0%) - High humidity alert threshold  
• `lowHumidity` (0.0-30.0%) - Low humidity alert threshold  
• `humiReportMode` - How humidity changes are reported  
• `humiPeriod` (1.0-10.0 sec) - Humidity reporting interval  
• `humiThreshold` (2.0-10.0%) - Minimum humidity change to trigger report  

#### Device Settings

• `displayOff` - Enable/disable automatic display shutdown  
• `sampling` - Sensor sampling rate configuration  
• `period` (0.5-600.0 sec) - Custom sampling period for 'custom' mode  
• `sensor` - Select internal or external sensor mode  

#### Battery Settings

• `voltageToPercent` - Convert battery voltage to percentage  

### Advanced Features

**Zigbee Attribute Control:**
The driver provides direct access to all Aqara proprietary cluster (0xFCC0) attributes, allowing fine-grained control over device behavior including reporting modes, thresholds, and sampling rates.

**Button Capabilities:**
Full support for all three physical buttons with push, hold, double-tap, and release actions. Perfect for scene control and automation triggers.

**External Sensor Support:**
The W100 can work with external temperature and humidity sensors, with the driver automatically detecting and reporting readings from both internal and external sources.

**Health Monitoring:**
Comprehensive device health tracking including power outage detection, parent network tracking, uptime monitoring, and battery health reporting.

**Local Control:**
All functionality operates locally without cloud dependencies. The device communicates directly with your Hubitat hub via Zigbee protocol.

---

## Technical Specifications

**Device Information:**
• **Model**: TH-S04D (Zigbee version)  
• **Protocol**: Zigbee 3.0  
• **Power**: Battery powered (internal rechargeable battery)  
• **Dimensions**: 86 × 86 × 13.65 mm (3.39 × 3.39 × 0.54 in.)  

**Environmental Ranges:**
• **Operating Temperature**: -20°C to +60°C (-4°F to +140°F)  
• **Operating Humidity**: 0-98% RH, no condensation  
• **Temperature Accuracy**: ±0.3°C  
• **Humidity Accuracy**: ±3% RH  

**Connectivity:**
• **Wireless**: Zigbee 3.0, Bluetooth (for setup)  
• **Range**: Standard Zigbee mesh network range  
• **Battery Life**: Extended operation with configurable reporting intervals  

**Package Contents:**
• Climate Sensor W100 × 1  
• User Manual × 1  
• Compliance Statement × 1  
• Screw Kit × 1  
• Wall Bracket × 1  
• Desktop Stand × 1  

---

## Installation and Setup

1. **Install Driver**: Add the driver code to your Hubitat hub through the Drivers Code section
2. **Pair Device**: Put device in pairing mode and add via Hubitat's Zigbee discovery
3. **Configure Preferences**: Set your desired temperature/humidity thresholds and reporting modes
4. **Test Functionality**: Verify temperature, humidity, and button operations
5. **Setup Automations**: Create rules using the sensor data and button events

The device will automatically be recognized and configured with default settings that work for most use cases. Advanced users can customize all aspects of the device behavior through the comprehensive preference settings.

---

## Use Cases

• **Climate Monitoring**: Track temperature and humidity in any room  
• **Smart Alerts**: Get notified when conditions exceed your thresholds  
• **HVAC Automation**: Use readings to control heating, cooling, and dehumidifiers  
• **Scene Control**: Use the three buttons to trigger lighting and device scenes  
• **Data Logging**: Historical tracking of environmental conditions  
• **Multi-Zone Control**: Deploy multiple sensors for whole-home monitoring  
• **External Sensors**: Connect additional probes for specialized monitoring  

---

## Troubleshooting

**Device Not Responding:**
• Check battery level and replace if needed  
• Verify Zigbee mesh connectivity  
• Try refreshing the device  

**Inaccurate Readings:**
• Check sensor placement (avoid direct sunlight, heat sources)  
• Verify external sensor connections if using external mode  
• Allow time for sensor stabilization after power-on  

**Button Events Not Working:**
• Ensure device is fully paired and initialized  
• Check button battery level  
• Verify endpoint configuration  

**Preference Changes Not Applied:**
• Use "Update All Preferences" command after changes  
• Check for validation errors in device logs  
• Reset to defaults if needed and reconfigure  

---

## Additional Links

- [Driver Code](https://github.com/kkossev/Hubitat/tree/development/Drivers/Aqara%20Climate%20Sensor%20W100)
- [Issues](https://github.com/kkossev/Hubitat/issues)
- [Aqara Official Product Page](https://www.aqara.com/en/product/climate-sensor-w100/)
- [Hubitat Community Forum](https://community.hubitat.com/)
- [Aqara Support](https://store-support.aqara.com/products/climate-sensor-w100)
