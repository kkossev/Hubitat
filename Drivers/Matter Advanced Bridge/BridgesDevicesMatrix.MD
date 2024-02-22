---
title: "Matter Advanced Bridge Project"
author: "Krassimir Kossev"
---


*last updated 2024/02/20* <br>
Target release date : mid of March 2024

### <b>Matter Advanced Bridge Project : MVP</b>
1. Publish and maintain a list of devices types work and which do not work for each of the Matter Bridges tested. (**status: WIP**)
2. Publish a list of known issues and limitations of the Hubitat Matter implementation. (**Status: WIP**)
3. Stable discovery process. (**Status: TESTING (from ver 0.4.0)**)
4. RGB bulb bugs fixed and tested.  (**Status: TESTING (ver 0.4.1)**)
5. Battery level support. (**status: TODO**)
6. Make the refresh command work in the child devices. (**Status: TODO**)
7. Curtain Motors (WindowCovering) bugs fixed and tested.(**status: TODO**)
8. Matter Advanced Bridge package can be installed and updated via HPM. (**status: DONE**)
9. Help/Documentation button in driver linked to GitHub web page. (**status: TODO**)
10. ???



### <b>Zemismart M1 Hub as Matter Bridge</b>   
*last updated 2024/02/17*
|Device Type                |         Status               |          Remarks                  |
|:--------------------------|:----------------------------:|----------------------------------:|
| Relays/Switches - On/Off  | working OK - tested          |                                   |
| Plugs - On/Off            | working OK - tested          |                                   |
| Bulbs - On/Off            | working OK - tested          |                                   |
| Bulbs - level control     | working OK - tested          |                                   |
| Bulbs - CT control        | working OK - tested          |                                   |
| Bulbs - RGBW control      | working OK - tested          |                                   |
| Motion Sensors            | working OK - tested          | Not all motion sensors are working! |
| mmWave Presence Sensors   | working OK - tested          | Moes/Linptech                     |
| Contact Sensors           | working OK - tested          | Generic Component Contact Sensor  |
| Water Leak Sensor         | working OK - tested          | as a contact / switch             |
| Tuya Valve on/off         | working OK - tested          | as a contact / switch             |
| Temperature Sensor        | ????????                     |  test it!                         |
| Humidity Sensor           | ????????                     |  test it!                         |
| Zemismart Curtain Motor   | working (some bugs to be fixed) | Zemismart Curtain Motor        |
| Fingerbot                 | working OK - tested          | as contact / switch               |
|:--------------------------|:----------------------------:|----------------------------------:|
| Wireless Remote Switch    | TODO  (not implemented yet)  | Tuya TS0044 Scene Switch - CRASHES! |
| Wireless button           | TODO  (not implemented yet)  | Tuya button          - CRASHES! |
| Battery Level reporting   | TODO  (not implemented yet)  | check !                           |
|:--------------------------|:----------------------------:|----------------------------------:|
| Tuya Gas Detector         | Unknown                |   not tested / not implemented          |
| Tuya Smoke Detector       | Unknown                |   not tested / not implemented          |
| Tuya Vibration Sensor     | Unknown                |   not tested                            |
| TRVs                      | Unknown                |   not tested                            |
| Light Sensors             | Unknown                |   not tested                            |
| Light Door Lock           | Unknown                |   not tested                            |
|:--------------------------|:----------------------------:|----------------------------------:|
| Tuya Dimmers              | NOT working (not exposed via Matter)    | Girier TS0110F, Tuya TS0601 |
| Thermostats               | NOT working (not exposed via Matter)    |   AVATTO thermostat         |
|:-------------------|:--------------------------:|-------------------------------------:|


---------------------

