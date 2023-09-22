# Tuya Zigbee devices supported in Hubitat

## this document is a Work In Progress ...

### Tuya Brands
|  Tuya  |  Links |
|---|---|
| ![image](https://user-images.githubusercontent.com/6189950/154422114-c45bc7dd-b3e6-4204-ab3a-5ebf66c09ca2.png)   | https://www.tuya.com/  |


### Tuya Motion Sensors

## Supported Passive InfraRed (PIR) sensors :

|  Device |  Links |
|---|---|
|  Tuya ZigBee Multi-Sensor 4 in 1  ![image](https://user-images.githubusercontent.com/6189950/163685629-86881a7a-0e3e-4b86-b568-16a3ff8c8fcd.png) |Zigbee ID: _TZ3210_zmy9hjay, _TYST11_i5j6ifxj, _TYST11_7hfcudw5<br>AliExpress: [link](https://www.aliexpress.com/item/1005001878974427.html )<br>Price Range: Mid <br><br>Reports: Motion, Illuminance, Temperature, Humidity<br>Configuration: sensitivity, Keep Time, LED | 
| Tuya ZigBee Multi-Sensor 3 in 1  ![image](https://user-images.githubusercontent.com/6189950/163686024-e1de1fed-8c03-4729-a359-607654780992.png) | AliExpress: [link](https://www.aliexpress.com/item/1005002339649035.html)<br> Amazon.de: [link](https://www.amazon.de/dp/B092J89272?ref_=cm_sw_r_cp_ud_dp_QA4DXGXCKHZF0M6EQN1A)<br> Price range: Mid<br><br>Reports: Motion, Temperature, Humidity<br>Configuration:Sensitivity, Keep Time | 
| Tuya ZigBee Multi-Sensor 2 in 1<br> ![image](https://user-images.githubusercontent.com/6189950/206842638-dc591556-74f6-44b3-8b01-060b644f30df.png) | model: TS0601<br>Manufactuer: TZE200_3towulqd <br>Battery: 2450<br><br> AliExpress: [link1](https://www.aliexpress.com/item/1005004294995498.html)<br> AliExpress: [link2](https://www.aliexpress.com/item/1005004018051598.html)<br>AliExpress: [link3](https://www.aliexpress.com/item/1005004134502945.html) <br><br>Reports: motion and illuminance<br>Configuration: sensitivity and rettrigger time (10/30/60/120)| 
| TUYATEC RH3040 Motion Sensor ![image](https://user-images.githubusercontent.com/6189950/155217683-4714c07e-939e-4c74-9ba5-fc59ccef98b1.png) | Reports: Motion, Battery<br><br>AliExpress: [link](https://www.aliexpress.com/item/1005002468620848.html )<br> AliExpress: [link](https://www.aliexpress.com/item/1005002557815290.html) <br> <br>Review:[(link)](https://investio.pro/zemismart-tuya-motion-sensor-for-smart-home-connection-to-home-assistant/) |
|  PIR Motion Sensor  ![image](https://user-images.githubusercontent.com/6189950/155220379-41c88ccf-1d9a-4c7c-b00a-cc5a67d69cb1.png) |Reports: Motion, Battery<br><br> AliExpress: [link1](https://www.aliexpress.com/item/1005001977463358.html)<br>AliExpress:  [link2](https://www.aliexpress.com/item/1005001889414247.html )<br>AliExpress:  [link3](https://www.aliexpress.com/item/4000808875375.html ) <br> |
|Tuya ZigBee PIR Motion Sensor ![image](https://user-images.githubusercontent.com/6189950/180646422-4db28a30-a67f-4eb9-a076-bad21951691b.png) | Reports: Motion, Battery<br><br> Zigbee ID: TS0202  _TZ3000_bsvqrxru <br>AliExpress: [link](https://www.aliexpress.com/item/1005003371197737.html)|
|  PIR sensor TS0202 ![image](https://user-images.githubusercontent.com/6189950/154433141-af9f57a4-0aad-4405-bcbd-1155da268a06.png) | Reports: Motion, Battery<br><br>AliExpress: [link](https://www.aliexpress.com/item/1005003776494634.html) <br> |
| Tuya  Ceiling-mounted Human Presence Sensor AIR ![image](https://user-images.githubusercontent.com/6189950/167108614-63b33209-50a4-49fa-b185-32c375e92e10.png) | Zigbee ID: "TS0601"; "__TZE200_auin8mzr" <br><br>Price range: mid  <br>  AliExpress: [(link)](https://www.aliexpress.com/item/1005003370183540.html) <br><br>Driver status: fully operational|
|Tuya Smart ZigBee Human Presence Detector ![image](https://user-images.githubusercontent.com/6189950/180646119-65b6a9f5-14e5-42a0-80b0-e93146cd21e4.png)| AliExpress: [link](https://www.aliexpress.com/item/1005004296422003.html)<br>Price range: mid/High<br><br><br>Driver status: fully operational|
| PIR Motion Sensor Detector With Light Sensor Scene Switch<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/386c4047-5dd0-44d7-9fca-0d6eea98bbaf)| Model : TS0202 <br>Manufacturer : _TZ3210_cwamkvua <br><br>AliExpres: [link](https://www.aliexpress.com/item/1005005302253176.html)<br>AliExpress: [link](https://www.aliexpress.com/item/1005004644107072.html)|

## Supported mmWave radars :
#### Remark: most of the Tuya 5.6 GHz radars are rather chatty, sending the distance to the detected object every second during the time period when they detect a presence.
Look for the recommended new 24Ghz TS0215 radars at the end of this list

|  Device |  Links |
|---|---|
| Tuya Smart ZigBee Smart Ceiling-mounted Human Presence Sensor 5.8 GHz mmWave radar <br> **w/ distance measurement** <br>  ![image](https://user-images.githubusercontent.com/6189950/166865911-d2d01efd-ae8e-4c92-ae18-0f78a5f52d39.png) |  Zigbee ID: "TS0601"; Manufacturers:  _TZE200_ztc6ggyl,  _TZE200_ikvncluo ,  _TZE200_lyetpprm <br>Price range: Mid/High<br>Combined Radar and PIR and illuminance sensor, detects movement pretty fast!<br>  <br> AliExpress: [(link)](https://www.aliexpress.com/item/1005004106075721.html) <br> MOES: [(link)](https://www.aliexpress.com/item/1005004147995775.html)<br> Amazon .com: [(link)](https://www.amazon.com/dp/B0BMLGZCXS)<br><br>Driver status: fully operational|
| Tuya Smart ZigBee Smart Ceiling-mounted Human Presence Sensor 24 GHz mmWave radar <br> **w/ 'Scenes'** <br> ![image](https://user-images.githubusercontent.com/6189950/167111727-ff34a0b5-17a0-4e63-ad11-8745a0ebf53c.png) | Zigbee ID: "TS0601"; "__TZE200_vrfecyku" <br>Price range: High  <br> AliExpress: [(link)](https://www.aliexpress.com/item/1005002897274124.html) <br><br> Driver status: basic functionalities only (motion/presence and radarSensitivity) |
| Tuya Smart ZigBee Smart Ceiling-mounted Human Presence Sensor 24 GHz mmWave radar <br> **w/ 'Fall Alarm'** <br> ![image](https://user-images.githubusercontent.com/6189950/167121956-1b390a19-efa6-4861-b76f-30c99a9c21bb.png) | Zigbee ID: "TS0601"; "__TZE200_lu01t0zl" <br>Price range: High   <br> AliExpress: [(link)](https://www.aliexpress.com/item/1005003870853288.html)<br><br> Driver status: basic functionalities only (motion/presence and radarSensitivity)  |
| Tuya Square Black 24GHz radar w/ LED  <br> ![image](https://user-images.githubusercontent.com/6189950/184286533-1637eddf-872f-4dc5-9539-4af54c342503.png) | <br> <b> Caution: chatty device!</b> <br><b>NOT RECOMMENDED to buy!</b><br><br>Review: [link](https://blakadder.com/cheapest-tuya-human-presence-sensor/) <br> AliExpress:  [(link)](https://www.aliexpress.com/item/1005004532077450.html))  <br> Driver status: fully functional (LED enable/disable, presence detection timers) |
| <b>NEW!</b><br>OWON ZigBee Occupancy Sensor OPS305<br>  ![image](https://user-images.githubusercontent.com/6189950/184812511-ff85832a-787d-4859-bf7e-deb26da0b0c5.png)  | 10GHz mmWave radar from OWON <br> OWON site: [(link)](https://www.owon-smart.com/zigbee-occupancy-sensor-ops305-e-product/)<br> Occupancy only, no configurable settings! |

## New Tuya radars :  
(more info [here](https://community.hubitat.com/t/the-new-tuya-24ghz-human-presence-sensor-ts0225-tze200-hl0ss9oa-finally-a-good-one/122283) )
|  Device |  Links |
|---|---|
|Tuya 5.8GHz Radar With Siren Alarm Motion Lux <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/95729606-28ce-4229-9a4d-c7943fc946ba) | Product Profie: "TS0225_HL0SS9OA_RADAR" ("Tuya TS0225_HL0SS9OA 24GHz Radar")<br>Model: TS0225 <br>Manufacturer: _TZE200_hl0ss9oa <br>Reports: Motion, Illuminance, humanMotionState ("none","large""small", "static")<br>Configuration: presenceKeepTime, ledIndicator, radarAlarmMode, radarAlarmVolume, radarAlarmTime, motionFalseDetection, motionDetectionSensitivity, motionMinimumDistance, motionDetectionDistance, smallMotionDetectionSensitivity, smallMotionMinimumDistance, smallMotionDetectionDistance, breatheFalseDetection, staticDetectionSensitivity, staticDetectionMinimumDistance, staticDetectionDistance <br><br>AliExpress: [link](https://www.aliexpress.com/item/1005005761971083.html) <br> AliExpress: [link](https://www.aliexpress.com/item/1005005767446349.html) <br> AliExpress: [link](https://www.aliexpress.com/item/1005005781125119.html) <br><br>Driver status: fully functional<br><br> **RECOMMENDED!**|
|Tuya Zigbee Human Presence Detector 5.8GHz<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/fda02313-3e1d-4424-9541-64dd583a87d3) | Product Profie: "TS0225_2AAELWXK_RADAR" ("Tuya TS0225_2AAELWXK 24GHz Radar")<br>Model: TS0225 <br>Manufacturer: _TZE200_hl0ss9oa <br>Reports: Motion, Illuminance, humanMotionState ("none","large""small", "static")<br>Configuration: presenceKeepTime, ledIndicator, radarAlarmMode, radarAlarmVolume, radarAlarmTime, motionFalseDetection, motionDetectionSensitivity, motionMinimumDistance, motionDetectionDistance, smallMotionDetectionSensitivity, smallMotionMinimumDistance, smallMotionDetectionDistance, breatheFalseDetection, staticDetectionSensitivity, staticDetectionDistance <br><br> AliExpress: [link](https://www.aliexpress.com/item/1005005909540714.html) <br><br>Driver status: fully functional<br><br> **RECOMMENDED!**|
|Tuya Zigbee Smart Human Body Sensor 24GHz Radar Detector <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/f5e1b5ac-624f-4c15-970f-cf0acb05f32b) |Model: TS0601<br>Manufacturer: _TZE204_kapvnnlk <br> Amazon .com: [link](https://www.amazon.com/dp/B0CDRBX1CQ)<br> AliExpress: [link](https://www.aliexpress.com/item/1005005858609756.html) <br> Reports: presence, distance, humanMotionState (none, small_move, large_move), battery level<br><b>NO ILLUMINANCE! The batteries are only for a backup, and will be depleted very fast!</b><br>Configuration: fadingTime,maximumDistance, radarSensitivity, smallMotionDetectionSensitivity <br>Spammy device: yes? (to be confirmed)<br><br>Driver status: W.I.P. (basic functions are working)| 
|Smart Human Presence Sensor <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/2c1ebe3e-3f08-4f49-b9c4-76fb1cf714f0)|Model: TS0601<br>Manufacturer: _TZE204_sooucan5 <br> Reports: Motion/presence, illuminance, distance<br>Configuration:radarSensitivity, minimumDistance, maximumDistance, fadingTime, detectionDelay <br>Spammy device - Yes<br>Amazon : [link](https://www.amazon.com/dp/B0BYDCY4YN) |
|Zigbee  Human Presence Sensor 24G 5.8G Radar <br>  ![image](https://github.com/kkossev/Hubitat/assets/6189950/609da955-f29e-49c0-bd4a-ed81b4c89919) | Product Profile: "TS0601_SXM7L9XA_RADAR" ("Tuya Human Presence Detector SXM7L9XA")<br>Model:TS0601<br>Manufacturer: _TZE204_sxm7l9xa<br> Reports: presence, illuminance, distance<br> Configuration:  radarSensitivity, detectionDelay, fadingTime, minimumDistance, maximumDistance<br> Spammy device: Yes <br><br>AliExpress: [link](https://www.aliexpress.com/item/1005004788260949.html)| 
|Tuya Zigbee Human Presence Detector <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/1d8c7d5e-7213-4cca-b747-d6739f45fe5d) | Product Profile: <br>Model: TS0601<br>Manufacturer: _TZE204_ijxvkhd0<br>Reports: Presence, Illuminance, Distance <br> Configuration: NONE yet (TODO!)<br>isSpammy: Yes?<br><br>Aliexpress: [link](https://www.aliexpress.us/item/1005005909775887.html)<br><br>Driver status: TODO<br> | 
|Loginovo Zigbee Mmwave Human Presence Sensor <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/df4aa36e-a5af-4ad5-8624-a07766d56f25) |Product Profile: "TS0601_YENSYA2C_RADAR" ("Tuya Human Presence Detector YENSYA2C") <br>Model: TS0601 <br>Manufacturer: _TZE204_yensya2c _TZE204_mhxn2jso<br> Reports: Motion, Illuminance, Distance <br> Configuration: NONE (TODO!)<br>isSpamy: Yes<br><br>AliExpress: [link](https://www.aliexpress.com/item/1005005677110270.html) <br><br>Driver status: Basic functionality only (TODO!)<br> |

----------------------------


### Tuya Smart Plugs/Sockets/Circut Breakers
|  Device |  Links |
|---|---|
| Tuya TS0121 (BlitzWolf) <br> ![image](https://user-images.githubusercontent.com/6189950/147770858-9dbc5b28-7e06-48ae-ab98-8a9c3477dd48.png) |  AliExpress: [link](https://www.aliexpress.com/store/group/Zigbee-smart-socket/4685106_10000001855269.html?spm=a2g0o.detail.100008.16.77b67a1e7L07Tx)) |
| Tuya Metering Plugs ![image](https://user-images.githubusercontent.com/6189950/147770322-e42d4f3f-47ff-4a21-9e90-3f3a98ba7241.png)  | Hubitat: https://community.hubitat.com/t/release-tuya-zigbee-metering-plug/86465 <br> AliExpress: TODO<br> Amazon: https://www.amazon.de/-/en/ZigBee-Wireless-Remote-Control-Monitor/dp/B09L4LB8HY |
| Lellki WK35 Plug Wall Socket EU BR IT JP FR IL <br> ![image](https://user-images.githubusercontent.com/6189950/167282480-825ad7cc-64fe-4bf9-b635-7168ee05690f.png) | Hubitat: https://community.hubitat.com/t/release-tuya-zigbee-metering-plug/86465 <br> AliExpress: https://www.aliexpress.com/item/4001243518512.html <br> |
| NEO ZigBee On Off Power Metering Plug <br> ![image](https://user-images.githubusercontent.com/6189950/189312590-ed18639c-fc4a-4d7e-b51e-2fcb1e47c922.png)  | Ultrasmart : [(link)](https://ultrasmart.pl/en_GB/p/NEO-ZigBee-On-Off-Power-Metering-Plug/81) | 
| Ajax / Zignito Zigbee Plug 13Amps with Energy Monitoring ![image](https://user-images.githubusercontent.com/6189950/189325364-856c3d3d-e4ed-4ca7-a288-b02a5d09ca18.png) |Amazon: https://www.amazon.co.uk/dp/B09N43BBC1?ref_=cm_sw_r_cp_ud_dp_YPHV2YS437AY5BXFVMSV |
| Plugue Zigbee 16a brasil br tomada de energia <br> ![image](https://user-images.githubusercontent.com/6189950/189314116-6600399f-3437-4376-9edd-4cb81fa1220e.png) | AliExpress:[(link)](https://pt.aliexpress.com/item/1005004019211620.html)| 
| 20A Tuya Zigbee Smart Plug Mini US Power Outlet <br> ![image](https://user-images.githubusercontent.com/6189950/170115134-3b8368a4-3098-46aa-93b3-9b3ee2df5d98.png) | AliExpress: [[link](https://www.aliexpress.com/item/1005004128965720.html)] <br> |
| Smart ZigBee Socket NOUS A1Z <br> ![image](https://user-images.githubusercontent.com/6189950/170118989-46f0757f-3afa-4d53-8096-25333b97b7f1.png) | Site: [https://nous.technology/product/a1z-1.html](url) |
| Tuya Smart Zigbee Plug AU 16A <br> ![image](https://user-images.githubusercontent.com/6189950/189305196-b51cd908-a341-4553-a250-0a92dda71c52.png)
 | [AliExpress](https://www.aliexpress.com/item/1005004505868292.html) <br>   |
 | Lellki Smart Wall Socket 220v <br> ![image](https://user-images.githubusercontent.com/6189950/189319812-375f24a8-829d-4ae5-9ffe-d1e6c1ce0975.png) | Alixpress: https://www.aliexpress.com/item/1005003642268555.html|
 | DIN rail metering switch <br> ![image](https://user-images.githubusercontent.com/6189950/189324623-fed488ca-2c3c-42a4-9dd9-0fb979e937dc.png) | AliExpress: https://www.aliexpress.com/item/1005002983983361.html |
|  Energy Meters  ![image](https://user-images.githubusercontent.com/6189950/147771140-a29c2401-9cd8-4e80-a777-93e0c5e183be.png)  | https://community.hubitat.com/t/release-tuya-zigbee-metering-plug/86465 <br> https://www.aliexpress.com/item/1005002409588154.html |
|  Circuit Breakers ![image](https://user-images.githubusercontent.com/6189950/154424284-7cd1eea2-b0b9-4a89-9634-9876e4445005.png) | https://community.hubitat.com/t/release-tuya-zigbee-metering-plug/86465 <br> https://fr.aliexpress.com/item/1005003726599840.html  |
| Tuya RC-MCB Circuit Breaker 2P <br> ![image](https://user-images.githubusercontent.com/6189950/232598234-b4a8515d-0610-4203-9efd-4489fde005ac.png)| Model:TS0601<br>Manufacturer: TZ3000_1hwjutgo TZ3000_lnggrqqi<br><br>https://www.aliexpress.com/item/1005003725997370.html |
| RTX Controlled fuse 4x 25A ZigBee TUYA <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/d6a52d21-602b-4772-b6ba-fadff7023837) | <br> RTX24.pl [link](https://www.rtx24.pl/bezpiecznik-sterowany-4x-25a-zigbee-tuya-p-476.html) |
| Tongou Zigbee Rail Switch MODEL TO-Q-SY1-JZT<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/12a461a5-f68d-43b1-a990-fb1dd0eb448f) | AliExpress: [(link)](https://www.aliexpress.com/item/1005004747066832.html)<br>| 
| Tongou Zigbee Rail Switch MODEL TO-Q-SY2-JZT<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/520855da-be8c-4faa-b674-3b69288a21cb)| AliExpress: [(link)](https://www.aliexpress.com/item/1005005444617697.html)<br>| 

----------------------------

### Tuya Temperature / Humidity / Illuminance Sensors
|  Device |  Links |
|---|---|
|  Temperarue and Humidity sensor w/ LCD display ![image](https://user-images.githubusercontent.com/6189950/154427503-86607778-9238-4a7f-b5fe-46b100a6a245.png) | https://community.hubitat.com/t/alpha-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock/88093 <br> https://www.aliexpress.com/item/1005001659657929.html <br> https://www.amazon.de/Ganata-Wireless-Temperature-Control-Humidity/dp/B095WXXSX4/ |
| Illiminance, Temperature, Humidity w/ Clock and LCD display ![image](https://user-images.githubusercontent.com/6189950/155202016-3c19afbb-62ce-406c-8486-89317567523a.png) | Amazon: https://www.amazon.de/-/en/Temperature-Humidity-Hygrometer-Thermometer-Greenhouse/dp/B091KVFFY1 <br> AliExpress: https://www.aliexpress.com/item/1005003374927532.html <br> |
| Illiminance, Temperature, Humidity w/ Clock and LCD display ![image](https://user-images.githubusercontent.com/6189950/155216114-ad3f0e7d-65b8-4dd5-ba99-d5fd94449068.png) | AliExpress: https://www.aliexpress.com/item/1005002989100446.html <br> Hubitat: https://community.hubitat.com/t/alpha-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock/88093 |
|  Illuminance, Temperature and Humidity Sensor With e-Ink diisplay  ![image](https://user-images.githubusercontent.com/6189950/155125337-0a97541e-2520-42f4-b89b-725fb423fa8b.png) | https://www.aliexpress.com/item/1005002535901726.html **<br> ATTENTION: Does not work stable! <br> Do not buy!**  |
| MOES Tuya ZigBee Smart Home Temperature And Humidity Sensor With LED Screen <br> ![image](https://user-images.githubusercontent.com/6189950/204976050-c4f9bb90-3d3e-40e5-b0d9-c3e54d924047.png) | Model:TS0201<br>Manufacturer: TZ3000_itnrsufe<br><br> FCC ID: [link](https://fccid.io/2A5TM-KCTW1Z/External-Photos/External-photos-5867957) <br> AliExpress: [link](https://www.aliexpress.us/item/3256804257182812.html) <br> Moes: [link](https://www.moeshouse.com/products/smart-zigbee-temperature-and-humidity-sensor-indoor-hygrometer-thermometer-detector) |
|Temperature and Humidity Sensor with LCD Display<br> ![image](https://user-images.githubusercontent.com/6189950/204976868-33c5c548-12b7-4b6b-9815-e428f5a0bea1.png) | Model:TS0601 <br> Manufactturer: TZE200_qoy0ekbd TZE200_znbl8dj5 <br>Supports: battery, temperature, humidity (not configurable)<br><br> AliExpress: [link](https://www.aliexpress.us/item/3256803873142843.html) <br>AliExpress: [link](https://www.aliexpress.us/item/3256803878221149.html) <br>  |
| Tuya Zigbee Temperature Humidity Soil Monitor<br> ![image](https://user-images.githubusercontent.com/6189950/210093610-e92bea4d-aac0-4c7f-8fdc-1e5789531b27.png) | AliExpress: [link1](https://www.aliexpress.com/item/1005004705106578.html)<br> AliExpress: [link2](https://www.aliexpress.com/item/1005004979025740.html) | 
| Tuya ZigbeeTemperature Humidity Sensor With Backlight ![image](https://user-images.githubusercontent.com/6189950/212531204-0cd25609-6914-44c0-8d41-9a14a2b8cca1.png) | Model: TS0601<br>Manufacturer: TZE200_whkgqxse <br><br>AliExpress: [link1](https://www.aliexpress.com/item/1005003980647546.html)| 
| Tuya Temperature and Humidity Sensor <br> ![image](https://user-images.githubusercontent.com/6189950/212731475-321bdb9b-69d4-4f53-ac6c-2d6588b6a67a.png) | Model: TS0201 <br> Manufacturer: TZ3000_bguser20 TZ3000_xr3htd96 <br> Supports: battery; humidity; temperature <br><br> AliExpress: [link1](https://www.aliexpress.com/item/1005003401903108.html) <br> AliExpress: [link2](https://www.aliexpress.com/item/1005004744650232.html) |
| SONOFF TH01 Temperature & Humidity sensor<br> ![image](https://user-images.githubusercontent.com/6189950/224310636-62ddf198-6f6b-4175-8523-282fc1f91604.png) | Features: TODO <br><br> itead .cc[link](https://itead.cc/product/sonoff-snzb-02-zigbee-temperature-and-humidity-sensor/) |
| SONOFF SNZB-02D Zigbee LCD Smart Temperature Humidity Sensor <br> ![image](https://user-images.githubusercontent.com/6189950/224309433-aa845332-065b-48db-b107-0c7a2f4c5902.png) | Features: C/F switch; TODO<br>Review: Youtube [link](https://www.youtube.com/watch?v=u0nB1olg72w)<br><br> itead .cc [link](https://itead.cc/product/sonoff-snzb-02d-zigbee-lcd-smart-temperature-humidity-sensor/) |
-------------------------------

### Tuya Other Sensors
|  Device |  Links |
|---|---|
|  Vibration Sensor ![image](https://user-images.githubusercontent.com/6189950/154428358-b61ad40e-9a17-4433-bcd4-b7fc2293d73c.png)  | https://www.aliexpress.com/item/1005002213730152.htm <br> https://community.hubitat.com/t/tuya-vibration-sensor/75269 <br> https://github.com/muxa/hubitat/blob/master/drivers/konke-zigbee-motion-sensor.groovy |
| Light/Brightness Sensor ![image](https://user-images.githubusercontent.com/6189950/154430215-deb4356c-77e4-4238-b02d-cf59f335a671.png)  | https://www.aliexpress.com/item/1005002070690444.html <br> https://community.hubitat.com/t/alpha-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock/88093  |
| Gas Smoke Carbon Dioxide Sensor Formaldehyde VOC Temperature Humidity Alarm Detector ![image](https://user-images.githubusercontent.com/6189950/155126946-59f14b00-207b-48c4-951c-efdf51484fd6.png) |  **ATTENTION: Spams the Zigbee network with packets every second! <br> Do not buy!** |
| |

------------------------------

### Tuya Buttons and Scene Switches
|  Device |  Links |
|---|---|
|  Scene Switch TS0044![image](https://user-images.githubusercontent.com/6189950/154430830-7ccdb0d5-1dd2-4772-8530-b1cda518f2bd.png) | https://www.aliexpress.com/item/1005001504737652.htm <br> https://community.hubitat.com/t/yagusmart-4-button-scene-switch/57237  |
|  Scene/Dimmer Switch  TS004F ![image](https://user-images.githubusercontent.com/6189950/154431871-34bc7f0c-795d-48b6-a7b6-3bcb7e492e0c.png)| https://www.amazon.de/-/en/Wireless-Switches-Controller-Automation-Scenarios/dp/B091HPX159 <br> https://community.hubitat.com/t/yagusmart-4-button-scene-switch/57237 <br> Hubitat inbuilt Tuya Scene Switch driver|
| Remote Control TS004F ![image](https://user-images.githubusercontent.com/6189950/155196487-0ff66665-510b-4fe7-984b-a21910dea4aa.png) | Amazon : https://www.amazon.de/-/en/Wireless-Portable-Required-Automation-Scenario/dp/B098L5XSD6 <br> Hubitat: https://community.hubitat.com/t/yagusmart-4-button-scene-switch/57237 |
|  Button TS0041 ![image](https://user-images.githubusercontent.com/6189950/155110780-3164615a-94e0-498b-aeb7-75c9d99dba08.png) |  https://www.aliexpress.com/item/1005002283147125.html <br> Hubitat inbuilt driver |
| ZigBee Scene Switch, 3 Buttons ![image](https://user-images.githubusercontent.com/6189950/155200952-c6d8dc64-ffc4-4581-96ed-661d41fbba9f.png)  | Amazon: https://www.amazon.de/-/en/ZigBee-Wireless-Buttons-Control-Batteries/dp/B0977KK7ZP  |
| LoraTap 6 Button Gang Scene Switch ![image](https://user-images.githubusercontent.com/6189950/166911477-960eddc5-b750-4bd1-a93e-25796ac1cb8d.png) | AliExpres: [(link)](https://www.aliexpress.com/item/1005003731082631.html) | 
| icasa Zigbee 3.0 wireless wall controller ![image](https://user-images.githubusercontent.com/6189950/198963909-f8d10968-166b-446c-86f3-fffb296d1e03.png)| https://www.amazon.co.uk/wireless-controller-Dimming-Compatible-Gateway/dp/B07L2SLY29?ref_=ast_sto_dp |
|Tuya 4 Key Arm Disarm Home SOS Button <br> ![image](https://user-images.githubusercontent.com/6189950/234899429-d24e6e2b-574b-4be2-aa49-256eae5acfef.png) | Model: TS0215<br>Manufacturers: _TYZB01_qm6djpta _TZ3000_fsiepnrh _TZ3000_p6ju8myv <br>AliExpress: [link](https://www.aliexpress.com/item/4001062612446.html)<br> | 
| Nedis Remote Control 4 Button<br> ![image](https://user-images.githubusercontent.com/6189950/234907606-99b2bd06-bb4b-41ec-9699-421d60716d54.png) | Model: TS0215A <br>Manufacturer: _TZ3000_fsiepnrh<br><br>Amazon. co. uk. [link](https://www.amazon.co.uk/dp/B087QSD3VC)<br>Nedis. com [link](https://nedis.com/en-us/product/smart-home/control/remote-control/550735173/smartlife-remote-control-zigbee-30-number-of-buttons-4-android-ios-white)| 
|Tuya ZigBee SOS Button<br> ![image](https://user-images.githubusercontent.com/6189950/234906596-32301d3f-c7ed-4ef5-8d17-d047079c2bf7.png) | Model: TS0215A<br>Manufacturers: _TZ3000_ug1vtuzn _TZ3000_0zrccfgx _TZ3000_p6ju8myv _TZ3000_4fsgukof _TZ3000_wr2ucaj9 _TZ3000_zsh6uat3 _TZ3000_tj4pwzzm _TZ3000_2izubafb _TZ3000_pkfazisv <br><br> AliExpress: [link](https://www.aliexpress.com/item/1005004126181157.html) | 
|OSRAM Smart+ Mini Switch<br> ![image](https://user-images.githubusercontent.com/6189950/234913121-f52e748e-0663-4ed1-9325-a4978dfd7590.png)|<b>Important</b>: first pair the switch using Hubitat inbuilt driver!<br><br>Model: Lightify Switch Mini<br>Manufacturer: OSRAM<br><br> Amazon UK: [link](https://www.amazon.co.uk/dp/B078CRB86M)<br>Amazon DE : [link](https://www.amazon.de/-/en/Ledvance-Motion-Smart-Home-Sensor/dp/B07SFZND8J)|
| Tradfri Shortcut Button <br> ![image](https://user-images.githubusercontent.com/6189950/234915739-d7fbbac2-7cdf-4351-8389-336cc49ff988.png)|Model: TRADFRI SHORTCUT Button E1812<br>Manufacturer: IKEA of Sweden<br><br> Ikea: [link](https://www.ikea.com/ca/en/p/tradfri-shortcut-button-white-smart-20356382/) 
| Loratap  4-Button Remote Control Scene Switch<br> ![image](https://user-images.githubusercontent.com/6189950/234919734-1126f84d-61a5-4fff-83b3-252752b96d25.png)|Model:TS0044  <br> Manufacturer: _TZ3000_b7bxojrg<br><br>AliExpress: [link](https://www.aliexpress.com/item/1005003017843535.html) | 
| Loratap 1-2-3-4 Portable Remote Control Scene Switch<br>![image](https://user-images.githubusercontent.com/6189950/234920744-2c627745-3418-4878-a96e-63cf39187d4c.png)| Model: TS0041 TS0042 TS0043 TS0044  <br>Manufacturer: _TZ3000_t8hzpgnd _TZ3000_ee8nrt2l _TZ3000_ufhtxr59<br><br>AliExpress: [link](https://www.aliexpress.com/item/1005003040683765.html)| 
| Tuya Smart Button <br> ![image](https://user-images.githubusercontent.com/6189950/234927721-31133787-0c07-4831-b31e-420bd486855c.png) | The shape, look and feel is like Aqara button!<br>Model: TS0041<br> Manufacturer: _TZ3000_fa9mlvja<br><br>AliExpress:[link](https://www.aliexpress.com/item/1005005363529624.html)|
| Tuya Multi-scene Switch Button<br> ![image](https://user-images.githubusercontent.com/6189950/234929898-a1b65f99-4eea-49bd-b25e-0047663daf5f.png)| Model: TS004F<br>Manufacturer: _TZ3000_ja5osu5g<br><br>AliExpress: [link](https://www.aliexpress.com/item/1005005274700227.html)|
| Tuya ZigBee SOS Button <br>![image](https://user-images.githubusercontent.com/6189950/234952936-c69f4e1a-6035-422d-a70c-05c1922f49bb.png)|Model: <br> Manufacturer: <br><br>AliExpress: [link](https://www.aliexpress.com/item/1005003347316228.html) | 
| Tuya Zigbee Smart Switch Button <br> ![image](https://user-images.githubusercontent.com/6189950/234956515-12848bf3-d2a3-4d55-8b67-7a49bf941b49.png)|Model: <br>Manufacturer: <br>Battery: rechargable 3.7V, capacity:  450mAh<br><br> AliExpress: [link](https://www.aliexpress.com/item/1005002283147125.html) |
------------------------------

### Tuya Bulbs and LED strips
|  Device |  Links |
|---|---|
|  E12/E14 color bulbs ![image](https://user-images.githubusercontent.com/6189950/154432389-18105d70-d9b4-4555-9437-1ff6dbdd79dc.png) | https://www.aliexpress.com/item/1005002312817236.html <br> Hubitat inbuilt Advanced Zigbee RGBW Bulb driver  |
|  E27 color bulbs ![image](https://user-images.githubusercontent.com/6189950/155110438-ac37c725-6e03-4e06-b111-b88d82a376ec.png)| Many different brands/manufacturers |
| LED strips Zigbee ![image](https://user-images.githubusercontent.com/6189950/155112052-21f6c8f5-fe7f-4edc-bcdb-a27380a4cc80.png) | Many different brands/manufacturers <br> https://www.aliexpress.com/item/1005002760490830.html <br> Inbuilt Hubitat driver|
|                                                                                                                                 |

---------------------------------

### Tuya Other Zigbee Devices

|  Device |  Links |
|---|---|
|  Tuya Zigbee Hub ![image](https://user-images.githubusercontent.com/6189950/154432781-9b55b4d4-132b-47f5-a910-8efe327158f5.png) |  https://community.hubitat.com/t/beta-tuya-cloud-driver-limited-device-support/80627 <br> https://www.aliexpress.com/item/4000560975293.html  <br> https://www.amazon.de/-/en/KETOTEK-Gateway-Wireless-Control-Compatible/dp/B08PF4ZYNY |
| Zigbee Repeater ![image](https://user-images.githubusercontent.com/6189950/155126018-ad7b0635-a62f-4608-8fe9-84eaf9d86c86.png) | Model: TS0207 <br> Aliexpress:  https://www.aliexpress.com/item/1005002460130292.html <br> Hubitat: https://community.hubitat.com/t/tuyago-zigbee-repeater/70465 <br> Driver: not needed|

--------------------------

### Tuya Siren / Alarms
|  Device |  Links |
|---|---|
| Tuya Zigbee Siren Alarm![image](https://user-images.githubusercontent.com/6189950/161398473-03ef138a-5152-41dd-9dc8-4db4cd10a8b4.png)  | AliExpress: https://www.aliexpress.com/item/1005004048262165.html <br> Hubitat: https://community.hubitat.com/t/tuya-smart-siren-zigbee-driver-doesnt-work <br> |
| Tuya Zigbee Siren with Temperature & Humidity Sensor <br> ![image](https://user-images.githubusercontent.com/6189950/210218070-ded69834-7ebc-42b6-8f69-af918db37baa.png)| AliXpress: [link1](https://www.aliexpress.com/item/4000687509921.html) <br> Walmart: [link](https://www.walmart.com/ip/Neo-Coolcam-ZigBee-Smart-Sensor-Alarm-Indoor-Temperature-Humidity-Sensor-Siren-Home-Smart-Alarm/161237851)  |
| Siren ![image](https://user-images.githubusercontent.com/6189950/155108281-fe16bc0f-35b5-4e83-a482-7f6cee6bca93.png)  | AliExpress: https://www.aliexpress.com/item/1005001776376752.html <br> Hubitat: https://community.hubitat.com/t/tuya-smart-siren-zigbee-driver-doesnt-work <br> <br> **ATTENTION: there are different variations of Tuya Sirens, some do not work with Hubitat (yet)!** <br> Possible working driver: https://community.hubitat.com/t/tuya-smart-siren-zigbee-driver-doesnt-work/73624/13 |
-----------------------------

### Tuya Roller Shade 
|  Device |  Links |
|---|---|
| Tuya Zigbee Roller Shade ![image](https://user-images.githubusercontent.com/6189950/155126440-28422b7a-606c-4f10-814f-1c4e5fc5601b.png) | Model: TS0601 <br> Manufaacturer: many brands <br> <br> https://www.aliexpress.com/item/1005001375412422.html <br> Hubitat: https://community.hubitat.com/t/release-zemismart-zigbee-blind-driver/67525 <br> Driver: https://github.com/amosyuen/hubitat/blob/main/zemismart/Zemismart%20Zigbee%20Blind.groovy <br> Hubitat Package Manager (HPM): "Zemismart Zigbee Blind" |

------------------------------

### Tuya Dimmers 
|  Device |  Links |
|---|---|
| Lonsonho Zigbee Smart Dimmer Switch Module With Neutral 1 2 Gang <br> ![image](https://user-images.githubusercontent.com/6189950/155127750-4c780918-fd6d-49d5-aca2-d3b27eb9bcea.png) |Profile: "TS110E_LONSONHO_DIMMER"<br><br>Manufacturer:_TZ3210_ngqk6jia _TZ3210_pagajpog _TZ3210_4ubylghk <br><br>AliExpress: [(link)](https://www.aliexpress.com/item/1005003012298844.html) <br> AliExpress: [(link)](https://www.aliexpress.com/item/4001279149071.html) <br>|
|Girier Tuya Zigbee Smart Dimmer Switch Module With Neutral 1 2 Gang<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/8272a145-9eb6-41cc-8913-8d8ea4162ce2)|Profile: "TS110E_GIRIER_DIMMER"<br><br>Manufacturer: _TZ3210_zxbtub8r _TZ3210_k1msuvg6<br><br> AliExpress: [(link)](https://www.aliexpress.com/item/1005004580552856.html)<br>  |
|Moes ZigBee Dimmer <br>![image](https://github.com/kkossev/Hubitat/assets/6189950/c55acff8-a081-450e-90bf-4865842cb4aa) |Profile: "TS110E_DIMMER"<br><br>Manufacturer: _TZE200_e3oitdyu<br><br>AliExpress: [(link)](https://www.aliexpress.com/item/1005001593033528.html) |
|Tuya Zigbee 3.0 Dimmer Smart Switch Module<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/1e99b8f3-1c8f-492d-a69e-f0d9e462e444)|Profile: "TS110F_DIMMER"<br><br>Manufacturer: _TYZB01_v8gtiaed _TYZB01_qezuin6k _TZ3000_ktuoyvt5 _TZ3000_92chsky7 _TZ3000_7ysdnebc <br><br> |
| Zigbee Multi-Gang Smart Light Dimmer <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/db56be87-7a78-4bc1-989a-ae7e49b09cef) |Profile: "TS0601_DIMMER"<br><br>Manufacturers: _TZE200_vm1gyrso _TZE200_whpb9yts _TZE200_9i9dt8is _TZE200_dfxkcots _TZE200_w4cryh2i _TZE200_ip2akl4w _TZE200_1agwnems _TZE200_la2c2uo9 _TZE200_579lguh2 _TZE200_fjjbhx9d _TZE200_drs6j6m5<br><br>AliExpress: [(link)](https://www.aliexpress.com/item/1005003571841927.html) |
|Smart Zigbee Dimmer Switch <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/424d9cc6-2a11-4c5e-8283-e6f68c2c26ca)|Profile: "TS0601_DIMMER"<br><br>Manufacturer: _TZE200_ebwgzdqq <br><br> Larkkey.com: [(link)](https://order.larkkey.com/gooddetail.html?id=63) |
| BSEED Zigbee Dimmer Switch<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/96b3b5f2-b72a-46da-9fec-95c12b8ef85a)|Profile: "TS0601_DIMMER"<br><br>Manufacturer: _TZE200_3p5ydos3 <br><br>Bseed: [(link)](https://www.bseed.com/collections/zigbee-series/products/bseed-eu-russia-new-zigbee-touch-wifi-light-dimmer-smart-switch)<br>AliExpress: [(link)](https://www.aliexpress.com/item/1005003391235290.html) <br>|
| Tuya Zigbee Smart In Wall Dimmer<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/fd4ea103-a198-4077-8c5d-8169a52e833c)| <br><br>Model: TS110F<br>Manufacturers: _TZ3210_lfbz816s _TZ3210_ebbfkvoy<br><br>AliExpress: [(link)](https://www.aliexpress.com/item/1005001810373201.html)| 
| Gledopo Triac AC Dimmer<<br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/b5e6d04c-1992-4122-8970-7d9e3f3deac8)|Profile: "OTHER_OEM_DIMMER"<br>Model: GL-SD-001<br>Manufcturer: GLEDOPTO<br><br>Gledopto: [(link)](https://www.gledopto.com/h-col-380.html)<br>AliExpress: [(link)](https://www.aliexpress.com/item/1005002120424028.html) <br>Amazon .com [(link)](https://www.amazon.com/GLEDOPTO-Dimmer-Control-Brightness-Compatible/dp/B091L1912K) <br>Amazon. de [link](https://www.amazon.de/-/en/Adjustable-Electronic-Transformer-Compatible-SmrtThings/dp/B09B99LVPJ)|
---------------------------

### Tuya Water Leak Sensors 
|  Device |  Links |
|---|---|
| NEO Coolcam Water Leak Sensor ![image](https://user-images.githubusercontent.com/6189950/160272275-4dfbbae7-60d9-445e-83a2-97d0dcd8b5ae.png) | Model: TS0601 <br> Manufacturer:  \_TZE200_qq9mpfhw <br> WalMart: https://www.walmart.com/ip/NEO-Tuya-ZigBee-Smart-Home-Water-Leak-Sensor-Wireless-Flooding-Detector-Leakage-Detection-Alert-Level-Overflow-Alarm-Life-App-Remote-Control-Works-Wi/374860039 <br> Hubitat: https://community.hubitat.com/t/tuya-water-sensor-aka-blitzwolf-paired-as-device/71765 <br> Driver: (GitHub [Link](https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20NEO%20Coolcam%20Zigbee%20Water%20Leak%20Sensor/Tuya%20NEO%20Coolcam%20Zigbee%20Water%20Leak%20Sensor.groovy)) |
| Water Leak Sensor ![image](https://user-images.githubusercontent.com/6189950/155128556-0871993b-14c6-4ab2-8646-1a0ba0db34f4.png) | Model: TS0207 <br> Manufacturer:  \_TYZB01_sqmd19i1 <br> AliExpres: https://www.aliexpress.com/item/1005002011778104.html <br> Amazon:https://www.amazon.de/-/en/TOOGOO-Wireless-Detector-Control-Compatible/dp/B08T9NP9NM <br> Hubitat: https://community.hubitat.com/t/tuya-water-sensor-aka-blitzwolf-paired-as-device/71765 <br> Driver: HE inbuilt "Generic Zigbee Moisture Sensor" |
| Water Leak Sensor ![image](https://user-images.githubusercontent.com/6189950/160271960-2b607c8d-e048-44b7-8976-7833d453947f.png) | Model: TS0207 <br> Manufacturer:  \_TZ3000_upgcbody <br> AliExpres: https://www.aliexpress.com/item/1005003926982178.html? <br>  |
|MEIAN Water Leak Sensor <br>  ![image](https://user-images.githubusercontent.com/6189950/201920407-0fd6ca0b-6f42-44aa-be0d-5ddc3fae8684.png) |  Model: TS0207 <br> Manufacturer: _TZ3000_kyb656no <br> AliExpress: [(link)](https://www.aliexpress.com/item/1005004662495000.html) |

----------------------------

### Valves
|  Device |  Links |
|---|---|
| Tuya ZigBee 3.0 Smart Gas Water Valve Controller <br> ![image](https://user-images.githubusercontent.com/6189950/164885487-70778b88-cb9c-444e-a9ec-5a1ce0fa8334.png) | AliExpress:   [(link)](https://www.aliexpress.com/item/4000334424893.html )<br> AliExpress: [(link)](https://www.aliexpress.com/item/1005002633228786.html) <br><br>Review: [(link)](https://investio.pro/review-smart-electric-actuator-of-water-gas-by-tuya/)<br>Review: [(link)](https://investio.pro/zigbee-actuator-to-control-water-and-gas/) | 
| Tuya Water Gas Shut Off Valve <br> ![image](https://user-images.githubusercontent.com/6189950/164885860-b081306d-af46-4d37-8f79-7dba509d6e92.png) | Amazon.de: [(link)](https://www.amazon.de/-/en/Intelligent-Control-Compatible-Assistant-Kitchen/dp/B097F3GWTN)  |
| ZIGBEE Sprinkler / Water Irrigation Valve <br> ![image](https://user-images.githubusercontent.com/6189950/213847222-6c40ccbb-d162-4cfa-9cd6-2d107aab6d11.png) |  Amazon  : [(link)](https://www.amazon.com/ZIGBEE-Sprinkler-Separate-Watering-Schedules/dp/B0B1JN6KZX) |
| SASWELL Irrigation Timer <br> ![image](https://user-images.githubusercontent.com/6189950/213847257-d841a3c6-fa9f-4c48-bec2-33ab2f8aa573.png)| Saswell: [link](https://www.saswell.com/smart-irrigation-wifi-water-timer-sas980swt-7-z01_p147.html) <br> Amazon: [link](https://www.amazon.com/SASWELL-Irrigation-Sprinkler-Programmable-SAS980SWT-7-Z01/dp/B09TP86CKY) |

----------------------------

### Tuya Light Switches 
|  Device |  Links |
|---|---|
| Push Button Wall Light Switch  ![image](https://user-images.githubusercontent.com/6189950/155199982-4dfc2d2b-3a7b-41c3-b229-643360584cb7.png) | Amazon: https://www.amazon.de/-/en/Intelligent-Neutral-Required-Gateway-Compatible/dp/B08LB9J82X <br>  |
| Moes 1,2,3,4 Gang Wall Touch Smart Light Switch ![image](https://user-images.githubusercontent.com/6189950/155881330-56f717a9-1abb-4f1a-b052-7c4412ecb9d1.png) | Model: TS0601 <br> Manufacturer: Moes <br><br> Moes: https://www.moeshouse.com/collections/tuya-zigbee-switch/products/zigbee-wall-touch-smart-light-switch-with-neutral-wire-no-neutral-wire-no-capacitor-needed-smart-life-tuya-2-3-way-muilti-control-association-hub-required-3-gang-white-eu <br><br> Hubitat: https://community.hubitat.com/t/uk-moes-zigbee-1-2-3-or-4-gang-light-switch/89747 <br> Driver: https://raw.githubusercontent.com/kkossev/hubitat-martinkura-svk-fork/main/Moes%20ZigBee%20Wall%20Switch |
| Zemismart **Brazil** Socket Smart Light Switch  ![image](https://user-images.githubusercontent.com/6189950/155833525-e20b92e5-4996-47a4-bc98-984e9f272b6e.png) | Model: TS0003 <br> Manufacturer: \_TZ3000_vjhcenzo <br><br>Zemismart: https://www.zemismart.com/products/tb25-zb <br> AliExpress: https://www.aliexpress.com/item/1005003822375489.html <br> Hubitat: https://community.hubitat.com/t/new-tuya-zigbee-wall-switches/89233/12 <br> Driver: https://raw.githubusercontent.com/kkossev/hubitat-muxa-fork/master/drivers/zemismart-zigbee-multigang-switch.groovy <br>  |

----------------------------

### Tuya TRVs
|  Device |  Links |
|---|---|
| Thermostat Radiator Valve Regulator (TRV)  ![image](https://user-images.githubusercontent.com/6189950/155201328-b6bffd0c-3861-4b71-8ff8-2334e31cb5c6.png) | Model: TS0601 <br> Manufacturer: many brands <br> AliExpress: https://www.aliexpress.com/item/1005002431040917.html <br> Amazon: https://www.amazon.de/-/en/Thermostat-Operated-Radiator-Regulator-Compatible/dp/B0972YM58R/ <br> Hubitat: https://community.hubitat.com/t/tuya-zigbee-trv/57787? <br> Driver: https://raw.githubusercontent.com/Mark-C-uk/Hubitat/master/Zigbee-Tuya-TRV |

----------------------------
### Tuya Thermostats
|  Device |  Links |
|---|---|
| Wall Mount Thermostat (Water/Electric Floor Heating)  ![image](https://user-images.githubusercontent.com/6189950/148385546-9e846840-8adb-4f3d-bbaf-41d549eab66f.png) | Model: TS0601 <br> Manufacturer: many different brands <br> <br> AliExpress: https://www.aliexpress.com/item/1005003575320865.html <br> Amazon.de (https://www.amazon.de/-/en/Thermostat-Temperature-Controller-Intelligent-Underfloor/dp/B09H6T9N9T?th=1 <br> <br> Hubitat: https://community.hubitat.com/t/beta-tuya-wall-mount-thermostat-water-electric-floor-heating-zigbee-driver/87050 <br> Driver: https://raw.githubusercontent.com/kkossev/Hubitat-Tuya-Wall-Thermostat/main/Tuya-Wall-Thermostat.groovy  |
|   |   |
|   |   |
|   |   |
|   |   |
