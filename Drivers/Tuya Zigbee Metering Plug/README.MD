This is a new topic on the **Tuya Zigbee Metering Plug** driver that was first published about 3 months ago.

The focus of the driver is to provide an optimized way for **reporting**  Power,  Energy,  Voltage, Amperage, and switch events (where available) for these Tuya plugs/outlets models that do not report the metering attributes automatically.

The driver is available for download from Hubitat Package Manager (HPM), search for 'Tuya Zigbee Metering Plug'.  The direct link to the code in GitHub repository is [here](https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Zigbee%20Metering%20Plug/Tuya%20Zigbee%20Metering%20Plug) .

## Supported devices:

Tuya TS0121 (BlitzWolf) (AE [link](https://www.aliexpress.com/store/group/Zigbee-smart-socket/4685106_10000001855269.html?spm=a2g0o.detail.100008.16.77b67a1e7L07Tx))
![image](https://user-images.githubusercontent.com/6189950/147770858-9dbc5b28-7e06-48ae-ab98-8a9c3477dd48.png)

-----------------------------------------------------------------

Tuya TS011F (many brands)
![image](https://user-images.githubusercontent.com/6189950/147770322-e42d4f3f-47ff-4a21-9e90-3f3a98ba7241.png)

----------------------------------------------------------------

Tuya TS0601 (TOMZN DDS238-2) - (AE [link](https://www.aliexpress.com/item/1005002409588154.html))
![image](https://user-images.githubusercontent.com/6189950/147771140-a29c2401-9cd8-4e80-a777-93e0c5e183be.png)

----------------------------------------------------------------
**COMING SOON!** : 

Tuya RC-RCBO / HOCH Automatic Circuit Breaker w/ energy monitoring [(link)](https://www.aliexpress.com/item/1005002976735485.html)
Manufacturer: **_TZE200_hkdl5fmv**
![image|359x421](upload://6hYD4N0kasjVsPvP9Bd2SL9nUyE.jpeg)


--------------------------------------------------------------------

Other metering plugs that send energy measurement reports on Tuya cluster 0xEF00 or on the standard 0x0702 and 0x0B04 clusters.

## Features:
- Supports Tuya smart plugs models TS0121 (BlitzWolf), TS011F (many brands), TS0601 TOMZN DDS-238
- Automatic polling for models that do not send power reports automatically (TS0121 and TS011F)
- Configurable polling interval (default is 60 seconds)
- Updates the new power/amperage readings in 5 seconds after switching on/off.
- Debouncing functionality for some models that tend to send the switch status updates twice
- 'digital' or 'physical' property in the switch events
- 'Always On' option prevents accidental switching off the power supply of the entire house! :) 
- 'Presence' capability simulation - will send a 'not present' event if nothing is received from the plug in 3 consecutive poll cycles
- Extended Debug logging (useful for tracking issues with unknown new models). Will be switched off automatically in 30 minutes.
- Extended Info logging (when changing parameters or other important and useful information must be logged)
- Automatically disables the polling for power, voltage, amperage or energy if the smart plug does not support this attribute.
- Optimized polling of Power, Voltage, and Amperage attributes in one single command (reduces the Zigbee network load)

### Revisions history:

 ver. 1.0.0 2021-11-09 - first version: - reads Power, Energy, Voltage, Amperage once every 60 seconds
 ver. 1.0.1 2021-11-10 - added 'pollingInterval' preference; 'amperage' attribute name bug fix; 
 ver. 1.1.0 2021-11-12 - added 'PresenceSensor' capability; the automatic polling can be switched off.
 ver. 1.1.1 2021-11-25 - added Tuya Outlet TS011F fingerprint
 ver. 1.1.2 2021-12-24 - added Tuya / Neo NAS-WR01 fingerprint; fingerprint inClusters correction
 ver. 1.2.0 2021-12-29 - major refactoring and optimizations
 ver. 1.2.1 2021-12-29 - added AlwaysOn option
 ver. 1.3.0 2022-01-01 - added 'HIKING TOMZN DDS238-2 TS0601 (DIN rail)' support'; 'Optimizations' option.
ver. 1.4.0 2022-01-23  - debug  & trace logging cleanup; initialize switch and energy automatic reporting mode; energy and switch are excluded from polling;   default debug logging is disabled, optimizations are enabled by default; switch digital/physical bug fixed; added driver version check