### <b>Aqara E1 Hub as Matter Bridge</b>
*last updated 2024/02/20*
|Device Type                |         Status               |          Remarks                  |
|:--------------------------|:----------------------------:|----------------------------------:|
| Relays - On/Off           | working OK - tested          | Aqara Double Rocker H1 EU         |
| Plugs - On/Off            | working OK - tested          | Aqara Smart Plug EU               |
| Bulbs - On/Off            | working OK - tested          | Aqara LED Strip T1                |
| Bulbs - level control     | working OK - tested          | Aqara LED Strip T1                |
| Bulbs - CT control        | working OK - tested          | Aqara LED Strip T1                |
| Bulbs - RGBW control      | working OK - tested          | Aqara LED Strip T1 (*colorMode is wrong!)|
| Motion Sensors            | working OK - tested          | Aqara P1 Motion Sensor, Xiaomi    |
| Vibration Sensors         | working OK - tested          | Aqara Vibration Sensor (as motion sensor) |
| Temperature Sensor        | working OK - tested          | Aqara Temperature and Humidity Sensor T1, Aqara TVOC sensor   |
| Humidity Sensor           | working OK - tested          | Aqara Temperature and Humidity Sensor T1, Aqara TVOC sensor   |
| Aqara Cube T1 Pro         | OK (exposed as 6 x OnOff)    | Aqara Cube T1 Pro                 |
|:--------------------------|:----------------------------:|----------------------------------:|
| Thermostats               | TODO (partially implemented) |  Aqara thermostat E1 (TRV) - TODO |
| Light Sensor T1           |TODO  (not implemented yet)   |Aqara Light Detection Sensor T1    |
| Aqara Door Lock           | partially working (status only) |  Aqara U100                    |
| Door and Window Sensor    | TODO - check it1     |Aqara Door and Window Sensor, old and T1   |
| Wireless Remote Switch    | TODO  (not implemented yet)  | Aqara Double Rocker H1 - CRASHES! |
| Wireless button           | TODO  (not implemented yet)  | Xiaomi/Lumi            - CRASHES! |
| Wired Remote Switch       | TODO  (not implemented yet)  | not imlemented                    |
| Battery Level reporting   | TODO  (not implemented yet)  | check !                           |
|:--------------------------|:----------------------------:|-----------------------------------:|
| Presence sensor FP1       | NOT working (not exposed via Matter)    | Aqara FP1              |
| Presence sensor FP2       | NOT working (not exposed via Matter)    | Aqara FP2              |
| PM 2.5                    | NOT working (not exposed via Matter)    | Aqara TVOC sensor      |
| Atmosheric pressure       | NOT working (not exposed via Matter)    | Aqara TVOC sensor      |
| Light Detector T1         | NOT working (not exposed via Matter??)  | not tested             |
| Xiaomi(Lumi) Light Sensor | NOT working (not exposed via Matter  )  | Xiaomi(Lumi) Light Sensor|
| Smoke Detectors           | NOT working (not exposed via Matter).   | Aqara Smart Smoke Detector|
| Wateer Leak Sensors       | NOT working (not exposed via Matter)    | Aqara Water Leak Sensor  |
|:--------------------------|:---------------------------------------:|-----------------------:|
| Aqara Dimmers             | Unknown                |   not tested / not implemented          |
| Smart Natural Gas Detector| Unknown                |   not tested / not implemented          |
| Aqara Curtain Motor       | Unknown                |   not tested                            |
| Smart Pet Feeder          | Unknown                |   waiting to be tested by bobbyD :)     |
|:--------------------------|:----------------------:|----------------------------------------:|

---------------------

### <b>Philips Hue Hub as Matter Bridge</b>
*last updated 2024/02/14*
|Device Type                | Status                    |          Remarks                      |
|:--------------------------|:-------------------------:|--------------------------------------:|
| Bulbs - On/Off            | working -   tested        |   All bulbs                           |
| Bulbs - Level control     | working  -  tested        |   All bulbs                           |
| Bulbs - CT control        | working  -  tested        |   All bulbs                           |
| Bulbs - RGWB control      | working  -  tested        |   All bulbs                           |
| Motion Sensor             | working  -  tested        |  Hue motion sensor                    |
| Temperature Sensor        | working  -  tested        |  Hue motion sensor                    |
| Plugs                     | working  -  tested        |                                       |
|:--------------------------|:-------------------------:|--------------------------------------:|
| Contact Sensors           | should be OK - NOT tested | not tested                            |
|:--------------------------|:-------------------------:|--------------------------------------:|
| Wireless button           | TODO  (not implemented yet)  | Xiaomi/Lumi            - CRASHES!  |
| Wireless Dimmer           | TODO  (not implemented yet)  | Hue Dimmer v2     ?                |
| Battery Level reporting   | TODO  (not implemented yet)  | Hue Dimmer v2     ?                |
|:--------------------------|:-------------------------:|--------------------------------------:|
| Hue Scenes                | NOT working (not exposed via Matter)    |                         |
| Hue Groups                | NOT working (not exposed via Matter)    |                         |
|:--------------------------|:-------------------------:|--------------------------------------:|


---------------------

### <b>Swithbot Hub2 as Matter Bridge</b>
*last updated 2024/02/14*
|Device Type                |         Status               |          Remarks                  |
|:--------------------------|:----------------------------:|----------------------------------:|
| Temperature Sensor        | working OK - tested          |  Switchbot Hub2 integrated   |
| Humidity Sensor           | working OK - tested          |  Switchbot Hub2 integrated   |
| Switchbot Curtain Motor   | working (some bugs to be fixed) |  Switchbot Curtain Motor         |
|:---------------------|:-------------------------:|--------------------------------------:|
| Battery Level reporting   | TODO  (not implemented yet)  | check !                           |
|:-------------------|:--------------------------:|-------------------------------------:|


