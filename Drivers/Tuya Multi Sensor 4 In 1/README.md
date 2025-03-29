
# The mmWave sensors support is moved from this driver to a new dedicated driver : 

https://community.hubitat.com/t/alpha-tuya-zigbee-mmwave-sensors-moving-the-code-from-the-tuya-4-in-1-driver/137410


----------------------------------------

This driver can be installed from Hubitat Community Package Manager ([HPM](https://community.hubitat.com/t/release-hubitat-package-manager-hubitatcommunity/94471)). Search for "Tuya Zigbee Multi-Sensor 4 In 1" or tag "Zigbee"

If you have already installed the driver manually, please do a **Match search in HPM** and then check for any updates.

The driver can be installed manually from: https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy 

Most of Tuya Motion sensors should work out of the box using HE inbuilt "Generic Zigbee Motion Sensor (no temp)" driver. However, there are some non-standard Tuya motion sensors which never send 'Motion Inactive' events to HE, or have additional capabilities, or allow specific parameters configuration, or simply do not follow Zigbee ZHA 1.2 standards. 

If your Tuya device did not work with the HE inbuilt drivers, simply changing to this driver is usually not enough. You need to pair the device again to HE hub, at a close distance (less than 1-2 meters). This driver should be selected automatically during the Zigbee pairing procedure. If not, most probably you have a new device model or manufacturer - please post the device details in this thread. The model and the manufacturer can be found on the Device Details - "Data' section. Please try to select. copy and paste these values as a text, not as a picture.


## Supported Passive InfraRed (PIR) sensors :

|  Device | Features| Links |
|---|---|---|
|  **Tuya ZigBee Multi-Sensor 4 in 1** <br> ![image](https://user-images.githubusercontent.com/6189950/163685629-86881a7a-0e3e-4b86-b568-16a3ff8c8fcd.png) |Zigbee ID: _TZ3210_zmy9hjay, _TYST11_i5j6ifxj, _TYST11_7hfcudw5<br><br>Price Range: Mid <br><br>Reports: Motion, Illuminance, Temperature, Humidity<br>Configuration: sensitivity, Keep Time, LED | AliExpress: [link](https://s.click.aliexpress.com/e/_DD2I7Ox)|
| | | |
| **Tuya ZigBee Multi-Sensor 3 in 1** <br> ![image](https://user-images.githubusercontent.com/6189950/163686024-e1de1fed-8c03-4729-a359-607654780992.png) | <br> Price range: Mid<br><br>Reports: Motion, Temperature, Humidity<br>Configuration:Sensitivity, Keep Time | AliExpress: [link](https://s.click.aliexpress.com/e/_DeNBxEF)<br>AliExpress: [link](https://s.click.aliexpress.com/e/_DD1Eamf)<br>|
| | | |
| **Tuya ZigBee Multi-Sensor 2 in 1** <br> ![image](https://user-images.githubusercontent.com/6189950/206842638-dc591556-74f6-44b3-8b01-060b644f30df.png) | model: TS0601<br>Manufactuer: TZE200_3towulqd <br>Battery: 2450<br>Reports: motion and illuminance<br>Configuration: sensitivity and rettrigger time (10/30/60/120)| <br> AliExpress: [link1](https://s.click.aliexpress.com/e/_DExiZcR)<br> AliExpress: [link2](https://s.click.aliexpress.com/e/_DEK0ptD)<br>AliExpress: [link3](https://s.click.aliexpress.com/e/_DEYbM1V) <br>AliExpress: [link4](https://s.click.aliexpress.com/e/_De3sEkL)<br>AliExpress: [link5](https://s.click.aliexpress.com/e/_DDP1AAJ)<br>| 
| | | |
| **TUYATEC RH3040 Motion Sensor** <br>![image](https://user-images.githubusercontent.com/6189950/155217683-4714c07e-939e-4c74-9ba5-fc59ccef98b1.png) | Reports: Motion, Battery<br> | <br>AliExpress: [link](https://s.click.aliexpress.com/e/_DFTUsrv )<br> AliExpress: [link](https://s.click.aliexpress.com/e/_DmXDeLp) <br>AliExpress: [link](https://s.click.aliexpress.com/e/_DmXDeLp)<br> AliExpress: [link](https://s.click.aliexpress.com/e/_Dl6imHN)<br>AliExpress: [link](https://s.click.aliexpress.com/e/_DmAbd2T)<br><br>Review:[(link)](https://investio.pro/zemismart-tuya-motion-sensor-for-smart-home-connection-to-home-assistant/) |
| | | |
|  **PIR Motion Sensor** <br> ![image](https://user-images.githubusercontent.com/6189950/155220379-41c88ccf-1d9a-4c7c-b00a-cc5a67d69cb1.png) | Reports: Motion, Battery<br><br> | AliExpress: [link1](https://s.click.aliexpress.com/e/_DEP1kkL)<br>AliExpress:  [link2](https://s.click.aliexpress.com/e/_Debje6R)<br>AliExpress:  [link3](https://s.click.aliexpress.com/e/_DmZLfsr) <br> |
| | | |
|**Tuya ZigBee PIR Motion Sensor** <br> ![image](https://user-images.githubusercontent.com/6189950/180646422-4db28a30-a67f-4eb9-a076-bad21951691b.png) | Reports: Motion, Battery<br><br> Zigbee ID: TS0202  _TZ3000_bsvqrxru <br> | AliExpress: [link](https://s.click.aliexpress.com/e/_DFEi7cB)<br>AliExpress: [link](https://s.click.aliexpress.com/e/_DDl6a2r)<br> |
| | | |
|  **PIR sensor TS0202** <br>![image](https://user-images.githubusercontent.com/6189950/154433141-af9f57a4-0aad-4405-bcbd-1155da268a06.png) | Reports: Motion, Battery<br> | <br>AliExpress: [link](https://s.click.aliexpress.com/e/_DmoJc0T)<br>AliExpress: [link](https://s.click.aliexpress.com/e/_DcJS3BN)<br>AliExpress: [link](https://s.click.aliexpress.com/e/_DDRdMYj)<br>AliExpress: [link](https://s.click.aliexpress.com/e/_Dd6lDHZ)<br>AliExpress: [link](https://s.click.aliexpress.com/e/_DBx8gOB) <br> |
| | | |
| **Tuya  Ceiling-mounted Human Presence Sensor AIR** <br>![image](https://user-images.githubusercontent.com/6189950/167108614-63b33209-50a4-49fa-b185-32c375e92e10.png) | Zigbee ID: "TS0601"; "__TZE200_auin8mzr" <br><br>Price range: mid  <br>Driver status: fully operational<br>  | AliExpress: [(link)](https://s.click.aliexpress.com/e/_Dk2id9p) <br>|
| | | |
| **PIR Motion Sensor Detector With Light Sensor Scene Switch** <br> ![image](https://github.com/kkossev/Hubitat/assets/6189950/386c4047-5dd0-44d7-9fca-0d6eea98bbaf)| Model : TS0202 <br>Manufacturer : _TZ3210_cwamkvua <br><br> | AliExpres: [link](https://s.click.aliexpress.com/e/_DBt1Guj)<br>AliExpress: [link](https://s.click.aliexpress.com/e/_DDELb39)<br>|
| | | |

----------------------------

## Compatibility

* Tuya specific cluster 0xEF00 PIR sensors
Note: currently, the driver is tested only with :
  *  model:"TS0202" PIR sensors
  *  model:"TS0601" PIR sensors
  * model: "TS0601" Microwave Radars
* IAS cluster 0x0500 sensors
  *  model:"TS0202" - various manufacturers
  * model:"RH3040" - various manufacturers
* Tuya new millimeter wave  radars Human Presence sensor support
-----------------------------------
## Features

* Motion detection
* Temperature
* Humidity
* Illuminance (4in1 models )
* Battery reporting (for most of the models)
* Tamper Alert (4in1 and 3in1 only)
* Reset Motion to Inactive software timer
* PIR sensor sensitivity configuration (for some models)
* Keep Time configuration (for some models)
* New Tuya radars 'Human Presence' support and specific configurations
------------------

### REVISIONS HISTORY:

* ver. 1.0.0 2022-04-16 - Inital test version
* ver. 1.0.1 2022-04-18 - IAS cluster multiple TS0202, TS0210 and RH3040 Motion Sensors fingerprints; ignore repeated motion inactive events
* ver. 1.0.2 2022-04-19  - setMotion command; state.HashStringPars; advancedOptions: ledEnable
* ver. 1.0.3 2022-05-05 - added  '_TZE200_ztc6ggyl' 'Tuya ZigBee Breath Presence Sensor' ; Illuminance unit changed to 'lx'
* ver. 1.0.4 2022-05-06 - added isHumanPresenceSensorAIR(); isHumanPresenceSensorScene(); isHumanPresenceSensorFall(); convertTemperatureIfNeeded
* ver. 1.0.5 2022-06-11 - _TZE200_3towulqd +battery; 'Reset Motion to Inactive' made explicit option; sensitivity and keepTime for IAS sensors (TS0202-tested OK) and TS0601(not tested); capability "PowerSource" used as presence 
* ver. 1.0.6 2022-07-10 - battery set to 0% and motion inactive when the device goes OFFLINE;
* ver. 1.0.7 2022-07-16  - _TZE200_ikvncluo (MOES) and _TZE200_lyetpprm radars; scale fadingTime and detectionDelay by 10; initialize() will resets to defaults; radar parameters update bug fix; removed unused states and attributes for radars
* ver. 1.0.8 2022-07-24   _TZE200_auin8mzr (HumanPresenceSensorAIR) unacknowledgedTime; setLEDMode; setDetectionMode commands and  vSensitivity; oSensitivity, vacancyDelay preferences; _TZE200_9qayzqa8 (black sensor) Attributes: motionType; preferences: inductionTime; targetDistance.
 * ver. 1.0.9 2022-08-11 kkossev  - degrees Celsius symbol bug fix; added square black radar _TZE200_0u3bj3rc support, temperatureOffset bug fix; decimal/number type prferences bug fix
*  ver 1.0.10 2022-08-15 - (dev. branch) - added Lux threshold parameter; square black radar LED configuration is resent back when device is powered on; round black PIR sensor powerSource is set to DC; added OWNON OCP305 Presence Sensor
* ver. 1.0.11 2022-08-22 - IAS devices initialization improvements; presence threshold increased to 4 hours; 3in1 exceptions bug fixes; 3in1 and 4in1 exceptions bug fixes;
* ver. 1.0.12 2022-09-05 - added _TZE200_wukb7rhc MOES radar
* ver. 1.0.13 2022-09-14 - added _TZE200_jva8ink8 AUBESS radar; 2-in-1 Sensitivity setting bug fix
* ver. 1.0.14 2022-10-31 - added Bond motion sensor ZX-BS-J11W fingerprint for tests
* ver. 1.0.15 2022-12-03 - OWON 0x0406 cluster binding; added _TZE204_ztc6ggyl _TZE200_ar0slwnd _TZE200_sfiy5tfs _TZE200_mrf6vtua (was wrongly 3in1) mmWave radards;
 * ver. 1.1.0  2022-12-25 - SetPar() command;  added 'Send Event when parameters change' option; code cleanup; added _TZE200_holel4dk; added 4-in-1 _TZ3210_rxqls8v0, _TZ3000_6ygjfyll, _TZ3210_wuhzzfqg
 * ver. 1.1.1  2023-01-07 - illuminance event bug fix; fadingTime minimum value 0.5; SetPar command shows in the UI the list of all possible parameters; _TZ3000_6ygjfyll bug fix;
 * ver. 1.2.0  2023-02-06- **[healthStatus](https://community.hubitat.com/t/devicepresent-capability/89774/18?u=kkossev)**; supressed repetative Radar detection delay and Radar fading time Info messages in the logs; logsOff missed when hub is restarted bug fix; capability 'Health Check'; _TZE200_3towulqd (2in1) new firmware versions fix for motion and battery %; 
 * ver. 1.2.1  2023-02-10 - reverted the unsuccessful changes made in the latest 1.2.0 version for _TZE200_3towulqd (2in1); added _TZE200_v6ossqfy as BlackSquareRadar; removed the wrongly added TUYATEC T/H sensor...
 * ver. 1.2.2  2023-03-18   - typo in a log transaction fixed; added TS0202 _TZ3000_kmh5qpmb as a 3-in-1 type device'; added _TZE200_xpq2rzhq radar; bug fix in setMotion()
 * ver. 1.3.0  2023-03-22 kkossev  -'_TYST11_7hfcudw5' moved to 3-in-1 group; **added deviceProfiles**; fixed initializaiton missing on the first pairing; added batteryVoltage; added tuyaVersion; added delayed battery event; removed state.lastBattery; caught sensitivity par exception; fixed forcedProfile was not set automatically on Initialize; 
* ver. 1.3.1  2023-03-29 - added 'invertMotion' option; 4in1 (Fantem) Refresh Tuya Magic; invertMotion is set to true by default for _TZE200_3towulqd;
* ver. 1.3.2  2023-04-17 - 4-in-1 parameter for adjusting the reporting time; supressed debug logs when ignoreDistance is flipped on; 'Send Event when parameters change' parameter is removed (events are always sent when there is a change); fadingTime and detectionDelay change was not logged and not sent as an event;
 * ver. 1.3.3  2023-05-14 - code cleanup; added TS0202 _TZ3210_cwamkvua [Motion Sensor and Scene Switch]; added _TZE204_sooucan5 radar in a new TS0601_YXZBRB58_RADAR group (for tests); added reportingTime4in1 to setPar command options;
 * ver. 1.3.4  2023-05-19 - added _TZE204_sxm7l9xa mmWave radar to TS0601_YXZBRB58_RADAR group; isRadar() bug fix;
 * ver. 1.3.5  2023-05-28 - fixes for _TZE200_lu01t0zlTS0601_RADAR_MIR-TY-FALL mmWave radar (only the basic Motion and radarSensitivity is supported for now).
ver. 1.3.6  2023-06-25 - chatty radars excessive debug logging bug fix.
* ver. 1.3.7  2023-07-19 - fixes for _TZE204_sooucan5; moved _TZE204_sxm7l9xa to a new Device Profile TS0601_SXM7L9XA_RADAR; added TS0202 _TZ3040_bb6xaihh _TZ3040_wqmtjsyk;
* ver. 1.4.0  2023-08-06  - added new TS0225 _TZE200_hl0ss9oa 24GHz radar (TS0225_HL0SS9OA_RADAR); added  basic support for the new TS0601 _TZE204_sbyx0lm6 radar w/ relay; added Hive MOT003; added sendCommand; added TS0202 _TZ3040_6ygjfyll
 * ver. 1.4.1  2023-08-14 -  TS0225_HL0SS9OA_RADAR ignoring ZCL illuminance and IAS motion reports; added radarAlarmMode, radarAlarmVolume, radarAlarmTime, Radar Static Detection Minimum Distance; added TS0225_AWARHUSB_RADAR TS0225_EGNGMRZH_RADAR 
  * ver. 1.4.2  2023-08-15 - 'Tuya Motion Sensor and Scene Switch' driver clone (Button capabilities enabled)
 * ver. 1.4.3  2023-08-17 - TS0225 _TZ3218_awarhusb device profile changed to TS0225_LINPTECH_RADAR; cluster 0xE002 parser; added TS0601 _TZE204_ijxvkhd0 to TS0601_IJXVKHD0_RADAR; added _TZE204_dtzziy1e, _TZE200_ypprdwsl _TZE204_xsm7l9xa; YXZBRB58 radar illuminance and fadingTime bug fixes; added new TS0225_2AAELWXK_RADAR profile
 * ver. 1.4.4  2023-08-18 - Method too large: Script1.processTuyaCluster ... :( TS0225_LINPTECH_RADAR: myParseDescriptionAsMap & swapOctets(); deleteAllCurrentStates(); TS0225_2AAELWXK_RADAR preferences configuration and commands; added Illuminance correction coefficient; code cleanup
*  **ver. 1.5.0**  2023-08-27 - added TS0601 _TZE204_yensya2c radar; refactoring: deviceProfilesV2: tuyaDPs; unknownDPs; added _TZE204_clrdrnya; _TZE204_mhxn2jso; 2in1: _TZE200_1ibpyhdc, _TZE200_bh3n6gk8; added TS0202 _TZ3000_jmrgyl7o _TZ3000_hktqahrq _TZ3000_kmh5qpmb _TZ3040_usvkzkyn; added TS0601 _TZE204_kapvnnlk new device profile TS0601_KAPVNNLK_RADAR
 * ver. 1.5.1  2023-09-09 - _TZE204_kapvnnlk fingerprint and DPs correction; added 2AAELWXK preferences; TS0225_LINPTECH_RADAR known preferences using E002 cluster
 * ver. 1.5.2  2023-09-14 - TS0601_IJXVKHD0_RADAR ignore dp1 dp2; Distance logs changed to Debug; Refresh() updates driver version; 
 * ver. 1.5.3  2023-09-30 kkossev  - humanMotionState re-enabled for TS0225_HL0SS9OA_RADAR; tuyaVersion is updated on Refresh; LINPTECH: added existance_time event; illuminance parsing exception changed to debug level; leave_time changed to fadingTime; fadingTime configuration
 * ver. 1.6.0  2023-10-08 - (dev. branch) major refactoring of the preferences input; all preference settings are reset to defaults when changing device profile; added 'all' attribute; present state 'motionStarted' in a human-readable form.  setPar and sendCommand major refactoring +parameters changed from enum to string; TS0601_KAPVNNLK_RADAR parameters support; 
 * ver. 1.6.1  2023-10-12 kkossev  - (dev. branch) TS0601_KAPVNNLK_RADAR TS0225_HL0SS9OA_RADAR TS0225_2AAELWXK_RADAR TS0601_RADAR_MIR-HE200-TY TS0601_YXZBRB58_RADAR TS0601_SXM7L9XA_RADAR TS0601_IJXVKHD0_RADAR TS0601_YENSYA2C_RADAR TS0601_SBYX0LM6_RADAR TS0601_PIR_AIR TS0601_PIR_PRESENCE refactoring; radar enum preferences;
* ver. 1.6.2  2023-10-14 - (dev. branch) LINPTECH preferences changed to enum type; enum preferences - set defaultValue; TS0601_PIR_PRESENCE - preference inductionTime changed to fadingTime, humanMotionState sent as event; TS0225_2AAELWXK_RADAR - preferences setting; _TZE204_ijxvkhd0 fixes; Linptech fixes; added radarAlarmMode radarAlarmVolume;
* ver. 1.6.3  2023-10-15 - (dev. branch) setPar() and preferences updates bug fixes; automatic fix for preferences which type was changed between the versions, including bool;
* ver. 1.6.4  2023-10-18 - (dev. branch) added TS0601 _TZE204_e5m9c5hl to SXM7L9XA profile; added a bunch of new manufacturers to SBYX0LM6 profile;
* ver. 1.6.5  2023-10-23 - (dev. branch) bugfix: setPar decimal values for enum types; added SONOFF_SNZB-06P_RADAR; added SIHAS_USM-300Z_4_IN_1; added SONOFF_MOTION_IAS; TS0202_MOTION_SWITCH _TZ3210_cwamkvua refactoring; luxThreshold hardcoded to 0 and not configurable!; do not try to input preferences of a type bool; TS0601_2IN1 refactoring; added keepTime and sensitivity attributes for PIR sensors; added _TZE200_ppuj1vem 3-in-1; TS0601_3IN1 refactoring; added _TZ3210_0aqbrnts 4in1;
* ver. 1.6.6  2023-11-02 -  _TZE204_ijxvkhd0 staticDetectionSensitivity bug fix; SONOFF radar clusters binding; assign profile UNKNOWN for unknown devices; SONOFF radar cluster FC11 attr 2001 processing as occupancy; TS0601_IJXVKHD0_RADAR sensitivity as number; number type pars are scalled also!; _TZE204_ijxvkhd0 sensitivity settings changes; added preProc function; TS0601_IJXVKHD0_RADAR - removed multiplying by 10
 * ver. 1.6.7  2023-11-09 - divideBy10 fix for TS0601_IJXVKHD0_RADAR; added new TS0202_MOTION_IAS_CONFIGURABLE group
 * ver. 1.6.8  2023-11-20 kkossev  - SONOFF SNZB-06P RADAR bug fixes; added radarSensitivity and fadingTime preferences; update parameters for Tuya radars bug fix;
 * ver. 1.7.0  2024-01-14 kkossev  -  Groovy linting; added TS0225_O7OE4N9A_RADAR TS0225 _TZFED8_o7oe4n9a for tests; TS0601 _TZE200_3towulqd new fingerprint @JdThomas24
 * ver. 1.8.0  2024-03-23 kkossev  -  more Groovy linting; fixed 'This driver requires HE version 2.2.7 (May 2021) or newer!' bug; device.latestState('battery') exception bug fixes;
* ver. 1.8.1  2024-04-16 kkossev  -  tuyaDPs list of maps bug fixes; added _TZE204_kyhbrfyl; added smallMotionDetectionSensitivity;
 * **ver. 1.9.0  2024-05-06 - deprecated all radars except Linptech;**
  * ver. 1.9.1  2024-05-25 - preferences are not sent for depricated devices.
 * ver. 1.9.2  2024-06-15 -  deviceProfile drop-down list bug fix; added quickRef link to GitHub WiKi page for PIR sensors;

--------------------
 * ver. 3.2.0  2024-05-26 - first version, based on the mmWave radar driver code : depricated Linptech; added TS0202 add _TYZB01_vwqnz1sn; 
 * ver. 3.2.1  2024-05-31 - commonLib ver 3.2.1 allignment; tested 2In1 _TZE200_3towulqd ; new device profile group 'RH3040_TUYATEC'; SiHAS; 
 * ver. 3.2.2  2024-07-05 - created motionLib; restored 'all' attribute
 * ver. 3.2.3  2024-07-27   - added Sonoff SNZB-03P
 * ver. 3.3.0  2024-08-30 - main branch release.
 * ver. 3.3.1  2024-10-26 - added TS0601 _TZE200_f1pvdgoh into a new device profile group 'TS0601_2IN1_MYQ_ZMS03'
 * ver. 3.3.2  2024-11-30 - added Azoula Zigbee 4 in 1 Multi Sensor model:'HK-SENSOR-4IN1-A', manufacturer:'Sunricher' into SIHAS group
 * ver. 3.3.3  2025-01-29 - (dev. branch) TS0601 _TZE200_ppuj1vem moved to 'TS0601_2IN1_MYQ_ZMS03' deviceProfile @ltdonjohnson


------------------


The development branch version that contains the latest additions and bug fixes can be manually downloaded from here: https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy

An archive containing older driver versions is available [here](https://drive.google.com/drive/folders/12zpEDg7phCT9wCnBhLMaPoIKu3jOO6Nm?usp=share_link) .


------------------

