# Human Activity Check - Setup Guide

This guide will walk you through setting up the Human Activity Check app for your specific needs.

## Quick Start

1. **Install the App** (see README.md for detailed installation steps)
2. **Select Your Sensors** - Choose at least one sensor type
3. **Set Inactivity Threshold** - How long to wait before alerting (default: 60 minutes)
4. **Configure Notifications** - Select notification devices
5. **Save and Monitor** - Check the status table for live updates

## Recommended Configurations

### Basic Home Monitoring
```
Devices:
- Motion sensors in main living areas
- Contact sensor on main entry door

Threshold: 120 minutes (2 hours)
Notifications: Mobile app notifications
Notify Once: Enabled
```

### Elderly Care Monitoring  
```
Devices:
- Motion sensors in bedroom, bathroom, kitchen
- Contact sensors on refrigerator, medicine cabinet
- Acceleration sensor on walking aid

Threshold: 60 minutes (1 hour)
Notifications: Multiple family members + care team
Notify Once: Disabled (for redundancy)
Virtual Switch: Connected to emergency alert system
```

### Pet/Child Activity Monitoring
```
Devices:
- Motion sensors in play areas
- Contact sensors on toy boxes, doors
- Acceleration sensors on favorite toys

Threshold: 180 minutes (3 hours)
Notifications: Parent mobile devices
Room Labels: "Playroom", "Bedroom", "Kitchen"
```

### Vacation/Security Monitoring
```
Devices:
- Motion sensors throughout house
- Contact sensors on all entry points
- Acceleration sensors on valuables

Threshold: 30 minutes (quick detection)
Notifications: Security service + homeowner
Virtual Switch: Trigger security cameras/lights
```

## Device Selection Tips

### Motion Sensors
- **Best for**: General activity detection
- **Place in**: High-traffic areas (hallways, living rooms)
- **Avoid**: Areas with pets if pets shouldn't count as activity
- **Consider**: Sensitivity settings and timeout periods

### Contact Sensors  
- **Best for**: Intentional interactions
- **Place on**: Doors, cabinets, appliances that indicate human activity
- **Examples**: Refrigerator, medicine cabinet, entry doors, closets
- **Note**: Only "open" events count as activity

### Acceleration Sensors
- **Best for**: Movement of specific objects
- **Place on**: Items that move when used (walking aids, chairs, tools)
- **Consider**: Sensitivity to vibrations and false triggers
- **Tip**: Test thoroughly to avoid false alarms

## Threshold Guidelines

### Factors to Consider
- **Normal routine patterns**: How long are typical inactive periods?
- **Sleep schedules**: Don't set shorter than normal sleep duration
- **Work schedules**: Account for time away from monitored areas
- **Emergency response time**: How quickly do you need to know?

### Recommended Starting Points
- **Active households**: 120-180 minutes
- **Elderly monitoring**: 60-90 minutes  
- **Security monitoring**: 30-60 minutes
- **Vacation homes**: 15-30 minutes

### Testing Your Threshold
1. Start with a longer threshold (e.g., 3 hours)
2. Monitor for false alarms over several days
3. Gradually reduce until you find the sweet spot
4. Use the Test Alarm feature to verify notifications work

## Room Label Strategy

### Benefits
- Easier identification in status table
- More informative alarm messages
- Better organization for larger homes

### Naming Conventions
- Keep names short and clear: "Kitchen", "Living Room", "Master Bath"
- Use consistent abbreviations: "BR" for bedroom, "BA" for bathroom
- Consider including floor: "2F Hallway", "Basement"
- Group related areas: "Office Area", "Kids Zone"

### Examples
```
Motion Sensor in hallway → "Main Hall"
Contact Sensor on fridge → "Kitchen"
Acceleration Sensor on walker → "Bedroom"
Motion Sensor in bathroom → "Master Bath"
```

## Notification Setup

