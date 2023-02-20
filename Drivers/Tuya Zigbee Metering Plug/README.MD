This is a new topic on the **Tuya Zigbee Metering Plug** driver that was first published in November 2021.

The focus of the driver is to provide an optimized way for **reporting**  Power,  Energy,  Voltage, Amperage, and switch events (where available) for these Tuya plugs/outlets models that do not report the metering attributes automatically.

The driver is available for download from Hubitat Package Manager (HPM), search for 'Tuya Zigbee Metering Plug'.  The direct link to the code in GitHub repository is [here](https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Zigbee%20Metering%20Plug/Tuya%20Zigbee%20Metering%20Plug) .

## Supported Tuya platform devices:

|  Device |  Links |
|---|---|
| Tuya TS0121 (BlitzWolf) <br> ![image](https://user-images.githubusercontent.com/6189950/147770858-9dbc5b28-7e06-48ae-ab98-8a9c3477dd48.png) |  (AE [link](https://www.aliexpress.com/store/group/Zigbee-smart-socket/4685106_10000001855269.html?spm=a2g0o.detail.100008.16.77b67a1e7L07Tx)) |
| Tuya TS011F (many brands) <br> ![image](https://user-images.githubusercontent.com/6189950/147770322-e42d4f3f-47ff-4a21-9e90-3f3a98ba7241.png) | <br><br>Review:[(link)](https://investio.pro/tuya-smart-zigbee/) <br> AliExpress Haozoe: [(link)](https://www.aliexpress.com/item/1005002344798281.html) <br> | 
| NEO ZigBee On Off Power Metering Plug <br> ![image](https://user-images.githubusercontent.com/6189950/189312590-ed18639c-fc4a-4d7e-b51e-2fcb1e47c922.png)  | Ultrasmart.pl : [(link)](https://ultrasmart.pl/en_GB/p/NEO-ZigBee-On-Off-Power-Metering-Plug/81) | 
Lellki WK35 Plug Wall Socket EU BR IT JP FR IL <br> ![image](https://user-images.githubusercontent.com/6189950/167282480-825ad7cc-64fe-4bf9-b635-7168ee05690f.png) |  <br> AliExpress: [(link)](https://www.aliexpress.com/item/4001243518512.html) <br> |
| 20A Tuya Zigbee Smart Plug Mini US Power Outlet <br> ![image](https://user-images.githubusercontent.com/6189950/170115134-3b8368a4-3098-46aa-93b3-9b3ee2df5d98.png) | AliExpress: [[link](https://www.aliexpress.com/item/1005004128965720.html)] <br> |
| Ajax / Zignito Zigbee Plug 13Amps with Energy Monitoring <br> ![image](https://user-images.githubusercontent.com/6189950/189325364-856c3d3d-e4ed-4ca7-a288-b02a5d09ca18.png) |Amazon:[(link)]( https://www.amazon.co.uk/dp/B09N43BBC1?ref_=cm_sw_r_cp_ud_dp_YPHV2YS437AY5BXFVMSV) |
| Smart ZigBee Socket NOUS A1Z <br> ![image](https://user-images.githubusercontent.com/6189950/170118989-46f0757f-3afa-4d53-8096-25333b97b7f1.png)  | Nous: [(link)](https://shop.smarthome-europe.com/en/peripheriques/5261-nous-zigbee-30-smart-plug-consumption-metering-5907772033517.html) |
| Tuya Smart Zigbee Plug AU 16A <br> ![image](https://user-images.githubusercontent.com/6189950/189305196-b51cd908-a341-4553-a250-0a92dda71c52.png) | AliExpress: [(link)](https://www.aliexpress.com/item/1005004505868292.html) <br>   |
| Plugue Zigbee 16a brasil br tomada de energia <br> ![image](https://user-images.githubusercontent.com/6189950/189314116-6600399f-3437-4376-9edd-4cb81fa1220e.png) | AliExpress:[(link)](https://pt.aliexpress.com/item/1005004019211620.html) <br> AliExpress: [(link)](https://pt.aliexpress.com/item/1005003059126398.html)| 
| Lellki Smart Wall Socket 220v <br> ![image](https://user-images.githubusercontent.com/6189950/189319812-375f24a8-829d-4ae5-9ffe-d1e6c1ce0975.png) | Alixpress:  [(link)](https://www.aliexpress.com/item/1005003642268555.html)|
 | DIN rail metering switch <br> ![image](https://user-images.githubusercontent.com/6189950/189324623-fed488ca-2c3c-42a4-9dd9-0fb979e937dc.png) | AliExpress: [(link)](https://www.aliexpress.com/item/1005002983983361.html) |
| Tuya TS0601 (TOMZN DDS238-2) <br>  ![image](https://user-images.githubusercontent.com/6189950/147771140-a29c2401-9cd8-4e80-a777-93e0c5e183be.png) | (AE [link](https://www.aliexpress.com/item/1005002409588154.html)) <br><br>Review: [(link)](https://investio.pro/zigbee-gateway-tuya-single-phase/) |

--------------------------------------------------------------------

Other metering plugs that send energy measurement reports on Tuya cluster 0xEF00 or on the standard 0x0702 and 0x0B04 clusters.
## Supported non-Tuya devices:

|  Device |  Links |
|---|---|
|frient Electricity Meter Interface <br> ![image](https://user-images.githubusercontent.com/6189950/204120069-14db6023-5179-4ac0-8bc4-e3eff7258872.png) | [frient :]( https://frient.com/products/electricity-meter-interface/) <br> [vesternet: ](https://www.vesternet.com/en-eu/products/zigbee-frient-electricity-meter-interface) <br> [smartify.in : ](https://smartify.in/shop/switching/relays/energy-meter-modules/frient-electricity-meter-interface/) |


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
