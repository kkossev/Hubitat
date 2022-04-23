Although the different Tuya branded Zigbee valves may look the same, they are produced by different manufacturers and have different Zigbee commands implemented.
Most of the Tuya valves should work in HE stright away, using the inbuilt **Sinope Water Valve** driver.

In the cases when Sinope Water Valve driver does not work for your model, you can try this driver that is intended to provide support for several different Tuya modles.

The recommended installation method is to use the community Hubitat Package Manager (HPM) app. Search for "**Tuya Zigbee Valve**" or by tag 'Zigbee'.
The driver can be also installed manually from this link : https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Valve/Tuya%20Zigbee%20Valve.groovy

## Supported models

|  Device |  Links |
|---|---|
|  Tuya ZigBee 3.0 Smart Gas Water Valve Controller  ![image](https://user-images.githubusercontent.com/6189950/164885487-70778b88-cb9c-444e-a9ec-5a1ce0fa8334.png) | AliExpress: https://www.aliexpress.com/item/4000334424893.html <br> AliExpress: https://www.aliexpress.com/item/1005002633228786.html | 
| Tuya Water Gas Shut Off Valve  ![image](https://user-images.githubusercontent.com/6189950/164885860-b081306d-af46-4d37-8f79-7dba509d6e92.png) | Amazon.de: https://www.amazon.de/-/en/Intelligent-Control-Compatible-Assistant-Kitchen/dp/B097F3GWTN  |

----------------------------
## Compatibility

* Tuya specific cluster 0xEF00 valves
  * model:"TS0601", manufacturer:"_TZE200_vrjkcam9"
* Valves that need Tuya specific initialization
  * model:"TS0001", manufacturer:"_TZ3000_iedbgyxt" 
* Standard Zigbee 3.0 (ZHA 1.2) valves
  * various models 
-----------------------------------
## Features

* Standard "Valve" capability
* Debig/Info logging
* more coming soon...
------------------
