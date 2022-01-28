This project is a work-in-progress, the current driver version is available in the development branch: https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock/Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy


## Supported models :

### Manufacturer : _TZE200_lve3dvpy

![image](https://user-images.githubusercontent.com/6189950/149659251-3503e3e9-237b-41e7-8c45-d8b83155f172.png)
https://www.aliexpress.com/item/1005003329038812.html

### Manufacturer : _TZE200_locansqn
![image](https://user-images.githubusercontent.com/6189950/151618266-a322189e-c9ad-4d72-8b7d-9eb8164d95e9.png)

**Driver status**: basic functionalities working OK!

-----------------------------------------------------------------------------

### Manufacturer :  _TYZB01_kvwjujy9
![image](https://user-images.githubusercontent.com/6189950/150995706-1b175d63-ed00-4ae9-a361-bb5c894e9143.png)
https://www.aliexpress.com/item/1005002549320064.html
**Driver status**:  this is a problematic device that is reported to be not working stable by different users in many different home automation platforms. Not recommended for purchase at this stage of the integration to HE!


## Note

While the same driver **may** work with other Tuya temperature/humidity/illuminance models (different than these listed above), but this is not guaranteed because of the commands differences between the models and manufacturers.

## Features

Currently, not all of the functionalities and settings that are available from Tuya SmartLife app for the specific model are implemented into this HE driver.

The basic functions that are working at the moment are:

* Synchronizes the sensor clock to HE hub time and day of the week.
* Reports the sensor temperature (0.1 C resolution), as frequently as sent by the device.
* Reports the sensor humidity (1% RH resolution),  as frequently as sent by the device.
* Reports battery level (%)
* Extended debug and info logging
