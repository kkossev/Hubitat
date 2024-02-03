
# **Matter Advanced Bridge driver - device support matrix**

---------------------

### <b>Philips Hue Hub as Matter Bridge</b>
|Device Type                | Status                    |  __________ Remarks __________     |
|:--------------------------|:-------------------------:|--------------------------------:|
| Bulbs - On/Off            | working -   tested        |                              |
| Bulbs - Level control     | working  -  tested        |                              |

---------------------

### <b>Aqara E1 Hub as Matter Bridge</b>
|Device Type                |                     Status                       |                                                        Remarks      |
|:----------------------|:--------------------------:|-------------------------------------:|
| Temperature Sensor        | working OK - tested          |  Aqara Temperature and Humidity Sensor T1, Aqara TVOC sensor   |
| Humidity Sensor           | working OK - tested          |  Aqara Temperature and Humidity Sensor T1, Aqara TVOC sensor   |
| Plugs - On/Off            | working OK - tested          |  Aqara Smart Plug EU                         |
| Motion Sensors            | working OK - tested          |  Aqara P1 Motion Sensor        |
| Vibration Sensors         | working OK (as Motion sensor)- tested          |  Aqara Vibration Sensor        |
|:---------------------|:-------------------------:|--------------------------------------:|
| Bulbs - On/Off            | expected to be working       |         not tested                  |
| Bulbs - level control     | expected to be working       |         not tested                  |
| Bulbs - color control     | TODO  (not implemented yet)  |         not implemented                 |
| Bulbs - color control     | TODO  (not implemented yet)  |         not implemented                  |
| Thermostats               | TODO (partially implemented) |  Aqara thermostat E1 (TRV) - TODO        |
| Wireless Remote Switch    | TODO  (not implemented yet) |   Aqara Double Rocker H1                 |
| Wireless button           | TODO  (not implemented yet) |   Xiaomi/Lumi                |
| Wireless button (advanced)| TODO  (not implemented yet) |   button + accelometer          |
| Wired Remote Switch       | TODO  (not implemented yet) |    not imlemented                       |
| Aqara Cube T1 Pro         | TODO  (not implemented yet) |   n/a                                    |
|:-------------------|:--------------------------:|-------------------------------------:|
| PM 2.5                    | NOT working (not exposed via Matter)    |   Aqara TVOC sensor               |
| Atmosheric pressure       | NOT working (not exposed via Matter)    |   Aqara TVOC sensor               |
| Illuminance  Sensor       | NOT working (not exposed via Matter) |   Xiaomi(Lumi) Light Sensor            |
| Smoke Detector            | NOT working (not exposed via Matter) |   Aqara  Smart Smoke Detector              |
| Door and Window Sensor    | NOT working (not exposed via Matter) |Aqara Door and Window Sensor, old and T1|



---------------------





