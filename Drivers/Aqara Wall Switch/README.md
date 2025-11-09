# Aqara Wall Switch Driver for Hubitat Elevation

This driver has been ported from SmartThings to Hubitat Elevation to support various Aqara wall switch models.

## Supported Models

- **Single Switch Models:**
  - QBKG04LM (lumi.ctrl_neutral1)
  - QBKG11LM (lumi.ctrl_ln1.aq1)
  - QBKG21LM (lumi.switch.b1lacn02)
  - QBKG23LM (lumi.switch.b1nacn02)
  - QBKG38LM (lumi.switch.b1lc04)
  - QBKG40LM (lumi.switch.b1nc01)
  - WS-USC01 (lumi.switch.b1laus01)
  - EU-01 (lumi.switch.l1aeu1)

- **Dual Switch Models:**
  - QBKG03LM (lumi.ctrl_neutral2)
  - QBKG12LM (lumi.ctrl_ln2.aq1)
  - QBKG22LM (lumi.switch.b2lacn02)
  - QBKG24LM (lumi.switch.b2nacn02)
  - QBKG39LM (lumi.switch.b2lc04)
  - QBKG41LM (lumi.switch.b2nc01)
  - WS-USC02 (lumi.switch.b2laus01)
  - EU-02 (lumi.switch.l2aeu1)

- **Triple Switch Models:**
  - QBKG25LM (lumi.switch.l3acn3)
  - QBKG26LM (lumi.switch.n3acn3)

- **Wireless Button Models:**
  - WXKG03LM (lumi.remote.b186acn01)
  - WXKG06LM (lumi.remote.b186acn02)
  - WXKG02LM (lumi.remote.b286acn01)
  - WXKG07LM (lumi.remote.b286acn02)

## Installation

1. Install both drivers in Hubitat:
   - Main driver: `aqara-wall-switch.groovy`
   - Child driver: `aqara-wall-switch-child.groovy` (required for multi-switch models)

2. Join your Aqara switch to the Hubitat hub using the standard Zigbee pairing process

3. The driver should be automatically selected based on the device fingerprint

## Features

- **Switch Control**: Standard on/off functionality
- **Button Events**: Push, hold, and double-click detection
- **Temperature Monitoring**: Built-in temperature sensor reporting
- **Power Monitoring**: Instantaneous power and energy consumption (where supported)
- **Battery Monitoring**: Battery level and voltage reporting (for wireless models)
- **Decoupled Mode**: Allows physical switch to operate independently from relay
- **Unwired Mode**: Support for switches without load wire connection

## Configuration Options

- **Unwired Switch**: Enable if the switch is not wired to control a load
- **Decoupled Mode**: Enable to decouple physical button from relay operation
- **Temperature Offset**: Adjust temperature readings by a fixed offset
- **Logging Options**: Enable info and debug logging as needed

## Changes from SmartThings Version

### Major Changes:
- Updated import statements for Hubitat compatibility
- Removed SmartThings-specific metadata (mnmn, vid, ocfDeviceType)
- Updated capability names (Health Check → HealthCheck, etc.)
- Replaced response() wrappers with direct command returns
- Updated child device creation for Hubitat's component model
- Removed SmartThings-specific UI elements
- Updated time zone and temperature scale handling
- Replaced displayDebugLog/displayInfoLog with standard Hubitat logging

### Technical Improvements:
- Added singleThreaded: true for better stability
- Updated preference definitions with proper defaults
- Improved error handling and logging
- Streamlined event generation
- Better state management

## Usage Notes

1. **Multi-Switch Models**: Child devices are automatically created for switches 2 and 3
2. **Button Events**: Supports pushed, held, and double events
3. **Decoupled Mode**: Useful for scene control while maintaining switch functionality
4. **Temperature Reporting**: Updates every 30 minutes automatically
5. **Power Monitoring**: Available on newer models with neutral wire

## Troubleshooting

- Enable debug logging to see detailed device communication
- Ensure proper Zigbee mesh connectivity for reliable operation
- Some features may not be available on all models
- Refer to device manual for specific model capabilities

## Credits

- Original SmartThings driver by @aonghus-mor
- Hubitat port by @kkossev & Claude Sonnet 4
- Based on work by a4refillpad, @dschich, @Chiu, and @mwtay84

## Version History

- v. 2.0.0 (10/19/2025) - Initial Hubitat port by kkossev & Claude Sonnet 4