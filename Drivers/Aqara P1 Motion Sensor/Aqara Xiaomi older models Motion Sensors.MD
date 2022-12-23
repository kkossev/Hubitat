## Aqara/Xiaomi Motion Sensors

This is a new thread for the older models Aqara/Xiaomi motion sensors, that are supported by the **Aqara P1 Motion Sensor driver** initially published [here](https://community.hubitat.com/t/aqara-fp1-and-p1-motion-sensors/92987/63?u=kkossev). As this driver supports both the new models (P1, FP1) and the old motion sensors, mixing the updates and the comments for the different devices is somewhat confusing for most of the forum users. Moreover, the behaviour of the old Aqara sensors in regards to the way they are paired and stay connected to the Zigbee network differ slightly compared to the new sensos.

There are already several custom drivers in Hubitat that support Aqara/Xiaomi motion sensors by @veeceeoh [(link)](https://community.hubitat.com/t/xiaomi-aqara-zigbee-device-drivers-possibly-may-no-longer-be-maintained/631), @markus [(link)](https://community.hubitat.com/t/release-xiaomi-aqara-opple-drivers-with-presence/41537), @chirpy [(link)](https://community.hubitat.com/t/release-xiaomi-aqara-opple-drivers-with-presence/41537) and other Hubitat community developers.All these existing drivers are working great, so there should be no reason to change the driver that works for you, except if you want to use one and the same driver for all the different Aqara models of motion and presence sensors.

The recommended method for installing this custom driver is to use the community Hubitat Package Manager [(HPM)](https://community.hubitat.com/t/release-hubitat-package-manager-hubitatcommunity/94471), search for "Aqara P1 Motion Sensor" or by Tag 'Zigbee'.

For manual installation, the last stable version code is here: https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Aqara%20P1%20Motion%20Sensor/Aqara_P1_Motion_Sensor.groovy 


|  Device |  Links |
|---|---|
| Aqara T1 Motion Sensor RTCGQ12LM <br> ![image](https://user-images.githubusercontent.com/6189950/206923704-45081944-bab3-4356-b88b-b8935cadd63e.png) | model:"lumi.motion.agl02" <br>Battery: CR2450 <br>Supports: battery voltage/percentage, detection interval, illuminance, occupancy <br> FCC: [link](https://fccid.io/2AKIT-AS011) <br> Review: [link](https://www.reddit.com/r/Aqara/comments/kuskin/quick_test_aqara_motion_sensor_t1_rtcgq12lm/) <br>Aqara site (in Chinese): [link](https://www.aqara.com/cn/d17_overview) <br>Manual (in Chinese) : [link](https://static-resource.aqara.com/temp/%E4%BA%BA%E4%BD%93%E4%BC%A0%E6%84%9F%E5%99%A8T1%E8%AF%B4%E6%98%8E%E4%B9%A6_1656476152533.pdf) <br>Purchase link: <b>n/a</b>|  [//]: <> (https://zigbee.blakadder.com/Aqara_RTCGQ12LM.html)
| Aqara E1 Motion Sensor RTCGQ15LM <br> ![image](https://user-images.githubusercontent.com/6189950/206926909-7f3f42e3-62c9-4e54-bbf2-b618ab7e649e.png) | model:"lumi.motion.acn001" <br>Battery: CR2450 <br>Supports: battery voltage/percentage,illuminance, occupancy <br><br>Aqara site (in Chinese): [link](https://www.aqara.com/cn/Motion-Sensor-E1_overview) <br>Manual : [link](https://static-resource.aqara.com/temp/%E4%BA%BA%E4%BD%93%E4%BC%A0%E6%84%9F%E5%99%A8E1%E8%AF%B4%E6%98%8E%E4%B9%A6_1655375123135.pdf) <br>Purchase link: <b>n/a</b>|  [//]: <> (https://zigbee.blakadder.com/Aqara_RTCGQ15LM.html)
| Aqara Motion Sensor RTCGQ11LM <br> ![image](https://user-images.githubusercontent.com/6189950/206924507-bc12b9c9-798b-4f66-b7cf-09905330aa4f.png) | model:"lumi.sensor_motion.aq2" <br> <br>Battery: CR2450 <br>Supports: battery voltage/percentage, occupancy <br> Aqara site: [link](https://www.aqara.com/en/human_motion_sensor.html) <br> Manual: [link](https://cdn.cnbj2.fds.api.mi-img.com/cdn/aqara/website/manuals_en/downloads/Motion%20Sensor%20Quick%20Start%20Guide.pdf) <br>Banggood: [link](https://www.banggood.com/Aqara-Zgbee-Wireless-Human-Body-PIR-Sensor-Smart-Home-Kit-Work-with-Gateway-APP-p-1177007.html) | [//]: <> (https://zigbee.blakadder.com/Aqara_RTCGQ11LM.html)
| Xiaomi/Mijia Motion Sensor RTCGQ01LM <br> ![image](https://user-images.githubusercontent.com/6189950/206925401-ab3d6b16-fa90-4857-b651-11febce45bd6.png) | model:"lumi.sensor_motion" <br>Battery: CR2032 <br> Supports: low battery warning; occupancy<br> Banggood: [link](https://www.banggood.com/Xiaomi-Zigbee-IR-Intelligent-Human-Body-Sensor-Smart-Home-Remote-Control-Suit-Kit-Work-With-Mijia-APP-p-1017540.html) |  [//]: <> (https://zigbee.blakadder.com/Xiaomi_RTCGQ01LM.html)


## Versions history

 * ver. 1.0.0 2022-06-24 - first test version
 * ver. 1.1.0 2022-06-30 - decodeAqaraStruct; added temperatureEvent;  RTCGQ13LM; RTCZCGQ11LM (FP1) parsing
 * ver. 1.1.1 2022-07-01 - no any commands are sent immediately after pairing!
 * ver. 1.1.2 2022-07-04 - PowerSource presence polling; FP1 pars
 * ver. 1.1.3 2022-07-04 - FP1 approachDistance and monitoringMode parameters update
 * ver. 1.1.4 2022-07-08 - aqaraReadAttributes()
 * ver. 1.1.5 2022-07-09  - when going offline the battery level is set to 0 (zero); when back online, the last known battery level is restored; when switching offline, motion is reset to 'inactive'; added digital and physical events type
 * ver. 1.1.6 2022-07-12 - aqaraBlackMagic; 
 * ver. 1.1.7 2022-07-23 - added MCCGQ14LM for tests
 * ver. 1.2.0 2022-07-29 - FP1 first successful initializaiton; attr. 0142 presence bug fix; debug logs improvements; monitoring_mode bug fix; LED is null bug fix ;motionRetriggerInterval bugfix for FP1; motion sensitivity bug fix for FP1; temperature exception bug; monitoring_mode bug fix; approachDistance bug fix; setMotion command for tests/tuning of automations; added motion active/inactive simulation for FP1
 * ver. 1.2.1 2022-08-10 - code / traces cleanup; change device name on initialize(); 
 * ver. 1.2.2 2022-08-21 - added motionRetriggerInterval for T1 model; filter illuminance parsing for RTCGQ13LM
 * ver. 1.2.3 2022-12-11 - (dev. branch ) added internalTemperature option (disabled by default); added homeKitCompatibility option to enable/disable battery 100% workaround for FP1 (HomeKit); Approach distance bug fix; battery 0% bug fix; pollPresence after hub reboot bug fix; RTCGQ13LM battery fix; added RTCGQ15LM and RTCGQ01LM; added GZCGQ01LM and GZCGQ11LM illuminance sensors for tests; refactored setDeviceName()

The latest development branch version URL is here : https://github.com/kkossev/Hubitat/blob/development/Drivers/Aqara%20P1%20Motion%20Sensor/Aqara_P1_Motion_Sensor.groovy 


### Useful links :

[Xiaomi & Aqara Devices - Pairing & Keeping them connected](
https://community.hubitat.com/t/xiaomi-aqara-devices-pairing-keeping-them-connected/623)




