## Revisions history:

 * ver. 1.0.0 2022-06-24 kkossev  - first test version
 * ver. 1.1.0 2022-06-30 kkossev  - decodeAqaraStruct; added temperatureEvent;  RTCGQ13LM; RTCZCGQ11LM (FP1) parsing
 * ver. 1.1.1 2022-07-01 kkossev  - no any commands are sent immediately after pairing!
 * ver. 1.1.2 2022-07-04 kkossev  - PowerSource presence polling; FP1 pars
 * ver. 1.1.3 2022-07-04 kkossev  - FP1 approachDistance and monitoringMode parameters update
 * ver. 1.1.4 2022-07-08 kkossev  - aqaraReadAttributes()
 * ver. 1.1.5 2022-07-09 kkossev  - when going offline the battery level is set to 0 (zero); when back online, the last known battery level is restored; when switching offline, motion is reset to 'inactive'; added digital and physical events type
 * ver. 1.1.6 2022-07-12 kkossev  - aqaraBlackMagic; 
 * ver. 1.1.7 2022-07-23 kkossev  - added MCCGQ14LM for tests
 * ver. 1.2.0 2022-07-29 kkossev  - FP1 first successful initializaiton : attr. 0142 presence bug fix; debug logs improvements; monitoring_mode bug fix; LED is null bug fix ;motionRetriggerInterval bugfix for FP1; motion sensitivity bug fix for FP1; temperature exception bug; monitoring_mode bug fix; approachDistance bug fix; setMotion command for tests/tuning of automations; added motion active/inactive simulation for FP1
 * ver. 1.2.1 2022-08-10 kkossev  - code / traces cleanup; change device name on initialize(); 
 * ver. 1.2.2 2022-08-21 kkossev  - added motionRetriggerInterval for T1 model; filter illuminance parsing for RTCGQ13LM
 * ver. 1.2.3 2022-12-08 kkossev  - (dev. branch ) added internalTemperature option (disabled by default); added homeKitCompatibility option to enable/disable battery 100% workaround for FP1 (HomeKit); Approach distance bug fix; battery 0% bug fix; pollPresence after hub reboot bug fix; RTCGQ13LM battery fix; added GZCGQ01LM and GZCGQ11LM illuminance sensors for tests;