## **Matter Advanced Bridge driver - device support per Bridge model matrix**
*last updated 2024/02/20*
|Matter Clusters| Matter Device Type <br>ID                    |Tuya|Aqara|Philips Hue|Switch Bot|HE Child Device Driver | Remarks |
|:----------:|:--------------------:|:-----:|:-----:|:-----:|:-----:|:--------------:|:--------------:|
|    --        | 4.   <b>Lighting Device Types</b>             |       |       |       |     |                |         |
|    06        | 4.1. On/Off Light<br>(ID: 0x0100)             |  Yes  |  Yes  |  Yes  |  ?  |'Matter Generic Component Switch' |       |
|  06, 08      | 4.2. Dimmable Light <br>(ID: 0x0101)          |  Yes  |  Yes  |  Yes  |  ?  |'Generic Component Dimmer'        |       |
| 06, 08, 0300 | 4.3. Color Temperature Light <br>(ID: 0x010C) |  Yes  |  Yes  |  Yes  |  ?  |'Generic Component CT'        |      |
| 06, 08, 0300 | 4.4. Extended Color Light <br>(ID: 0x010D)    |  Yes  |  Yes  |  Yes  |  ?  |'Generic Component RGBW'       |      |
|    --        | 5. <b>Smart Plugs/Outlets/Actuators</b>       |       |       |       |     |      ---                  |                  |
|    06        | 5.1 On/Off Plug-in Unit <br>(ID: 0x010A)      |  Yes  |  Yes  |  Yes  |  ?  |'Matter Generic Component Switch'  |      |
|  06, 08      | 5.2 Dimmable Plug-in Unit <br>(ID: 0x010B)    |   ?   |   ?   |   ?   |  ?  |'Generic Component Dimmer'        |       |
|    --        | 6. <b>Switches and Control Device Types</b>   |       |       |       |     |      ---         |                  |
|    06        | 6.1  On/Off Light Switch <br>(ID: 0x0103)     |  Yes  |  Yes  |  Yes  |  ?  |'Matter Generic Component Switch'  | Obsolete? |
|  06, 08      | 6.2      Dimmer Switch <br>(ID: 0x0104)       |   ?   |   ?   |   ?   |  ?  |'Generic Component Dimmer'  | Tuya dimmers are NOT exporeted! |
| 06, 08, 0300 | 6.3   Color Dimmer Switch <br>(ID: 0x0105)    |   ?   |   ?   |   ?   |  ?  |'Generic Component RGBW'               |                          |
|06, 08, 0300,<br>0400,0406| 6.4     Control Bridge <br>(ID: 0x0840)       |   ?   |   ?   |   ?   |  ?  |                      |                          |
|    3B        | 6.6    Generic Switch <br>(ID: 0x000F)        |  TODO |  TODO |   ?   |  ?  | TODO         | Clarify the Tuya/Aqara devices that use cluster 0x3B|  
|    --        | 7.     <b>Sensor Device Types</b>             |       |       |       |     |      --         |                          |
|    45        | 7.1.   Contact Sensor <br>(ID: 0x0015)        |  Yes  |  Yes  |   ?   |  ?  |'Generic Component Contact Sensor'| Confirm Aqara Contact sensors are working!|
|    45        | n/a  Water Leak Sensors<br>(ID:n/a)           |  Yes  |  No   |   ?   |  ?  |'Generic Component Contact Sensor'| as a Contact Sensor! | 
|   0400       | 7.2    Light Sensor <br>(ID: 0x0106)          |  No   |  TODO |   ?   |  ?  | TODO              |Confirm Light sensors are working or not? |
|   0406       | 7.3    Occupancy Sensor <br>(ID: 0x0107)      |  Yes  |  Yes  |   ?   |  ?  |'Matter Generic Component Motion Sensor'|     |
|   0402       | 7.4    Temperature Sensor <br>(ID: 0x0302)    |  Yes  |  Yes  |  Yes  | Yes |'Generic Component Omni Sensor'|      |
|   0403       | 7.5      Pressure Sensor<br>(ID: 0x0305 )     |  No |  No |   ?   |  ?  |               |                  |
|   0405       | 7.7       Humidity Sensor <br>(ID: 0x0307)    |  Yes  |  Yes  |   ?   | Yes |'Generic Component Omni Sensor'|       |
|06, 08, 0300, <br>0400, 0406| 7.8        On/Off Sensor <br>(ID: 0x0850) |   ?   |   ?   |   ?   |  ?  |               | not clear what is this  |
|5C, 0405, 0402, <br>040C      | 7.9      Smoke CO Alarm <br>(ID: 0x0076)|   No   |   No  |   ?   |  ?  |    not available        |   No support in this driver   |
|    --        | 8       <b>Closure Device Types</b>           |       |       |       |     |   --           |           |
|   0101       | 8.1        Door Lock <br>(ID: 0x000A)         |  No   | Yes*  |   ?   |  ?  |'Generic Component Lock'|        |
|   0102       | 8.3      Window Covering <br>(ID: 0x0202)     |  Yes  |   ?   |  --   | Yes |'Matter Generic Component Window Shade'|      |
|    --        |  9      <b>HVAC Device Types</b>              |       |       |       |     |    --           |              |
|   0201       | 9.2        Thermostat <br>(ID: 0x0301)        |  No?  |  Yes  |  No   |  ?  |'Generic Component Thermostat'|       |
|   0202       | 9.3           Fan <br>(ID: 0x002B)            |  No   |  No   |  No   |  ?  |   not available        |   No support in this driver   |
|    2C        | 9.4       Air Purifier  <br>(ID: 0x002D)      |  No   |  No   |  No   |  ?  |   not available        |   No support in this driver   |
|    5B        | 9.5     Air Quality Sensor <br>(ID: 0x002C)   |  No   |  No   |  No   |  ?  |   not available        |   No support in this driver   |
|    n/a       |        <b>Other Devices<b>                    |       |       |       |     |   --          |      |
|    2F        | --     Battry Reporting                       |  TODO   |  TODO   |  TODO   |  ?  |'Matter Generic Component Battery'| TODO - check what the problem is - HIGH priority  |
|    n/a       |        <b>Other Functions<b>                  |       |       |       |     |    --           |      |
|    ---       |         Composite devices                     |   NO  |   NO  |   NO  |  NO  | No support in this driver | TODO - low priority    |
|    ---       |         Groups                                |   NO  |   NO  |   NO  |  NO  | not exported by Matter Bridges?|      |
|    ---       |         Scenes                                |   NO  |   NO  |   NO  |  NO  | not exported by Matter Bridges?|      |



