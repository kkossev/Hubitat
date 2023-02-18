# Tuya Zigbee Contact Sensor++ w/ healthStatus driver for Hubitat

Most of the Zigbee contact sensors on the market (including Tuya TS0203) that use the standard IAS zone messaging are natively supported in Hubitat by the inbuilt **Generic Zigbee Contact Sensor** driver. This custom  driver provides support for the non-standard Tuya-specific contact sensors (TS0601), but works also with IAS sensors made by Tuya (TS0203) and most of the other manufacturers, adding [device healthStatus](https://community.hubitat.com/t/project-alpha-device-health-status/111817) (online/offline) monitoring. It also allows battery reporting configuration for some of the models.

The recommended way to install the driver is from Hubitat Package Manager ([HPM](https://community.hubitat.com/t/beta-hubitat-package-manager/38016)). Search for "Tuya Zigbee Contact Sensor++" or by tag "Zigbee". If you have already installed this driver manually, do a "Match Up" in HPM first and then Update.

Driver code is also availabe in Github: [link](https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Contact%20Sensor/Tuya%20Contact%20Sensor.groovy)
 
 
------------------------
## Supported Tuya models
|  Device |  Links |
|---|---|
| Tuya Contact and Temperature/Humidity Sensor <br> ![image](https://user-images.githubusercontent.com/6189950/219631200-fc858613-788b-45f3-b0a7-2a729a05887a.png)  |Model: TS0601<br>Manufacturer: TZE200_nvups4nh <br>Features: Contact, temperature, humidity, battery<br>Battery: TODO<br>Driver status: operational<br><br> AliExpress: [link](https://www.aliexpress.us/item/3256804692294345.html) <br> |
| Tuya Contact and Illuminance Sensor<br> ![image](https://user-images.githubusercontent.com/6189950/219630061-b9ecc3bd-3a07-45b0-a2fb-78349cf3c42e.png) |Model: TS0601<br>Manufacturer: TZE200_pay2byax, TZE200_n8dljorx<br>Features: Contact, illuminance, battery<br>Battery: <br><br>Driver status: <b>work-in-progress</b><br> Links: TODO |
| Tuya ContactSensor <br> ![image](https://user-images.githubusercontent.com/6189950/219629284-80dd012f-25b8-406e-ab31-9ab0094dcf6d.png)| Model: TS0203<br>Manufacturers: "_TZ3000_26fmupbb",  "_TZ3000_n2egfsli", "_TZ3000_oxslv1c9", "_TZ3000_2mbfxlzr", "_TZ3000_402jjyro",  "_TZ3000_7d8yme6f", "_TZ3000_psqjayrd", "_TZ3000_ebar6ljy", "_TYZB01_xph99wvr", "_TYZB01_ncdapbwy", "_TZ3000_fab7r7mc", "TUYATEC-nznq0233" <br>Features: Contact, illuminance, battery<br>Battery: <br><br> Links: TODO|

--------------------------

#### Supported non-Tuya models
|  Device |  Links |
|---|---|
| BlitzWolf Contact Sensor <br> ![image](https://user-images.githubusercontent.com/6189950/219621518-0a209cda-bc2d-45cf-8b7e-3be3a36b5841.png) |Model: RH3001<br>Manufacturer: TUYATEC-0l6xaqmi, TUYATEC-trhrga6p, TUYATEC-nznq0233 <br>Features:Contact, battery<br><br> BlitzWolf .com [link](https://www.blitzwolf.com/BlitzWolf--BW-IS2-Zigbee-Smart-Home-Door-and-Window-Sensor-Open-or-Close-APP-Remote-Alarm-p-1604217.html) |
| SONOFF SNZB-04 Contact Sensor <br> ![image](https://user-images.githubusercontent.com/6189950/219620331-ef1c13c8-5e55-47cb-9cda-6848701099a3.png)|Model: DS01<br>Manufacturer: eWeLink<br>Features: Contact, battery<br>Battery: <br><br> Sonoff tech: [link](https://sonoff.tech/product/gateway-and-sensors/snzb-04/)|
| Third Reality Contact Sensor <br>![image](https://user-images.githubusercontent.com/6189950/219615608-f7c282c5-bf32-4309-90fd-6e66a1453561.png) |Model: 3RDS17BZ: <br> Manufacturer: Third Reality, Inc<br>Features: Contact, battery<br>Battery: 2xAAA<br><br>3reality .com: [link](https://www.3reality.com/)<br>Amazon .com : [link](https://www.amazon.com/THIRDREALITY-Contact-Security-Required-SmartThings/dp/B08R9PH4JT?th=1) <br> Amazon.co .uk : [link](https://www.amazon.co.uk/THIRDREALITY-Contact-Security-Required-SmartThings/dp/B08R9PH4JT)<br>Amazon .de : [link](https://www.amazon.de/-/en/THIRDREALITY-Contact-Security-Required-SmartThings/dp/B08R9PH4JT) |

---------------------------
## Compatibility
* TS0601 Tuya-specific cluster models
* TS0203 IAS cluster models (work with HE inbuilt drivers too!)
* RH3001 BlitzWolf models
* Sonoff DS01 
* Third Reality 3RDS17BZ (under tests)
* Any Zigbee standard IAS Cluster (0x0500) contact sensors


-------------------------

## Features
* Multiple manufacturers and models support
* healthStatus (online/offline)
* Battery reporting interval configuration
* Info and Debug logging preferencies

-------------------------

## REVISIONS HISTORY:
* ver. 1.0.0 2023-02-12 - Initial test version
* ver. 1.0.1 2023-02-15 - dynamic Preferences, depending on the device Profile; setDeviceName bug fixed; added BlitzWolf RH3001; _TZE200_nvups4nh fingerprint correction; healthStatus timer started; presenceCountDefaultThreshold bug fix;
* ver. 1.0.2 2023-02-17 kkossev - healthCheck is scheduled every 1 hour; added presenceCountThreshold option (default 12 hours); healthStatus is cleared when disabled or set to 'unknown' when enabled back; offlineThreshold bug fix ;added Third Reality 3RDS17BZ

The development branch version that contains the latest additions and bug fixes can be manually downloaded from here: [link](https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Contact%20Sensor/Tuya%20Contact%20Sensor.groovy)

----------------------

Reserved:  TODO
