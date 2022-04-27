This driver can be installed from Hubitat Package Manager, search for "Tuya Smart Siren Zigbee" or by Tag 'Zigbee'.

The last stable version code is here: https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Smart%20Siren%20Zigbee/Tuya%20Smart%20Siren%20Zigbee.groovy

-------------------------------
## Supported models :
|  Device |  Links |
|---|---|
| Tuya Zigbee Siren Alarm![image](https://user-images.githubusercontent.com/6189950/161398473-03ef138a-5152-41dd-9dc8-4db4cd10a8b4.png)  |  Model: TS0601<br> Manufacturer: \_TZE200_t1blo2bj <br> <br> AliExpress: https://www.aliexpress.com/item/1005004048262165.html <be> 
-------------------------------------------------
## Note: 
the current version of this driver supports only the Tuya model **without** temperature and humidity sensors!

## Features
* Hubitat standard 'Alarm' capabilities:  "strobe", "off", "both", "siren"
 **Note**: the sound and the LEDs can not be controlled separately with this Tuya models!
* volume, duration and melody custom attributes (available forcontrol from RM)
  * Volume control: low, mid, high
  * Sound duration control: 1 .. 255 seconds
  * Melody selection: 1..18
* Battery level reporting
