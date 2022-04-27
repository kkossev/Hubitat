# Tuya Scene Switch TS004F driver

The recommended way to install the driver is Hubitat Package Manager ([HPM](https://community.hubitat.com/t/beta-hubitat-package-manager/38016)). Search for "Tuya Scene Switch TS004F" or by tag "Zigbee".
 Driver code is availabe in Github: https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20TS004F/TS004F.groovy
 
 **Important**: Due to the Tuya specific initialization that these devices require, these scene switches must be obligatory re-paired to HE hub in order to work properly.
 
------------------------
## Supported models
|  Device |  Links |
|---|---|
| Tuya Scene/Dimmer Switch TS004F <br> ![image](https://user-images.githubusercontent.com/6189950/154431871-34bc7f0c-795d-48b6-a7b6-3bcb7e492e0c.png)| Brands: Zemismart, Yagusmart, Moes and many others.<br> Moes: [(link)](https://www.moeshouse.com/collections/special-offer-1/products/1-4-gang-tuya-zigbee-wireless-12-scene-switch-mechanical-push-button-controller-battery-powered-smart-home-automation-scenario-switch-for-tuya-smart-devices?variant=32686456963153) <br> Amazon.de: [(link)](https://www.amazon.de/-/en/Wireless-Switches-Controller-Automation-Scenarios/dp/B091HPX159) <br>Amazon.co.uk: [(link)](https://www.amazon.co.uk/gp/product/B08J3TMGJH) <br> AliExpress: [(link)](https://it.aliexpress.com/item/1005001504737652.html) |
| Tuya Remote Control TS004F <br> ![image](https://user-images.githubusercontent.com/6189950/155196487-0ff66665-510b-4fe7-984b-a21910dea4aa.png) | Amazon.com: [link](https://www.amazon.com/Wireless-Portable-Required-Automation-Scenario/dp/B093SLYJSV)<br> Amazon.de: [(link)]( https://www.amazon.de/-/en/Wireless-Portable-Required-Automation-Scenario/dp/B098L5XSD6) (model TS0044)<br> |
| Tuya ZigBee Smart Knob <br> ![image](https://user-images.githubusercontent.com/6189950/164965971-970bbe7a-eda6-4f22-a66d-ebc7562cdd8e.png)| AliExpress: [(link)](https://www.aliexpress.com/item/1005003376291224.html) <br> |

---------------------------
## Compatibility
* TS004F models
* TS0044 models (Moes Remote)
* TS0044 models (it is recommended to use the HE inbuilt 'Tuya Scene Switch' driver)

**Note**: due to non-standard Tuya implementation of the dimming functinality that is incompatible with HE, only the 'Scene Control' mode is supported in Hubitat!


-------------------------

## Features
* Single, Double, Hold keypress events
* Reverse Buttons ordering option
* Battery reporting
* Info and Debug logging preferencies

-------------------------

## REVISIONS HISTORY:
* ver. 1.0.0 2021-05-08  - SmartThings version 
* ver. 2.0.0 2021-10-03  - First version for Hubitat in 'Scene Control'mode - AFTER PAIRING FIRST to Tuya Zigbee gateway!
* ver. 2.1.0 2021-10-20  - typos fixed; button wrong event names bug fixed; extended debug logging; added experimental switchToDimmerMode command
* ver. 2.1.1 2021-10-20  - numberOfButtons event bug fix; 
* ver. 2.2.0 2021-10-20  - First succesfuly working version with HE!
* ver. 2.2.1 2021-10-23  - added "Reverse button order" preference option
* ver. 2.2.2 2021-11-17  - added battery reporting capability; added buttons handlers for use in Hubutat Dashboards; code cleanup
* ver. 2.2.3 2021-12-01  - added fingerprint for Tuya Remote _TZ3000_pcqjmcud
* ver. 2.2.4 2021-12-05  - added support for 'YSR-MINI-Z Remote TS004F'
* ver. 2.3.0 2022-02-13  - added support for 'Tuya Smart Knob TS004F'
* ver. 2.4.0 2022-03-31  - added support for 'MOES remote TS0044', singleThreaded: true; bug fix: debouncing timer was not started for TS0044
* ver. 2.4.1 2022-04-23 - improved tracing of debouncing logic code; option [overwrite: true] is set explicitely on debouncing timer restart; debounce timer increased to 1000ms  


The development branch version that contains the latest additions and bug fixes can be manually downloaded from here: https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20TS004F/TS004F.groovy

----------------------

Reserved:  Scene Control vs Dimming functionality:  TODO

