# Tuya Zigbee Valve

Although the different Tuya branded Zigbee valves may look the same, they are produced by different manufacturers and have different Zigbee commands implemented.
Most of the Tuya valves should work in HE stright away, using the inbuilt **Sinope Water Valve** driver.

In the cases when Sinope Water Valve driver does not work for your model, you can try this driver that is intended to provide support for several different Tuya modles.

The recommended installation method is to use the community Hubitat Package Manager (HPM) app. Search for "**Tuya Zigbee Valve**" or by tag 'Zigbee'.
The driver can be also installed manually from this link : https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Valve/Tuya%20Zigbee%20Valve.groovy

<!-- BEGIN SUPPORTED-MODELS (generated from DEVICES.md — edit DEVICES.md, then run /update-readme-devices render) -->
## Supported models

|  Device |  Links |
|---|---|
| Tuya ZigBee 3.0 Smart Gas Water Valve Controller ![image](https://user-images.githubusercontent.com/6189950/164885487-70778b88-cb9c-444e-a9ec-5a1ce0fa8334.png)<br><br><b>WARNING: some manufacturers valves, such as _TZ3000_5ucujjts may not work with some HE hubs!</b> | Profile: TS0001_VALVE_ONOFF, TS0011_VALVE_ONOFF, TS011F_VALVE_ONOFF <br>Model: TS0001, TS0011, TS011F<br>Manufacturer: _TZ3000_iedbgyxt _TZ3000_o4cjetlm _TYZB01_4tlksk8a _TZ3000_h3noz0a5 _TZ3000_5ucujjts _TYZB01_rifa0wlb _TYZB01_ymcdbl3u _TZ3000_rk2yzt0u <br><br>Features: On/Off; power-on behaviour<br>Power supply: 12V DC <br><br>AliExpress: [(link)](https://s.click.aliexpress.com/e/_c3curWAF)<br>AliExpress: [(link)](https://s.click.aliexpress.com/e/_c3DG5pqR) |
| Tuya Water Gas Shut Off Valve ![image](https://user-images.githubusercontent.com/6189950/164885860-b081306d-af46-4d37-8f79-7dba509d6e92.png)<br><br><b>CAUTION: SPAMMY DEVICE!</b> (sends open/close state packets every 3 seconds to HE hub!) | Profile: TS0601_VALVE_ONOFF<br>Model: TS0601<br>Manufacturer: _TZE200_vrjkcam9 _TZE200_yxcgyjf1 _TZE200_d0ypnbvn <br><br>Features: On/Off; power-on behaviour<br>Power supply: 12V DC<br><br>Amazon .de: [(link)](https://www.amazon.de/-/en/Intelligent-Control-Compatible-Assistant-Kitchen/dp/B097F3GWTN) |
| SASWELL Irrigation Timer SAS980SWT-7-Z01 <br> ![image](https://user-images.githubusercontent.com/6189950/213847257-d841a3c6-fa9f-4c48-bec2-33ab2f8aa573.png) | Profile: TS0601_SASWELL_VALVE<br>Model: TS0601<br>Manufacturer: _TZE200_akjefhj5 _TZE200_81isopgh _TZE200_2wg5qrjy _TZE200_fphxkxue <br><br>Features: On/Off, timer state/time left, weather delay, battery %, water consumption<br>Power supply: batteries<br><br>Amazon: [(link)](https://www.amazon.com/SASWELL-Irrigation-Sprinkler-Programmable-SAS980SWT-7-Z01/dp/B09TP86CKY) <br>Domadoo .fr: [(link)](https://www.domadoo.fr/fr/peripheriques/5886-saswell-electrovanne-et-programmateur-d-arrosage-connecte-zigbee-mesure-de-consommation.html) |
| GiEX Sprinkler / Water Irrigation Valve <br> ![image](https://user-images.githubusercontent.com/6189950/213847222-6c40ccbb-d162-4cfa-9cd6-2d107aab6d11.png) | Profile: TS0601_GIEX_VALVE<br>Model: TS0601<br>Manufacturer: _TZE200_sh1btabb _TZE200_a7sghmms _TZE204_a7sghmms _TZE200_7ytb3h8u _TZE204_7ytb3h8u _TZE284_7ytb3h8u <br><br>Features: On/Off, duration/capacity modes, timer state, water consumed, battery %<br>Power supply: batteries<br><br>Amazon: [(link)](https://www.amazon.com/ZIGBEE-Sprinkler-Separate-Watering-Schedules/dp/B0B1JN6KZX) <br>Amazon: [(link)](https://www.amazon.com/dp/B0D3BXVZKY) |
| PARKSIDE® Smart Irrigation Computer (PSBZS A1)<br> ![image](https://user-images.githubusercontent.com/6189950/221957269-9e40f908-d66b-409f-93a8-5fa5f0ee37c1.png) | Profile: TS0601_LIDL_VALVE<br>Model: TS0601<br>Manufacturer: _TZE200_htnnfasr <br><br>CSA: [link](https://csa-iot.org/csa_product/lidl-home-smarter-water-computer/)<br><br>Features: On/Off, timer, battery %<br>Driver status: <b>need testers</b><br>Power supply: batteries |
| Rain Seer Garden Home Irrigation Watering Timer <br> ![image](https://community.hubitat.com/uploads/default/original/3X/0/7/07d2cf9695c29177a20df60137c904eaa4f82f1c.jpeg) | Profile: TS0049_IRRIGATION_VALVE<br>Model: TS0049<br>Manufacturer: _TZ3210_0jxeoadc _TZ3000_hwnphliv _TZ3000_srldgdxz <br><br>Features: On/Off, irrigation duration, battery state (high/mid/low)<br>Power supply: batteries<br><br>AliExpress: [(link)](https://s.click.aliexpress.com/e/_DmZGnDz)<br>AliExpress: [(link)](https://www.aliexpress.com/item/1005005227004076.html)<br>Amazon .au: [(link)](https://www.amazon.com.au/dp/B0BX47V4YB) |
| FrankEver FK_V02 Smart Water Valve | Profile: TS0601_FRANKEVER_FK_V02<br>Model: TS0601<br>Manufacturer: _TZE200_1n2zev06 _TZE200_5uodvhgc _TZE200_wt9agwf3 <br><br>Features: On/Off, valve open threshold, valve open %, irrigation timer, battery %<br>Power supply: batteries<br><br>Review: [(link)](https://www.youtube.com/watch?v=lpL6xAYuBHk) |
| SONOFF SWV Smart Water Valve | Profile: SONOFF_SWV_VALVE<br>Model: SWV<br>Manufacturer: SONOFF <br><br>Features: On/Off, flow rate, water consumption, auto shut-off (fw 1.0.4+), valve fault status, battery %<br>Power supply: batteries<br><br>ITEAD: [(link)](https://itead.cc/product/sonoff-zigbee-smart-water-valve/)<br>SONOFF: [(link)](https://sonoff.tech/products/sonoff-zigbee-smart-water-valve)<br>AliExpress: [(link)](https://s.click.aliexpress.com/e/_c3WftRlV) |
| SONOFF SWV-ZN Series (SWV-ZNE, SWV-ZFE, SWV-ZNU, SWV-ZFU) | Profile: SONOFF_SWV_ZN_VALVE<br>Model: SWV-ZNE, SWV-ZFE, SWV-ZNU, SWV-ZFU<br>Manufacturer: SONOFF <br><br>Features: On/Off, irrigation schedule status, manual irrigation defaults, duration/capacity modes, battery %<br>Power supply: batteries |
| SONOFF SWV-ZF2 Double Valve <br><br><b>NOTE:</b> Requires component child driver (v1.8.0+) | Profile: SONOFF_SWV_ZF2_DOUBLE_VALVE<br>Model: SWV-ZF2<br>Manufacturer: SONOFF <br><br>Features: On/Off (dual channels), manual irrigation defaults, component child devices, duration/capacity modes, battery %<br>Power supply: batteries |
| Tuya TZE284 Double Valve (GiEX GX-03ZG / Insoma SGW08W / MUCIAKiE) | Profile: TS0601_TZE284_VALVE<br>Model: TS0601<br>Manufacturer: _TZE284_8zizsafo _TZE284_eaet5qt5 _TZE284_fhvpaltk <br><br>Features: On/Off (dual valves), per-valve countdown timers, water consumption, battery %<br>Power supply: batteries<br><br>AliExpress: [(link)](https://de.aliexpress.com/item/1005007836145637.html)<br>AliExpress: [(link)](https://www.aliexpress.us/item/3256807355418184.html) |
<!-- END SUPPORTED-MODELS -->

---

## Compatibility

* TS0001, TS0011, TS011F : Tuya on/off valves
* TS0601 : Tuya specific cluster 0xEF00 valves (GiEX, Saswell, Lidl, FrankEver, TZE284)
* TS0049 : Rain Seer irrigation valve
* SONOFF SWV / SWV-ZN / SWV-ZF2 : SONOFF smart water valves with custom 0xFC11 cluster
* Standard Zigbee 3.0 (ZHA 1.2) valves

### SONOFF SWV-ZF2 parent and child semantics

The two component children are the authoritative devices for independent valve automation. Child 1 controls endpoint 1 and child 2 controls endpoint 2. On the parent, `valve1` and `valve2` expose the individual endpoint states, while the standard `valve` and `switch` attributes are aggregate summaries: open/on if either endpoint is open, closed/off only when both endpoints are closed, and unknown when the combined state cannot be determined. Parent `open()`/`close()` control endpoint 1; use child 2 or `setValve2()` for endpoint 2.

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
(last edited 2026-07-12)
