# Tuya Zigbee Valve

Although the different Tuya branded Zigbee valves may look the same, they are produced by different manufacturers and have different Zigbee commands implemented.
Most of the Tuya valves should work in HE stright away, using the inbuilt **Sinope Water Valve** driver.

In the cases when Sinope Water Valve driver does not work for your model, you can try this driver that is intended to provide support for several different Tuya modles.

The recommended installation method is to use the community Hubitat Package Manager (HPM) app. Search for "**Tuya Zigbee Valve**" or by tag 'Zigbee'.
The driver can be also installed manually from this link : https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Valve/Tuya%20Zigbee%20Valve.groovy

## Supported models

|  Device |  Links |
|---|---|
|  Tuya ZigBee 3.0 Smart Gas Water Valve Controller  ![image](https://user-images.githubusercontent.com/6189950/164885487-70778b88-cb9c-444e-a9ec-5a1ce0fa8334.png) <br><br>Profile: TS0001_VALVE_ONOFF, TS0011_VALVE_ONOFF, TS011F_VALVE_ONOFF <br>Model: TS0001<br>Manufacturer: _TZ3000_iedbgyxt _TZ3000_o4cjetlm |Features: On/Off; power-on-behaviour; <br>Power supply: 12V DC  <br><br>AliExpress:   [(link)](https://www.aliexpress.com/item/4000334424893.html )<br> AliExpress: [(link)](https://www.aliexpress.com/item/1005002633228786.html) <br><br>Review: [(link)](https://investio.pro/review-smart-electric-actuator-of-water-gas-by-tuya/)<br>Review: [(link)](https://investio.pro/zigbee-actuator-to-control-water-and-gas/) <br><br><b>WARNING: some manufacturers valves, such as _TZ3000_5ucujjts may not work with some HE hubs!</b> | 
| Tuya Water Gas Shut Off Valve  ![image](https://user-images.githubusercontent.com/6189950/164885860-b081306d-af46-4d37-8f79-7dba509d6e92.png) <br><br>Profile: TS0601_VALVE_ONOFF<br>Model: TS0601<br>Manufacturer: _TZE200_vrjkcam9 _TZE200_d0ypnbvn _TZE200_yxcgyjf1 | <b>CAUTION: SPAMMY DEVICE! </b> (sends open/close state packets every 3 seconds to HE hub!)<br><br>Features: On/Off, Power-on behaviour<br>Power supply: 12V DC<br><br>Amazon .de: [(link)](https://www.amazon.de/-/en/Intelligent-Control-Compatible-Assistant-Kitchen/dp/B097F3GWTN) <br> AliExpress: [(link)](https://www.aliexpress.com/item/1005003074109888.html) |
| SASWELL Irrigation Timer <br> ![image](https://user-images.githubusercontent.com/6189950/213847257-d841a3c6-fa9f-4c48-bec2-33ab2f8aa573.png) <br><br>Profile: TS0601_SASWELL_VALVE<br>Model: TS0601<br>Manufacturer: _TZE200_81isopgh _TZE200_akjefhj5 _TZE200_2wg5qrjy| User Manual: [link](https://fccid.io/2AOIFSAS980SWT/User-Manual/User-Manual-5361734.pdf)<br><br>Features: On/Off, Timer state/time left, Weather delay, Battery %, Water consumption<br>Power supply: batteries<br><br>Saswell: [link](https://www.saswell.com/smart-irrigation-wifi-water-timer-sas980swt-7-z01_p147.html) <br> Amazon: [link](https://www.amazon.com/SASWELL-Irrigation-Sprinkler-Programmable-SAS980SWT-7-Z01/dp/B09TP86CKY) <br> Domadoo .fr : [link](https://www.domadoo.fr/fr/peripheriques/5886-saswell-electrovanne-et-programmateur-d-arrosage-connecte-zigbee-mesure-de-consommation.html) <br> Domotique .fr : [link ](https://www.domotique-store.fr/domotique/usages/arrosage-domotique-automatique-a-distance/1536-electrovanne-programmateur-d-arrosage-automatique-connecte-zigbee-compatible-app-smartphone-tuya-smart-life-et-lidl-home.html) |
| GiEX Sprinkler / Water Irrigation Valve <br>  ![image](https://user-images.githubusercontent.com/6189950/213847222-6c40ccbb-d162-4cfa-9cd6-2d107aab6d11.png) <br><br>Profile: TS0601_IRRIGATION_VALVE<br>Model: TS0601<br>Manufacturer: _TZE200_sh1btabb | Features: On/Off, Duration/Capacity modes, Timer state, Water consumed, Battery %<br>Power supply: batteries<br> Amazon  : [(link)](https://www.amazon.com/ZIGBEE-Sprinkler-Separate-Watering-Schedules/dp/B0B1JN6KZX) |
| PARKSIDE® Smart Irrigation Computer<br> ![image](https://user-images.githubusercontent.com/6189950/221957269-9e40f908-d66b-409f-93a8-5fa5f0ee37c1.png) <br><br>Profile: TS0601_LIDL_VALVE<br>Model: TS0601<br>Manufacturer: _TZE200_htnnfasr _TZE200_c88teujp |CSA: [link](https://csa-iot.org/csa_product/lidl-home-smarter-water-computer/)<br><br>Features: On/Off, Timer, Battery %<br>Driver status: <b>need testers</b><br>Power supply: batteries<br><br>LIDL. de : [link](https://www.lidl.de/p/parkside-smarter-bewaesserungscomputer-zigbee-smart-home/p100325201) |
| Rain Seer Garden Home Irrigation Watering Timer <br> ![image](https://community.hubitat.com/uploads/default/original/3X/0/7/07d2cf9695c29177a20df60137c904eaa4f82f1c.jpeg) <br><br>Profile: TS0049_IRRIGATION_VALVE<br>Model: TS0049<br>Manufacturer: _TZ3210_0jxeoadc _TZ3000_hwnphliv | Features: On/Off, Irrigation duration, Battery state (high/mid/low)<br>Power supply: batteries<br><br> [AliExpress .us link](https://s.click.aliexpress.com/e/_DmZGnDz) |
| FrankEver FK_V02 Smart Water Valve <br><br>Profile: TS0601_FRANKEVER_FK_V02<br>Model: TS0601<br>Manufacturer: _TZE200_1n2zev06 _TZE200_5uodvhgc _TZE200_wt9agwf3 | Features: On/Off, Valve open threshold, Valve open percentage, Irrigation timer, Battery %<br>Power supply: batteries |
| SONOFF SWV Smart Water Valve <br><br>Profile: SONOFF_SWV_VALVE<br>Model: SWV<br>Manufacturer: SONOFF | Features: On/Off, Flow rate, Water consumption, Auto shut-off (fw 1.0.4+), Valve fault status, Battery %<br>Power supply: batteries |
| SONOFF SWV-ZN Series (SWV-ZNE, SWV-ZFE, SWV-ZNU, SWV-ZFU) <br><br>Profile: SONOFF_SWV_ZN_VALVE<br>Model: SWV-ZN variants<br>Manufacturer: SONOFF | Features: On/Off, Irrigation schedule status, Manual irrigation defaults, Duration/Capacity modes, Battery %<br>Power supply: batteries |
| SONOFF SWV-ZF2 Double Valve <br><br>Profile: SONOFF_SWV_ZF2_DOUBLE_VALVE<br>Model: SWV-ZF2<br>Manufacturer: SONOFF | Features: On/Off (dual channels), Manual irrigation defaults, Component child devices, Duration/Capacity modes, Battery %<br>Power supply: batteries<br><br><b>NOTE:</b> Requires component child driver (v1.8.0+) |
| Tuya TZE284 Double Valve (GiEX GX-03ZG, Insoma SGW08W, MUCIAKiE) <br><br>Profile: TS0601_TZE284_VALVE<br>Model: TS0601<br>Manufacturer: _TZE284_8zizsafo _TZE284_eaet5qt5 _TZE284_fhvpaltk | Features: On/Off (dual valves), Per-valve countdown timers, Water consumption, Battery %<br>Power supply: batteries |

---

## Compatibility

* TS0001, TS0011, TS011F : Tuya on/off valves
* TS0601 : Tuya specific cluster 0xEF00 valves (GiEX, Saswell, Lidl, FrankEver, TZE284)
* TS0049 : Rain Seer irrigation valve
* SONOFF SWV / SWV-ZN / SWV-ZF2 : SONOFF smart water valves with custom 0xFC11 cluster
* Standard Zigbee 3.0 (ZHA 1.2) valves

---

## Features

* Standard "Valve" capability plus mirrored "Switch" for Alexa/Google Home/HomeKit
* **healthStatus** (online/offline)
* Battery reporting for battery-powered models
* Auto-off (irrigation duration) timer
* Irrigation capacity and mode (duration/capacity)
* Irrigation start/end times, last duration, water consumed (model-dependent)
* SONOFF specifics: flow rate, valve fault status, auto shut-off, manual irrigation defaults, dual-valve child devices (SWV-ZF2)
* Device profile auto-detection with manual override
* Debug / description-text logging (debug auto-off after 24 h)

-----
(last edited 2026-07-10)