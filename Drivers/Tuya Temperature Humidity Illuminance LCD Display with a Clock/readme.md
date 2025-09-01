

This driver can be installed from Hubitat Package Manager, search for "Tuya Temperature Humidity Illuminance LCD Display with a Clock" or by Tag 'Zigbee'.

Important: when requesting support for a new Tuya device, please follow this guide : [How To Identify a Zigbee Device](https://github.com/kkossev/Hubitat/wiki/Hubitat-How-To-:-Identify-a-Zigbee-Device).

The latest development branch version code is here:
https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock/Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy

## Supported models :

-----------------------------------------------------------------

|Device | Links|
|--- | ---|
|Temperature And Humidity Sensor Indoor Hygrometer Thermometer Detector With LCD Screen<br> ![image](https://user-images.githubusercontent.com/6189950/149659251-3503e3e9-237b-41e7-8c45-d8b83155f172.png) | Manufacturer : _TZE200_lve3dvpy <br><br>AliExpress: [link](https://s.click.aliexpress.com/e/_DnMyGWf) <br> Manufacturer:  TS0601 _TZE200_nnrfa68v <br>(NOUS) hubitat-shop.eu : [link](https://hubitat-shop.eu/product/nous-humidity-temperature-display/)|
|Temperature And Humidity Sensor Indoor Hygrometer Thermometer With LCD Display <br> ![image](https://user-images.githubusercontent.com/6189950/151618266-a322189e-c9ad-4d72-8b7d-9eb8164d95e9.png) | Manufacturer : _TZE200_locansqn <br> <br>AliExpress: [link](https://s.click.aliexpress.com/e/_DkFtQKX) <br> **Driver status**: basic functionalities working OK! <br> <br><b>Warning</b>: Do <b>not</b> set the temperature sensitivity below 0.5C and humidity sensitivity below 5% ! <br> <br> Update: this device can display the temperature in Fahrenheit scale (when F scale is set on your HE hub), although may require several attempts.. <br>|
|Temperature Humidity Sensor Lux Light Detector Indoor Hygrometer Thermometer With LCD Screen<br>  ![image](https://user-images.githubusercontent.com/6189950/152700124-a2ade01f-9e08-4049-814b-c67e680bd64b.png) | Manufacturer : _TZ3000_qaaysllp <br> AliExpress: [link](https://www.aliexpress.com/item/1005002401032843.html) <br><br>**Driver status**: basic functionalities working OK!|
|Temperature And Humidity Sensor With LED Screen <br> ![image](https://user-images.githubusercontent.com/6189950/150995706-1b175d63-ed00-4ae9-a361-bb5c894e9143.png) | Manufacturer :  _TYZB01_kvwjujy9 <br><br> AliExpress: [link](https://www.aliexpress.com/item/1005002549320064.htm) <br><br>|
|MOES Tuya ZigBee Smart Home Temperature And Humidity Sensor With LED Screen <br> ![image](https://user-images.githubusercontent.com/6189950/204976050-c4f9bb90-3d3e-40e5-b0d9-c3e54d924047.png) | Model:TS0201<br>Manufacturer: TZ3000_itnrsufe _TZ3000_ywagc4rj <br>Supports: battery percentage, temperature, humidity (configurable sensitivity and reporting periods)<br><br> FCC ID: [link](https://fccid.io/2A5TM-KCTW1Z/External-Photos/External-photos-5867957) <br> AliExpress: [link](https://www.aliexpress.us/item/3256804257182812.html) <br> Moes: [link](https://www.moeshouse.com/products/smart-zigbee-temperature-and-humidity-sensor-indoor-hygrometer-thermometer-detector)|
|Temperature and Humidity Sensor with LCD Display<br> ![image](https://user-images.githubusercontent.com/6189950/204976868-33c5c548-12b7-4b6b-9815-e428f5a0bea1.png) | Model:TS0601 <br> Manufactturer: TZE200_qoy0ekbd TZE200_znbl8dj5 <br>Supports: battery, temperature, humidity (not configurable)<br><br> AliExpress: [link](https://www.aliexpress.us/item/3256803873142843.html) <br>AliExpress: [link](https://www.aliexpress.us/item/3256803878221149.html) <br>|
|Tuya ZigbeeTemperature Humidity Sensor With Backlight ![image](https://user-images.githubusercontent.com/6189950/212531204-0cd25609-6914-44c0-8d41-9a14a2b8cca1.png) | Model: TS0601<br>Manufacturer: TZE200_whkgqxse <br><br>AliExpress: [link1](https://www.aliexpress.com/item/1005003980647546.html)|
|Tuya Temperature and Humidity Sensor <br> ![image](https://user-images.githubusercontent.com/6189950/212731475-321bdb9b-69d4-4f53-ac6c-2d6588b6a67a.png) | Model: TS0201 <br> Manufacturer: _TZ3000_bguser20 _TZ3000_xr3htd96 <br> Supports: battery; humidity; temperature <br><br><b>Reporting period is hardcoded to 5 minutes (even if there was a rapid change of the T/H) and can not be configured!</b><br><br> AliExpress: [link1](https://www.aliexpress.com/item/1005003401903108.html) <br> AliExpress: [link2](https://www.aliexpress.com/item/1005004744650232.html)|

|  Device |  Links |
|--------|------|
| Tuya Zigbee Temperature Humidity Soil Monitor<br> ![image](https://user-images.githubusercontent.com/6189950/210093610-e92bea4d-aac0-4c7f-8fdc-1e5789531b27.png) | <b>Caution: junk device!</b><br> Will deplete the batteries fast, spams the Zigbee network with unnecesery data every 10 seconds.<br>Fake humidity readings (100% or 70% ), and temperature updates stop after some time.<br>SmartLife app draws fake made-up graphs of the T&H readings over time.<br><br>**## DO NOT BUY ! ##**<br><br>AliExpress: [link1](https://www.aliexpress.com/item/1005004705106578.html)<br> AliExpress: [link2](https://www.aliexpress.com/item/1005004979025740.html) | 

### Non-Tuya T/H sensors

|  Device |  Links |
|--------|------|
| SONOFF SNZB-02 – Zigbee Temperature and Humidity Sensorr<br> ![image](https://user-images.githubusercontent.com/6189950/224310636-62ddf198-6f6b-4175-8523-282fc1f91604.png) | Features: Fully configurable temperature and humidity reporting periods, can report as quickly as 10 seconds and 0.1 deg. change.<br><br><b>RECOMMENDED!</b><br><br> itead .cc [(link)](https://itead.cc/product/sonoff-snzb-02-zigbee-temperature-and-humidity-sensor/ref/221/) |
| SONOFF SNZB-02D Zigbee LCD Smart Temperature Humidity Sensor <br> ![image](https://user-images.githubusercontent.com/6189950/224309433-aa845332-065b-48db-b107-0c7a2f4c5902.png) | <b>WARNING</b>: may be difficult to pair to C-7 and older hubs.<br><br>Features: C/F switch; <br>Review: Youtube [link](https://www.youtube.com/watch?v=u0nB1olg72w)<br><br> itead .cc [(link)](https://itead.cc/product/sonoff-snzb-02d-zigbee-lcd-smart-temperature-humidity-sensor/ref/221/) |


## Note

While the same driver **may** work with other Tuya temperature/humidity/illuminance models (different than these listed above), but this is not guaranteed because of the commands differences between the models and manufacturers.

## Features

Currently, not all of the functionalities and settings that are available from Tuya SmartLife app for the specific model are implemented into this HE driver.

The basic functions that are working at the moment are:

* Synchronizes the sensor clock to HE hub time and day of the week.
* Reports the sensor temperature (0.1 C resolution), as frequently as sent by the device.
* Reports the sensor humidity (1% RH resolution),  as frequently as sent by the device.
* Reports battery level (%)
* Dynamic parameters configuration, depending on the device model:
  * Auto-detect or force Celsius/Fahrenheit scale setting
  * Temperature Sensitivity setting
  * Humidity Sensitivity setting
  * Illuminance Sensitivity setting
  * Minimum and Maximum Temperature Alarm settings
  * Minimum and Maximum Humidity Alarm settings
  * Minimum and Maximum Temperature and Humidity reporting interval settings

* Extended debug and info logging


------


### Revisions history:

* ver. 1.0.0 2022-01-25 - Initial test version
* ver. 1.0.2 2022-02-06 - Tuya commands refactoring; TS0222 T/H poll on illuminance change (EP2); modelGroupPreference bug fix; dynamic parameters depending on the model; Tuya commands refactoring; TS0222 T/H poll on illuminance change (EP2); modelGroupPreference bug fix; dyncamic parameters
 * ver. 1.0.3 2022-02-13 -  _TZE200_c7emyjom fingerprint added; 
 * ver. 1.0.4 2022-02-20 - Celsius/Fahrenheit correction for TS0601_Tuya devices
 * ver. 1.0.5 2022-04-25  - added TS0601_AUBESS (illuminance only); ModelGroup is shown in State Variables
 * ver. 1.0.6 2022-05-09  - new model 'TS0201_LCZ030' (_TZ3000_qaaysllp)
 * ver. 1.0.7 2022-06-09 - new model 'TS0601_Contact'(_TZE200_pay2byax); illuminance unit changed to 'lx;  Bug fix - all settings were reset back in to the defaults on hub reboot
* ver. 1.0.8 2022-08-08  
  * _TZE200_pay2byax contact state and battery reporting fixes;  
  * removed degrees symbol from the logs; 
  *  removed temperatureScaleParameter ( the driver uses the HE hub system C/F scale setting)
  * humiditySensitivity and temperatureSensitivity bug fixes;
  * '_TZE200_locansqn' (TS0601_Haozee) bug fixes
  * decimal/number parameters types bug fixes;
  * added temperature and humidity offsets; 
  * configured parameters (including C/F HE scale) are sent to the device when paired again to HE;
  *  added Minimum time between temperature and humidity reports;
* ver. 1.0.9 2022-10-02  - configure _TZ2000_a476raq2 reporting time; added TS0601 _TZE200_bjawzodf; code cleanup
* ver. 1.0.10 2022-10-11 - _TZ3000_itnrsufe' reporting configuration bug fix?; reporting configuration result Info log; added Sonoff SNZB-02 fingerprint; reportingConfguration is sent on pairing to HE;
* ver. 1.0.11 2022-11-17  - added _TZE200_whkgqxse; fingerprint correction; _TZ3000_bguser20 _TZ3000_fllyghyj _TZ3000_yd2e749y _TZ3000_6uzkisv2
* ver. 1.1.0  2022-12-18  : 
  *  added _info_ attribute; 
  * delayed reporting configuration when the sleepy device wakes up; 
  * excluded TS0201 model devices in the delayed configuration (TS0201 **_TH** are configurable!)
  * _TZE200_locansqn fingerprint correction and max reporting periods formula correction
  *  -  added TS0601_Soil _TZE200_myd45weu ; added _TZE200_znbl8dj5 _TZE200_a8sdabtg _TZE200_qoy0ekbd
* ver. 1.1.1  2023-01-14 - added _TZ3000_ywagc4rj TS0201_TH; bug fix: negative temperatures not calculated correctly;
* ver. 1.2.0  2023-01-15  - parsing multiple DP received in one command;
* ver. 1.2.1  2023-01-15 - _TZE200_locansqn fixes;_TZ3000_bguser20 correct model;
* ver. 1.3.0  2023-02-02- [healthStatus](https://community.hubitat.com/t/devicepresent-capability/89774/18?u=kkossev); added capability 'Health Check'
 * ver. 1.3.1  2023-02-10 - added RH3052 TUYATEC-gqhxixyk ; 
 * ver. 1.3.2  2023-03-04 - added TS0601 _TZE200_zl1kmjqx _TZE200_qyflbnbj, added TS0201 _TZ3000_dowj6gyi and _TZ3000_8ybe88nf
 * ver. 1.3.3  2023-04-23 - _TZE200_znbl8dj5 inClusters correction; ignored invalid humidity values; implemented ping() and rtt (round-trip-time) attribute;
* ver. 1.3.4  2023-04-24 - send rtt 'timeout' if ping() fails; added resetStats command; added individual stat.stats counters for T/H/I/battery; configuration possible loop bug fix; 
 * ver. 1.3.5  2023-05-28 - sendRttEvent exception fixed; added _TZE200_cirvgep4 in TS0601_Tuya group; fingerprint correction; battery reports are capped to 100% and not ignored;
*  ver. 1.3.6  2023-06-10 added _TZE200_yjjdcqsq to TS0601_Tuya group; 
 * ver. 1.3.7  2023-08-02 vpjuslin -Yet another name for Tuya soil sensor: _TZE200_ga1maeof
 * ver. 1.3.8  2023-08-17 - added OWON THS317-ET for tests; added TS0201 _TZ3000_rdhukkmi; added TS0222 _TYZB01_ftdkanlj
 * ver. 1.3.9  2023-09-29 - added Sonoff SNZB-02P; added TS0201 _TZ3210_ncw88jfq; moved _TZE200_yjjdcqsq and _TZE200_cirvgep4 to a new group 'TS0601_Tuya_2'; added _TZE204_upagmta9, added battery state 'low', 'medium', 'high'
 * ver. 1.3.10 2023-11-28 -  added TS0222 _TYZB01_fi5yftwv; added temperature scale (C/F) and temperature sensitivity setting for TS0601_Tuya_2 group;
 * ver. 1.4.0  2023-11-28 -  bug fix - healthStatus periodic job was not started; _TZ3000_qaaysllp illuminance dp added;
 * ver. 1.5.0  2024-01-18 - Groovy lint; added TS0601 _TZE200_vvmbj46n to TS0601_Tuya_2 group; _TZE200_qyflbnbj fingerprint correction;
 * ver. 1.5.1  2024-02-13 - bugfix: battery reporting period for non-Tuya devices.
 * ver. 1.5.2  2024-05-14 - added _TZE204_upagmta9 and _TZE200_upagmta9 to TS0601_Tuya_2 group; healthStatus initialized as 'unknown'; 
 * ver. 1.6.0  2024-05-19 - added the correct NOUS TS0601 _TZE200_nnrfa68v fingerprint to group 'TS0601_Tuya'; all Current States and Preferences are cleared on initialize command;
ver. 1.6.1  2024-06-10 -  added ThirdReality 3RTHS0224Z and 3RTHS24BZ
 * ver. 1.6.2  2024-06-26 - added TS000F _TZ3218_7fiyo3kv in DS18B20 group (temperature only); added Tuya cluster command '06' processing; added description in the debug logs
 * ver. 1.6.3  2024-07-16 - added TS0601 _TZE204_yjjdcqsq to TS0601_Tuya_2 group;
 * ver. 1.6.4  2024-07-23 - added Tuya Smart Soil Tester _TZE284_aao3yzhs into 'TS0601_Soil_II'
 * ver. 1.6.5  2024-08-09 - bugfix: TS0201 _TZ3000_dowj6gyi moved back to TS0201 group;
 * ver. 1.6.6  2024-08-14 - added TS0601 _TZE204_myd45weu; added TS0601 _TZE204_qyflbnbj
 * ver. 1.6.7  2024-10-23 - added TS0222 _TZ3000_kky16aay in a new 'TS0222_Soil' group
 * ver. 1.6.8  2024-11-19 - added TS0601 _TZE284_sgabhwa6 and _TZE284_nhgdf6qr into 'TS0601_Soil_II'; added _TZE200_qrztc3ev _TZE200_snloy4rw _TZE200_eanjj2pa _TZE200_ydrdfkim into 'TS0601_Tuya' group
 * ver. 1.7.0  2024-11-23 -  temperatureOffset and humidityOffset moved outside of the configParams; added queryAllTuyaDPs() on Refresh
 * ver. 1.8.0  2024-12-30 - HE platform 2.4.0.x compatibility patch
 * ver. 1.8.1  2025-02-22 - (dev. branch) added TS000F _TZ3218_ya5d6wth in DS18B20 group (temperature only);


The latest development version is available from the development branch on GitHub: https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock/Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy



-----

 *                                  TODO: add all configurable parameters for _TZE204_s139roas
 *                                  TODO: queryOnDeviceAnnounce for TS0601_Tuya_2 group
 *                                  TODO: TS0601 _TZE200_vvmbj46n - preferences changes are not accepted by the device!; add temperature and humidity max reporting interval settings for TS0601_Tuya_2 group;
 *                                  TODO: add TS0601 _TZE200_khx7nnka in a new TUYA_LIGHT device profile : https://community.hubitat.com/t/simple-smart-light-sensor/110341/16?u=kkossev @Pradeep
 *                                  TODO: _TZ3000_qaaysllp frequent illuminance reports - check configuration; add minimum time between lux reports parameter!
 *                                  TODO:  TS0201 - bindings are sent, even if nothing to configure?
 *                                  TODO: add Batteryreporting time configuration (like in the TS004F driver)
*