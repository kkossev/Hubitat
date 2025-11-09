# Thermostats Sync - Hubitat App

## Overview
The **Thermostats Sync** app is a Hubitat automation app that synchronizes the main attributes of two thermostats bidirectionally. When an attribute changes on one thermostat, the same attribute is automatically applied to the other thermostat.

## Installation

### Option 1: Hubitat Package Manager (Recommended)
1. Install [Hubitat Package Manager](https://community.hubitat.com/t/beta-hubitat-package-manager/38016) if you haven't already
2. Go to **Apps** → **Hubitat Package Manager**
3. Select **Install** → **Search by Keywords**
4. Search for "**Thermostats Sync**"
5. Select the package and click **Next**
6. Review and click **Install**

### Option 2: Manual Installation
You can install the App manually from GitHub: https://raw.githubusercontent.com/kkossev/Hubitat/development/Apps/Thermostats%20Sync/Thermostats_Sync.groovy

**Note**: This is an **App** (not a driver!). Import it under **Apps Code** in the Hubitat interface.



![Aqara W100 Virtual Sync Example](https://github.com/kkossev/Hubitat/blob/development/Apps/Thermostats%20Sync/Images/aqara-w100-virtual-sync.png?raw=true)



## Features

### Enhanced Synchronization Support
- **8 Sync Attributes**: Supports thermostat mode, heating/cooling setpoints, fan mode, temperature, operating state, battery level, and health status
- **Command Validation**: Automatically checks if target devices support required commands before attempting sync
- **Flexible Configuration**: Each attribute can be independently enabled/disabled
- **Advanced Attributes**: Battery and health status sync are optional (disabled by default)

### Manual Control Buttons
- **Sync Thermostat1 → Thermostat2**: Manually trigger one-way synchronization from the first to second thermostat
- **Sync Thermostat2 → Thermostat1**: Manually trigger one-way synchronization from the second to first thermostat  
- **Start AutoSync**: Enable automatic bidirectional synchronization
- **Stop AutoSync**: Disable automatic synchronization (manual sync buttons remain functional)

### Synchronization Options
- **Thermostat Mode**: Synchronizes mode changes (off/heat/cool/auto)
- **Heating Setpoint**: Synchronizes heating temperature setpoint changes
- **Cooling Setpoint**: Synchronizes cooling temperature setpoint changes  
- **Fan Mode**: Synchronizes fan mode changes (auto/on/circulate)
- **Temperature**: Synchronizes temperature readings (uses setTemperature command, enabled by default)
- **Operating State**: Synchronizes thermostat operating state (uses setThermostatOperatingState command, enabled by default)
- **Battery Level**: Synchronizes battery percentage (uses setBattery command, disabled by default)
- **Health Status**: Synchronizes device health status - online/offline (uses setHealthStatus command, disabled by default)


### Loop Prevention
- **Synchronization Delay**: Configurable delay (100-5000ms) before applying changes
- **Maximum Sync Attempts**: Prevents runaway synchronization with attempt limits (1-10)
- **TRVZB Device Support**: Special handling for buggy thermostats that send duplicate events
- **Global Flag Clearing**: All sync flags cleared every 2 seconds to ensure fresh sync capability

### User Interface
![Thermostats Sync UI](https://github.com/kkossev/Hubitat/blob/development/Apps/Thermostats%20Sync/Images/thermostats-sync-1.png?raw=true)

![Thermostats Sync UI - Advanced Settings](https://github.com/kkossev/Hubitat/blob/development/Apps/Thermostats%20Sync/Images/thermostats-sync-2.png?raw=true)
- **Device Selection**: Easy selection of two thermostats from available devices
- **Real-time Status**: Shows current values of both thermostats including all syncable attributes
- **Manual Control Buttons**: Manual sync, AutoSync control, and debug functions
- **Granular Control**: Individual enable/disable for each of 8 sync attributes
- **Instance Management**: Custom naming support for multiple app instances

## Configuration

### Required Settings
- **First Thermostat**: Select the first thermostat device
- **Second Thermostat**: Select the second thermostat device

### Manual Controls
- **Manual Sync Buttons**: Use the directional sync buttons for immediate one-way synchronization
- **AutoSync Control**: Use Start/Stop AutoSync buttons to enable or disable automatic synchronization
- **Status Monitoring**: Check the AutoSync Status display to confirm current state

### Optional Settings
- **Synchronization Options**: Choose which attributes to sync (6 core attributes enabled by default, battery and health status disabled)
- **Synchronization Delay**: Time delay before applying changes (default: 500ms)
- **Maximum Sync Attempts**: Limit sync attempts per change (default: 3)
- **Logging Options**: Debug, informational, and trace logging available

## Usage

### Getting Started
1. **Install the App**: Import the Thermostats_Sync.groovy file as a new app in Hubitat
2. **Configure Devices**: Select your two thermostats from the device dropdowns
3. **Choose Sync Options**: Enable the attributes you want to synchronize
4. **Test Manual Sync**: Use the manual sync buttons to verify operation
5. **Enable AutoSync**: Use the "Start AutoSync" button to enable automatic synchronization

### Manual Synchronization
- **One-Way Sync**: Use directional sync buttons for immediate synchronization
- **Selective Sync**: Only enabled attributes are synced during manual operations

### AutoSync Control
- **Enable**: Click "Start AutoSync" to begin automatic bidirectional synchronization
- **Disable**: Click "Stop AutoSync" to disable automatic sync while preserving manual controls
- **Status Check**: Monitor the AutoSync Status display to confirm current operational state

## Troubleshooting

**AutoSync not working:**
- Check that AutoSync Status shows "Enabled"
- Verify both thermostats are selected and different devices
- Check that desired sync options are enabled

**Manual sync not responding:**
- Ensure both thermostats are properly selected
- Check device logs for error messages
- Verify target thermostat supports the required commands

**Sync attempts reaching maximum:**
- Use "Clear All Sync Flags" button to reset stuck sync state
- Check for conflicting automations affecting the thermostats
- For TRVZB devices, increase synchronization delay to 1000ms or higher

**Advanced attributes not syncing:**
- Temperature, operating state, battery, and health status require specific device commands
- Check device logs to verify target devices support the required commands
- Some attributes may be read-only on certain devices

Enable debug logging for detailed troubleshooting information. Debug logging automatically disables after 30 minutes.

