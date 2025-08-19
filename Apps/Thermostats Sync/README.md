# Thermostats Sync - Hubitat App

## Overview
The **Thermostats Sync** app is a Hubitat automation app that synchronizes the main attributes of two thermostats bidirectionally. When an attribute changes on one thermostat, the same attribute is automatically applied to the other thermostat.

## Features

### Synchronization Options
- **Thermostat Mode**: Synchronizes mode changes (off/heat/cool/auto)
- **Heating Setpoint**: Synchronizes heating temperature setpoint changes
- **Cooling Setpoint**: Synchronizes cooling temperature setpoint changes  
- **Fan Mode**: Synchronizes fan mode changes (auto/on/circulate)
- **Schedule Mode**: Synchronizes schedule mode changes (if supported by devices)

### Loop Prevention
- **Synchronization Delay**: Configurable delay (100-5000ms) before applying changes
- **Maximum Sync Attempts**: Prevents runaway synchronization with attempt limits (1-10)
- **In-Progress Tracking**: Prevents multiple simultaneous syncs of the same attribute
- **Counter Reset**: Automatic reset of sync counters after successful operations

### User Interface
- **Device Selection**: Easy selection of two thermostats from available devices
- **Real-time Status**: Shows current values of both thermostats when selected
- **Granular Control**: Individual enable/disable for each sync type
- **Comprehensive Logging**: Debug and informational logging options

## Installation

1. Copy the `Thermostats_Sync.groovy` file to your Hubitat hub
2. Navigate to **Apps** → **Add User App** 
3. Select **Thermostats Sync** from the list
4. Configure your settings and save

## Configuration

### Required Settings
- **First Thermostat**: Select the first thermostat device
- **Second Thermostat**: Select the second thermostat device

### Optional Settings
- **Synchronization Options**: Choose which attributes to sync (all enabled by default except Schedule Mode)
- **Synchronization Delay**: Time delay before applying changes (default: 500ms)
- **Maximum Sync Attempts**: Limit sync attempts per change (default: 3)
- **Debug Logging**: Enable/disable debug logging (auto-disables after 30 minutes)
- **Description Logging**: Enable/disable informational logging

## How It Works

1. **Event Subscription**: The app subscribes to attribute change events for selected sync types
2. **Change Detection**: When an attribute changes on either thermostat, the corresponding handler is triggered
3. **Loop Prevention**: The app checks if a sync is already in progress and enforces attempt limits
4. **Delayed Execution**: Changes are applied after the configured delay to prevent rapid-fire loops
5. **Bidirectional Sync**: Both thermostats can trigger changes to the other

## Safety Features

### Infinite Loop Prevention
- **In-Progress Flags**: Tracks when sync operations are active
- **Sync Counters**: Limits the number of sync attempts per attribute
- **Automatic Reset**: Counters reset after 5 seconds to allow normal operation
- **Error Handling**: Try-catch blocks prevent crashes from device communication issues

### Input Validation
- **Device Checking**: Ensures both thermostats are selected and different devices
- **Capability Verification**: Checks if target devices support required commands
- **Null Safety**: Handles missing or invalid device references gracefully

## Logging

The app provides comprehensive logging:

- **Info Level**: Successful sync operations and initialization
- **Debug Level**: Detailed sync state tracking and counter management  
- **Warn Level**: Error conditions, max attempts reached, unsupported commands

Debug logging automatically disables after 30 minutes to prevent log spam.

## Troubleshooting

### Common Issues

1. **Sync Not Working**
   - Verify both thermostats are selected and different devices
   - Check that sync options are enabled for desired attributes
   - Ensure devices support the thermostat capability

2. **Infinite Loops**
   - Reduce sync delay if responses are too slow
   - Check max sync attempts setting (default 3 should be sufficient)
   - Monitor logs for "maximum sync attempts reached" warnings

3. **Missing Schedule Sync**
   - Not all thermostats support schedule commands
   - Check device documentation for setSchedule capability
   - Monitor logs for "does not support setSchedule command" messages

### Diagnostic Information

Enable debug logging to see:
- Sync state tracking
- Counter increments and resets
- Device communication attempts
- Loop prevention logic

## Version History

- **v1.0.0** (2025-08-18): Initial release with full bidirectional sync and loop prevention

## License

Licensed under the Apache License, Version 2.0. See the file header for full license text.

## Author

Created by Krassimir Kossev (kkossev)
