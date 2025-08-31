# Changelog

All notable changes to the Human Activity Check Hubitat app will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2025-08-30

### Added
- Support for pushable buttons (`pushableButton` capability)
- Support for switches (`switch` capability) 
- Support for locks (`lock` capability)
- Sortable status table with clickable column headers
- Manual device state refresh functionality
- Enhanced notification formatting with multi-line structure
- Side-by-side button layout for improved UI
- Better room organization and display
- Comprehensive error handling and logging

### Changed
- Improved device state initialization process
- Enhanced alarm logic for better reliability
- Updated notification format to be more informative
- Fixed duration formatting to show integer values instead of decimals
- Improved table styling and responsive design
- Streamlined timeout configuration (1-120 minutes instead of 1-1440)

### Fixed
- Device state display showing "unknown" values on first load
- Alarm logic incorrectly triggering when devices had recent activity
- Duration calculations showing unnecessary decimal places
- Button layout and spacing issues in the UI
- Duplicate method definitions causing compilation errors

### Technical
- Added proper device event timestamp handling
- Improved state management for multiple device types
- Enhanced JavaScript table sorting functionality
- Better error handling for edge cases
- Optimized performance for larger device collections
- Removed "notify once" mode in favor of single alarm cycle approach

## [1.0.0] - 2025-08-29

### Added
- Initial release of Human Activity Check app
- Support for motion sensors (`motionSensor` capability)
- Support for contact sensors (`contactSensor` capability)
- Support for acceleration sensors (`accelerationSensor` capability)
- Configurable inactivity timeout (1-1440 minutes)
- Notification system with spam prevention
- Virtual switch integration for alarm state
- Room labeling for device organization
- Live HTML status table
- Test alarm functionality
- Automatic 5-minute monitoring cycle
- State persistence across hub restarts

### Features
- Multi-device monitoring with activity tracking
- Smart alarm logic requiring ALL devices to be inactive
- Detailed notifications with device information
- Customizable room assignments
- Real-time status display with timestamps
- Automatic alarm clearing when activity resumes
- Comprehensive logging for debugging

### Technical
- Proper Hubitat app structure with required methods
- Event subscription management
- State variable handling
- Scheduled execution every 5 minutes
- HTML table generation with styling
- Device capability detection and handling

## [Unreleased]

### Planned Features
- Configurable check intervals beyond 5 minutes
- Historical activity reporting and analytics
- Advanced notification escalation options
- Integration with external monitoring services
- Multi-location support for larger properties
- Advanced filtering and device grouping options

### Known Issues
- Table sorting resets when "Refresh Device States" is clicked (limitation of Hubitat's web interface)
- Fixed 5-minute check interval (not user-configurable)
- Status table refreshes only on manual refresh or page reload
