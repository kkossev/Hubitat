# Tuya Zigbee Contact Sensor++ w/ healthStatus driver for Hubitat

The recommended way to install the driver is Hubitat Package Manager ([HPM](https://community.hubitat.com/t/beta-hubitat-package-manager/38016)). Search for "Tuya Scene Switch TS004F" or by tag "Zigbee".

Driver code is availabe in Github: [https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20TS004F/TS004F.groovy](https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Contact%20Sensor/Tuya%20Contact%20Sensor.groovy)
 
 
------------------------
## Supported Tuya models
|  Device |  Links |
|---|---|
| Tuya Contact and Temperature/Humidity Sensor <br>  |Model: TS0601<br>Manufacturer: TZE200_nvups4nh <br>Features:Contact, temperature, humidity, battery<br><br>  |
| Tuya Contact and Illuminance Sensor<br>  |Model: TS0601<br>Manufacturer: TZE200_pay2byax, TZE200_n8dljorx<br>Features:Contact, illuminance, battery<br><br> |
| Tuya ContactSensor| Model: TS0203<br>Manufacturer: <br>Features:Contact, illuminance, battery<br><br> |
--------------------------

#### Supported non-Tuya models
|  Device |  Links |
|---|---|
| BlitzWolf Contact Sensor <br>  |Model: RH3001<br>Manufacturer: TUYATEC-0l6xaqmi, TUYATEC-trhrga6p, TUYATEC-nznq0233 <br>Features:Contact, battery<br><br>  |


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

The development branch version that contains the latest additions and bug fixes can be manually downloaded from here: [https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20TS004F/TS004F.groovy](https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Contact%20Sensor/Tuya%20Contact%20Sensor.groovy)

----------------------

Reserved:  TODO