### Device Types
- **Mobile apps**: Hubitat mobile app, Pushover, Telegram
- **Smart speakers**: Alexa, Google Home (via TTS)
- **Email services**: Gmail, SMTP
- **SMS services**: Twilio, carrier SMS

### Message Content
Messages automatically include:
- How long activity has been missing
- Your configured threshold
- Up to 3 devices with longest inactivity
- Room names (if configured)

### Anti-Spam Features
- **Notify Once**: Prevents repeated messages during same alarm
- **Re-arms automatically**: Sends new alert for next inactivity period
- **Clear notifications**: Some devices support clearing when alarm ends

## Virtual Switch Integration

### Use Cases
- **Trigger other automations**: Use switch state in Rule Machine
- **External alerting**: Connect to security systems
- **Visual indicators**: Control lights or displays
- **Logging**: Track alarm history via switch events

### Setup Tips
1. Create a virtual switch device first
2. Give it a descriptive name: "Activity Alarm Switch"
3. Use the switch state in other rules/automations
4. Remember: Switch turns ON during alarm, OFF when cleared

## Status Table Interpretation

### Status Colors
- **Green**: Recent activity detected
- **Red**: No recent activity (concerning)
- **Gray**: No data yet (new device or app restart)

### Age Column
- Shows time since last activity
- Updates every 5 minutes during checks
- Format: "2h 15m", "45m", "1d 3h"

### Troubleshooting via Status Table
- **All devices gray**: App recently restarted, wait for activity
- **One device always red**: Check device battery/connectivity
- **Unexpected activity**: Check for pets, air currents, etc.

## Testing and Validation

### Initial Setup Testing
1. **Install app** with short threshold (5 minutes)
2. **Trigger test alarm** to verify notifications
3. **Generate real activity** on each sensor type
4. **Verify status table** updates correctly
5. **Wait for threshold** to confirm alarm triggers
6. **Generate activity** to confirm alarm clears

### Ongoing Monitoring
- Check status table weekly
- Monitor notification delivery
- Adjust threshold based on false alarms
- Review device battery levels monthly

## Troubleshooting Common Issues

### No Notifications Received
1. Check notification device setup in Hubitat
2. Verify device capabilities include "notification"
3. Test notification device independently
4. Check app logs for error messages
5. Try test alarm function

### False Alarms
1. Check for pet activity if pets present
2. Verify sensor sensitivity settings
3. Consider air currents affecting motion sensors
4. Review activity patterns in status table
5. Increase threshold if needed

### Missing Activity Detection
1. Verify sensors are working independently
2. Check battery levels on wireless devices
3. Confirm activity type matches sensor type
4. Review device placement and coverage
5. Check Hubitat device event logs

### Status Table Not Updating
1. Verify app is properly installed and running
2. Check that devices are selected in app settings
3. Confirm devices are generating events in Hubitat
4. Wait 5 minutes for next automatic check
5. Try triggering manual activity

## Advanced Configuration

### Integration with Rule Machine
- Use virtual switch state for complex automations
- Combine with time restrictions
- Add additional notification channels
- Create escalation sequences

### Community Room Integration
If using community room management:
- Room labels can supplement or replace manual labels
- Consider API integration for automatic room detection
- Coordinate with existing room-based automations

### Performance Optimization
- For many devices (20+), consider splitting into multiple app instances
- Monitor hub performance during checks
- Consider reducing check frequency if needed
- Use device groups to simplify management

## Maintenance

### Regular Tasks
- **Weekly**: Review status table for anomalies
- **Monthly**: Check device batteries and connectivity
- **Quarterly**: Review and adjust thresholds
- **As needed**: Update room labels for changes

### Updates and Changes
- Always backup settings before major changes
- Test changes with short thresholds initially
- Monitor for several days after changes
- Keep change log for troubleshooting

This setup guide should help you configure the Human Activity Check app for your specific monitoring needs. Start with conservative settings and adjust based on your experience and requirements.
