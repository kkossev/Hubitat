# Human Activity Check - Hubitat App

A comprehensive Hubitat app that monitors multiple types of sensors to detect human activity and provides notifications when no activity is detected for a configurable period.

## Features

### Device Monitoring
- **Motion Sensors**: Detects active/inactive states
- **Contact Sensors**: Monitors open/closed states  
- **Acceleration Sensors**: Tracks active/inactive movement
- **Pushable Buttons**: Monitors button presses
- **Switches**: Tracks on/off states
- **Locks**: Monitors locked/unlocked states

### Smart Notifications
- Configurable inactivity timeout (1-120 minutes)
- Single alarm cycle to prevent notification spam
- Detailed multi-line notifications with device information
- Automatic alarm clearing when activity resumes

### Virtual Switch Integration
- Optional virtual switch control during alarms
- Automatically turns switch ON when alarm triggers
- Automatically turns switch OFF when activity resumes
- Test functionality to manually control switch

### Real-Time Status Display
- Live status table showing all monitored devices
- Device name, room, type, current status, last change time, and age
- Sortable columns (click headers to sort)
- Auto-refresh every 5 minutes
- Manual refresh button

### Room Organization
- Group devices by room for better organization
- Room information displayed in status table
- Configurable room assignments per device

## Installation

1. In the Hubitat web interface, go to **Apps Code**
2. Click **New App**
3. Copy and paste the contents of `human-activity-check.groovy`
4. Click **Save**
5. Go to **Apps** and click **Add User App**
6. Select **Human Activity Check**

## Configuration

### Device Selection
1. **Motion Sensors**: Select motion sensors to monitor
2. **Contact Sensors**: Select contact sensors to monitor
3. **Acceleration Sensors**: Select acceleration sensors to monitor
4. **Pushable Buttons**: Select buttons to monitor
5. **Switches**: Select switches to monitor
6. **Locks**: Select locks to monitor

### Notification Settings
- **Notification Devices**: Select devices to receive notifications (phones, speakers, etc.)
- **Inactivity Timeout**: Set minutes of inactivity before triggering alarm (1-120 minutes)

### Optional Features
- **Virtual Switch**: Select a virtual switch to control during alarms
- **Room Assignments**: Configure room names for each device

### Testing
- **Test Alarm**: Manually trigger a test notification
- **Refresh Device States**: Force update of all device states

## Behavior

### Activity Detection
The app monitors all selected devices and tracks their last activity time. Activity is defined as:
- Motion sensors: becoming active
- Contact sensors: opening
- Acceleration sensors: becoming active  
- Buttons: being pushed
- Switches: turning on
- Locks: being unlocked

### Alarm Logic
1. App checks all devices every 5 minutes
2. If ALL devices have been inactive longer than the configured timeout, an alarm triggers
3. Notifications are sent and virtual switch (if configured) turns ON
4. When ANY device shows activity, the alarm clears automatically
5. Virtual switch turns OFF and notifications stop

### Notification Format
```
Human Activity Check ALARM

No activity detected for 25 minutes

Inactive Devices:
• Kitchen Motion (Motion) - 26m
• Front Door (Contact) - 25m  
• Living Room Switch (Switch) - 30m

Last activity: Kitchen Motion at 08/30/2025 14:30:15
```

## Status Table

The status table provides real-time information:
- **Device**: Device name with room (if configured)
- **Type**: Sensor type (Motion, Contact, Acceleration, Button, Switch, Lock)
- **Status**: Current state (Active/Inactive with color coding)
- **Last Change**: Date and time of last activity
- **Age**: Time since last activity

### Table Features
- **Sortable Columns**: Click any header to sort ascending/descending
- **Color Coding**: Green for active states, gray for inactive
- **Auto-refresh**: Updates every 5 minutes automatically
- **Manual Refresh**: Click "Refresh Device States" button

## Troubleshooting

### Devices Show "Unknown" Status
- Click "Refresh Device States" to initialize device states
- Ensure devices are properly paired and responding
- Check device logs for communication issues

### Notifications Not Working
- Verify notification devices are selected and working
- Test notifications using the "Test Alarm" button
- Check notification device settings and capabilities

### False Alarms
- Increase the inactivity timeout if needed
- Review which devices are selected for monitoring
- Some devices may report activity differently than expected

### Virtual Switch Not Working
- Ensure virtual switch device is created and selected
- Test using "Test Alarm" button
- Check virtual switch device logs

### Table Sorting Resets
- Sorting is preserved until page refresh
- "Refresh Device States" button will reset sort order
- This is normal behavior due to Hubitat's web interface architecture

## Implementation Details

### Capabilities Used
- `motionSensor` (active/inactive)
- `contactSensor` (open/closed)
- `accelerationSensor` (active/inactive)
- `pushableButton` (pushed)
- `switch` (on/off)
- `lock` (locked/unlocked)

### State Management
The app maintains persistent state for:
- Last activity time for each device
- Last known status for each device
- Alarm active/inactive state
- Device subscription management

## Version History

### v2.0.0 (2025-08-30)
- Added support for pushable buttons, switches, and locks
- Implemented sortable status table with click-to-sort headers
- Enhanced notification formatting with multi-line structure
- Improved device state initialization and refresh functionality
- Added side-by-side button layout for better UI
- Fixed duration formatting to show integer values
- Enhanced room organization and display
- Improved alarm logic and state management
- Added comprehensive error handling and logging

### v1.0.0 (2025-08-29)
- Initial release with motion, contact, and acceleration sensors
- Basic notification system and virtual switch integration
- Room labeling and status table
- Test alarm functionality

## Support

For issues, questions, or feature requests, please check:
1. Device logs in Hubitat interface
2. App logs for debugging information
3. Hubitat community forums
4. This documentation for troubleshooting tips

## License

This app is provided as-is for use with Hubitat Elevation hubs. Modify and distribute freely while maintaining attribution.
