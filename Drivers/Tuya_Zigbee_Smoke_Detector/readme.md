This driver adds support in Hubitat for these Tuya smoke detectors, that use the Tuya specific non-standard Zigbee cluster 0xEF00 messages.
Currently it is being tested with only one manufacturer model "_TZE200_ntcy3xu1".

Please note, that none of these devices which are designed (not just manufactured) in China are known to have any UL, CSA, TÜV, AS/NZS or similar type certificates!

The recommended installation method is to use the community Hubitat Package Manager (HPM) app. Search for "**Tuya Zigbee Smoke Detector**" or by tag 'Zigbee'.
The driver can be also installed manually from this [Github link](https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya_Zigbee_Smoke_Detector/Tuya_Zigbee_Smoke_Detector.groovy)

## Supported models

|  Device |  Links |
|---|---|
|  Tuya Smart Smoke Detector Sensor ![image](https://user-images.githubusercontent.com/6189950/198937966-8e05ec7c-f1ad-49bb-8578-e5cc1acadc00.png) |  [AliExpress](https://www.aliexpress.com/item/1005003951429372.html) | 
| Zigbee  Smart Smoke Detector ![image](https://user-images.githubusercontent.com/6189950/198967762-5b4bf2e6-4a3b-4469-8dbe-8122d631403d.png)| AliExpress: https://www.aliexpress.com/item/1005004101962864.html|


----------------------------
## Compatibility

* Tuya specific cluster 0xEF00 Smoke Detectors
  * model:"TS0601", manufacturer:"_TZE200_ntcy3xu1"
  * model:"TS0601", manufacturer:"_TZE200_uebojraa" (not tested)
  * model:"TS0601", manufacturer:"_TZE200_t5p1vj8r" (not tested)
  * model:"TS0601", manufacturer:"_TZE200_yh7aoahi" (not tested)
  * model:"TS0601", manufacturer:"_TZE200_5d3vhjro" (not tested)
  * model:"TS0601", manufacturer:"_TZE200_aycxwiau" (not tested)
  * model:"TS0601", manufacturer:"_TZE200_vzekyi4c" (not tested)


-----------------------------------
## Features

* Standard "Smoke Detector" capability - ("detected","clear","tested") 
* Battery level capability
* Presence capability - will fire an event 'not present' if there was no communication with the sensor for more than 4 hours
* Tamper Alert" capability
* PowerSource" capability - wil fire 'powerSource unknown' event when the device goes offline (not present)
* Debig/Info logging
------------------
