This driver is currently in development. Can be installed manually from : https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy 

## Supported models

|  Device |  Links |
|---|---|
|  Tuya ZigBee Multi-Sensor 4 in 1  ![image](https://user-images.githubusercontent.com/6189950/163685629-86881a7a-0e3e-4b86-b568-16a3ff8c8fcd.png) | AliExpress: https://www.aliexpress.com/item/1005001878974427.html <br> | 
| Tuya ZigBee Multi-Sensor 3 in 1  ![image](https://user-images.githubusercontent.com/6189950/163686024-e1de1fed-8c03-4729-a359-607654780992.png) | AliExpress: https://www.aliexpress.com/item/1005003712577679.html <br> Amazon.de: [link](https://www.amazon.de/dp/B092J89272?ref_=cm_sw_r_cp_ud_dp_QA4DXGXCKHZF0M6EQN1A)<br>  | 
| TUYATEC RH3040 Motion Sensor ![image](https://user-images.githubusercontent.com/6189950/155217683-4714c07e-939e-4c74-9ba5-fc59ccef98b1.png) | AliExpress: https://www.aliexpress.com/item/1005002468620848.html <br> AliExpress: https://www.aliexpress.com/item/1005002557815290.html <br> |
|  PIR sensor TS0202 ![image](https://user-images.githubusercontent.com/6189950/154433141-af9f57a4-0aad-4405-bcbd-1155da268a06.png) | AliExpres: https://www.aliexpress.com/item/1005003776494634.html <br> |
----------------------------
## Compatibility

* Tuya specific cluster 0xEF00 PIR sensors
Note: currently, the driver is tested only with :
  *  model:"TS0202", manufacturer:"_TZ3210_zmy9hjay"
  *  model:"TS0601", manufacturer:"_TZE200_7hfcudw5"
* IAS cluster 0x0500 sensors
  *  model:"TS0202" - various manufacturers
  * model:"RH3040" - various manufacturers
-----------------------------------
## Features

* Motion detection
* Temperature
* Humidity
* Illuminance (4in1 models )
* Battery reporting
* Tamper Alert (4in1 and 3in1 only)
------------------