Table Legend:

"Yes" means supported by both the Bridge and the driver, and tested and confirmed to be working.

"NO"  means not supported by the particular brand Bridge of by the driver.

"No?" means the tested device did not show up as bridged by the particular brand Bridge, but there is a chance other devices may be expored.

"--" or "n/a" means not applicable for the particular brand bridge.

"?"   means unknown - not tested.


--------------------------------

## Known Hubitat Matter platform limitations and issues : 

* initialize() method is called on each and every Matter error - resulting in multiple subscriptions to one and the same attribute.
* unsubscribe() resets all Matter connections - results in re-subscribing all other Matter devices connected to the same HE hub
* Hubitat does not receive a notification when a Matter device has been reset (powered off/on) - the subscriptions (automatic reproting) are lost!
* Matter interface status (operational/failure) can not be checked in the driver - it is unknown whether the bridge is online or offline before sendinga ping command.
* Subscription to Matter events does not work - this prevents buttons/scene switches supprot in Hubitat. 
* There is no decoding of matter complex data structures - Hue bridge rooms as example can not be imported.
* Hubitat pareser for some matter messages throws exception java.lang.ArrayIndexOutOfBoundsException - the effect of this problem is unknown.

----------------------------

## Known driver issues

* Performance may not be the optimal, because of the huge state variables. Optimizating the speed/performance will be a second stage of the project, after the BETA release.
* TODO

----------------------------

Tuya/Zemismart Matter Bridges supported devices : [link](https://community.hubitat.com/t/matter-advanced-bridge-alpha-tests-group/131191/2#bzemismart-m1-hub-as-matter-bridgeb-1)

Aqara Matter Bridges supported devices : [link](https://community.hubitat.com/t/matter-advanced-bridge-alpha-tests-group/131191/3#baqara-e1-hub-as-matter-bridgeb-2)

Philips Hue Bridge supported devices : [link](https://community.hubitat.com/t/matter-advanced-bridge-alpha-tests-group/131191/3#bphilips-hue-hub-as-matter-bridgeb-3)

All bridges compatibility matrix : [link](https://community.hubitat.com/t/matter-advanced-bridge-alpha-tests-group/131191/3#matter-advanced-bridge-driver-device-support-per-bridge-model-matrix-5)

---------------------





