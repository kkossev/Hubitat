# Thermostats Sync - Hubitat App

## Overview
The **Thermostats Sync** app is a Hubitat automation app that synchronizes the main attributes of two thermostats bidirectionally. When an attribute changes on one thermostat, the same attribute is automatically applied to the other thermostat.

You can install the App manually from GitHub : https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Apps/Thermostats%20Sync/Thermostats_Sync.groovy

Please note, that this is an **App** (not a driver!)

Threw together a quick solution with a hand from Claude Sonnet 4. The UI’s rough, but it does the job.

## Features

### Manual Control Buttons
- **Sync Thermostat1 → Thermostat2**: Manually trigger one-way synchronization from the first to second thermostat
- **Sync Thermostat2 → Thermostat1**: Manually trigger one-way synchronization from the second to first thermostat  
- **Start AutoSync**: Enable automatic bidirectional synchronization
- **Stop AutoSync**: Disable automatic synchronization (manual sync buttons remain functional)

### AutoSync Management
- **Real-time Status Display**: Shows current AutoSync state (Enabled/Disabled)
- **Dynamic Control**: Enable or disable automatic synchronization without reinstalling the app
- **Independent Operation**: Manual sync buttons work regardless of AutoSync status

### Version Information
- **Version Display**: Shows current app version for easy reference
- **Compile Time**: Displays when the app was last compiled for troubleshooting

### Synchronization Options
- **Thermostat Mode**: Synchronizes mode changes (off/heat/cool/auto)
- **Heating Setpoint**: Synchronizes heating temperature setpoint changes
- **Cooling Setpoint**: Synchronizes cooling temperature setpoint changes  
- **Fan Mode**: Synchronizes fan mode changes (auto/on/circulate)


### Loop Prevention
- **Synchronization Delay**: Configurable delay (100-5000ms) before applying changes
- **Maximum Sync Attempts**: Prevents runaway synchronization with attempt limits (1-10)
- **In-Progress Tracking**: Prevents multiple simultaneous syncs of the same attribute
- **Counter Reset**: Automatic reset of sync counters after successful operations

### User Interface
![Thermostats Sync UI](https://github.com/kkossev/Hubitat/blob/development/Apps/Thermostats%20Sync/Images/thermostats-sync-1.png?raw=true)

![Thermostats Sync UI - Advanced Settings](https://github.com/kkossev/Hubitat/blob/development/Apps/Thermostats%20Sync/Images/thermostats-sync-2.png?raw=true)
- **Device Selection**: Easy selection of two thermostats from available devices
- **Real-time Status**: Shows current values of both thermostats when selected
- **Manual Control Buttons**: Four dedicated buttons for manual sync operations and AutoSync control
- **AutoSync Status**: Live display of automatic synchronization state
- **Version Information**: Current version and compile time display for reference
- **Granular Control**: Individual enable/disable for each sync type
- **Comprehensive Logging**: Debug and informational logging options

## Configuration

### Required Settings
- **First Thermostat**: Select the first thermostat device
- **Second Thermostat**: Select the second thermostat device

### Manual Controls
- **Manual Sync Buttons**: Use the directional sync buttons for immediate one-way synchronization
- **AutoSync Control**: Use Start/Stop AutoSync buttons to enable or disable automatic synchronization
- **Status Monitoring**: Check the AutoSync Status display to confirm current state

### Optional Settings
- **Synchronization Options**: Choose which attributes to sync (all enabled by default)
- **Synchronization Delay**: Time delay before applying changes (default: 500ms)
- **Maximum Sync Attempts**: Limit sync attempts per change (default: 3)
- **Debug Logging**: Enable/disable debug logging (auto-disables after 30 minutes)
- **Description Logging**: Enable/disable informational logging

## Usage

### Getting Started
1. **Install the App**: Import the Thermostats_Sync.groovy file as a new app in Hubitat
2. **Configure Devices**: Select your two thermostats from the device dropdowns
3. **Choose Sync Options**: Enable the attributes you want to synchronize
4. **Test Manual Sync**: Use the manual sync buttons to verify operation
5. **Enable AutoSync**: Use the "Start AutoSync" button to enable automatic synchronization

### Manual Synchronization
- **One-Way Sync**: Use "Sync Thermostat1 → Thermostat2" or "Sync Thermostat2 → Thermostat1" for immediate directional sync
- **Selective Sync**: Manual sync respects your selected synchronization options (only enabled attributes are synced)
- **Error Handling**: Manual sync operations include comprehensive error handling and logging

### AutoSync Control
- **Enable**: Click "Start AutoSync" to begin automatic bidirectional synchronization
- **Disable**: Click "Stop AutoSync" to disable automatic sync while preserving manual controls
- **Status Check**: Monitor the AutoSync Status display to confirm current operational state
<details>
<summary>Technical Details (click to expand)</summary>

## How It Works

1. **Event Subscription**: The app subscribes to attribute change events for selected sync types
2. **Change Detection**: When an attribute changes on either thermostat, the corresponding handler is triggered
3. **Enhanced Loop Prevention**: The app uses immediate flag setting and bidirectional checking to prevent race conditions
4. **Delayed Execution**: Changes are applied after the configured delay to prevent rapid-fire loops
5. **Bidirectional Sync**: Both thermostats can trigger changes to the other (when AutoSync is enabled)
6. **Manual Override**: Manual sync buttons provide immediate one-way synchronization regardless of AutoSync state

## Safety Features

### Enhanced Loop Prevention
- **Immediate Flag Setting**: Sync flags are set immediately when events trigger (not when they execute)
- **Bidirectional Checking**: Checks sync status for both source and target devices
- **Dual Flag Clearing**: Both source and target device flags are cleared when sync completes
- **Race Condition Prevention**: Handles rapid user input without false blocking
- **Sync Counters**: Limits the number of sync attempts per attribute
- **Automatic Reset**: Counters reset after 5 seconds to allow normal operation
- **Error Handling**: Try-catch blocks with finally clauses ensure flags are always cleared

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

**AutoSync not working:**
- Check that AutoSync Status shows "Enabled"
- Use "Start AutoSync" button if status shows "Disabled"
- Verify both thermostats are selected and different devices
- Check that desired sync options are enabled

**Manual sync buttons not responding:**
- Ensure both thermostats are properly selected
- Check device logs for error messages
- Verify target thermostat supports the required commands

**Rapid changes being blocked:**
- This is normal behavior to prevent loops
- Wait a few seconds between rapid changes
- Use manual sync buttons for immediate override
- Check sync attempt limits in configuration

**Sync attempts reaching maximum:**
- Review the synchronization delay setting
- Check for conflicting automations affecting the thermostats
- Verify thermostat firmware is up to date
- Use debug logging to identify the source of conflicts

### Debug Information

Enable debug logging to see detailed sync operations:
- Sync flag management and timing
- Counter tracking and reset operations  
- Device communication attempts and results
- AutoSync state changes and button interactions

**Note**: Debug logging automatically disables after 30 minutes to prevent log spam.

### Version Information

Current version and compile time are displayed in the app interface for easy reference when troubleshooting or reporting issues.

</details>

