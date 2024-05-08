const fz = require('zigbee-herdsman-converters/converters/fromZigbee');
const tz = require('zigbee-herdsman-converters/converters/toZigbee');
const exposes = require('zigbee-herdsman-converters/lib/exposes');
const reporting = require('zigbee-herdsman-converters/lib/reporting');
const legacy = require('zigbee-herdsman-converters/lib/legacy');
const extend = require('zigbee-herdsman-converters/lib/extend');
const ota = require('zigbee-herdsman-converters/lib/ota');
const tuya = require('zigbee-herdsman-converters/lib/tuya');
const utils = require('zigbee-herdsman-converters/lib/utils');
const globalStore = require('zigbee-herdsman-converters/lib/store');
const e = exposes.presets;
const ea = exposes.access;

const tzDatapoints = {
    ...tuya.tz.datapoints,
    key: [...tuya.tz.datapoints.key,  'alarm', 'alarm_time', 'alarm_volume', 'type', 'volume', 'ringtone', 'duration', 'medium_motion_detection_distance',
            'large_motion_detection_distance', 'large_motion_detection_sensitivity', 'small_motion_detection_distance',
            'small_motion_detection_sensitivity', 'static_detection_distance', 'static_detection_sensitivity', 'keep_time', 'indicator',
            'motion_sensitivity', 'detection_distance_max', 'detection_distance_min', 'presence_sensitivity', 'sensitivity', 'illuminance_interval',
            'medium_motion_detection_sensitivity', 'small_detection_distance', 'small_detection_sensitivity',]
}


const definition = [

    {
        fingerprint: tuya.fingerprint('TS0225', ['_TZE200_hl0ss9oa']),
        model: 'ZG-205ZL',
        vendor: 'TuYa',
        description: '24Ghz human presence sensor',
        fromZigbee: [tuya.fz.datapoints],
        toZigbee: [tzDatapoints],
        exposes: [
            e.presence(),
            e.enum('motion_state', ea.STATE, ['none', 'large', 'small', 'static']).withDescription('Motion state'),
            e.illuminance_lux(),
            e.numeric('fading_time', ea.STATE_SET).withValueMin(0).withValueMax(600).withValueStep(1).withUnit('s')
                .withDescription('Presence keep time'),
            e.numeric('large_motion_detection_distance', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(0.01).withUnit('m')
                .withDescription('Large motion detection distance'),
            e.numeric('large_motion_detection_sensitivity', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(1).withUnit('x')
                .withDescription('Large motion detection sensitivity'),
            e.numeric('small_motion_detection_distance', ea.STATE_SET).withValueMin(0).withValueMax(6).withValueStep(0.01).withUnit('m')
                .withDescription('Small motion detection distance'),
            e.numeric('small_motion_detection_sensitivity', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(1).withUnit('x')
                .withDescription('Small motion detection sensitivity'),
            e.numeric('static_detection_distance', ea.STATE_SET).withValueMin(0).withValueMax(6).withValueStep(0.01).withUnit('m')
                .withDescription('Static detection distance'),
            e.numeric('static_detection_sensitivity', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(1).withUnit('x')
                .withDescription('Static detection sensitivity'),
            e.enum('mode', ea.STATE_SET, ['off', 'arm', 'alarm']).withDescription('Alarm mode'),
            e.enum('alarm_volume', ea.STATE_SET, ['mute', 'low', 'medium', 'high']).withDescription('Alarm volume'),
            e.numeric('alarm_time', ea.STATE_SET).withValueMin(1).withValueMax(60).withValueStep(1).withUnit('m').withDescription('Alarm time'),
            e.binary('indicator', ea.STATE_SET, 'ON', 'OFF').withDescription('LED indicator mode'),
        ],
        meta: {
            tuyaDatapoints: [
                [1, 'presence', tuya.valueConverter.trueFalse1],
                [20, 'illuminance_lux', tuya.valueConverter.raw],
                [11, 'motion_state', tuya.valueConverterBasic.lookup({
                    'none': tuya.enum(0), 'large': tuya.enum(1), 'small': tuya.enum(2), 'static': tuya.enum(3),
                })],
                [12, 'fading_time', tuya.valueConverter.raw],
                [13, 'large_motion_detection_distance', tuya.valueConverter.divideBy100],
                [15, 'large_motion_detection_sensitivity', tuya.valueConverter.raw],
                [14, 'small_motion_detection_distance', tuya.valueConverter.divideBy100],
                [16, 'small_motion_detection_sensitivity', tuya.valueConverter.raw],
                [103, 'static_detection_distance', tuya.valueConverter.divideBy100],
                [104, 'static_detection_sensitivity', tuya.valueConverter.raw],
                [105, 'mode', tuya.valueConverterBasic.lookup({'arm': tuya.enum(0), 'off': tuya.enum(1), 'alarm': tuya.enum(2)})],
                [102, 'alarm_volume', tuya.valueConverterBasic.lookup({
                    'low': tuya.enum(0), 'medium': tuya.enum(1), 'high': tuya.enum(2), 'mute': tuya.enum(3),
                })],
                [101, 'alarm_time', tuya.valueConverter.raw],
                [24, 'indicator', tuya.valueConverter.onOff],
            ],
        },
    },
     {
        fingerprint: tuya.fingerprint('TS0225', ['_TZE200_2aaelwxk']),
        model: 'ZG-205Z/A',
        vendor: 'TuYa',
        description: '5.8Ghz Human presence sensor',
        fromZigbee: [tuya.fz.datapoints],
        toZigbee: [tuya.tz.datapoints],
        exposes: [
            e.presence(), e.illuminance().withUnit('lx'),
            e.numeric('large_motion_detection_sensitivity', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(1).withUnit('x')
                .withDescription('Motion detection sensitivity'),
            e.numeric('large_motion_detection_distance', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(0.01).withUnit('m')
                .withDescription('Motion detection distance'),
            e.enum('motion_state', ea.STATE, ['none', 'small', 'medium', 'large']).withDescription('State of the motion'),
            e.numeric('fading_time', ea.STATE_SET).withValueMin(0).withValueMax(28800).withValueStep(1).withUnit('s')
                .withDescription('For how much time presence should stay true after detecting it'),
            e.numeric('medium_motion_detection_distance', ea.STATE_SET).withValueMin(0).withValueMax(6).withValueStep(0.01).withUnit('m')
                .withDescription('Medium motion detection distance'),
            e.numeric('medium_motion_detection_sensitivity', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(1).withUnit('x')
                .withDescription('Medium motion detection sensitivity'),
            e.binary('indicator', ea.STATE_SET, 'ON', 'OFF').withDescription('LED Indicator'),
            e.numeric('small_detection_distance', ea.STATE_SET).withValueMin(0).withValueMax(6).withValueStep(0.01).withUnit('m')
                .withDescription('Small detection distance'),
            e.numeric('small_detection_sensitivity', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(1).withUnit('x')
                .withDescription('Small detection sensitivity'),
        ],
        meta: {
            tuyaDatapoints: [
                [1, 'presence', tuya.valueConverter.trueFalse1],
                [2, 'large_motion_detection_sensitivity', tuya.valueConverter.raw],
                [4, 'large_motion_detection_distance', tuya.valueConverter.divideBy100],
                [101, 'motion_state', tuya.valueConverterBasic.lookup(
                    {'none': tuya.enum(0), 'large': tuya.enum(1), 'medium': tuya.enum(2), 'small': tuya.enum(3)})],
                [102, 'fading_time', tuya.valueConverter.raw],
                [104, 'medium_motion_detection_distance', tuya.valueConverter.divideBy100],
                [105, 'medium_motion_detection_sensitivity', tuya.valueConverter.raw],
                [106, 'illuminance', tuya.valueConverter.raw],
                [107, 'indicator', tuya.valueConverter.onOff],
                [108, 'small_detection_distance', tuya.valueConverter.divideBy100],
                [109, 'small_detection_sensitivity', tuya.valueConverter.raw],
                // Not exposed DPs/untested
                // [103, 'motion_false_detection', tuya.valueConverter.raw],
                // [113, 'breathe_false_detection', tuya.valueConverter.raw],
                // [3, 'mov_minimum_distance', tuya.valueConverter.raw],
                // [110, 'micro_minimum_distance', tuya.valueConverter.raw],
                // [111, 'motionless_minimum_distance', tuya.valueConverter.raw],
                // [112, 'reset_setting', tuya.valueConverter.raw],
                // [114, 'time', tuya.valueConverter.raw],
                // [115, 'alarm_time', tuya.valueConverter.raw],
                // [116, 'alarm_volume', tuya.valueConverterBasic.lookup(
                //  {'low': tuya.enum(0), 'medium': tuya.enum(1), 'high': tuya.enum(2), 'mute': tuya.enum(3)})],
                // [117, 'working_mode', tuya.valueConverterBasic.lookup(
                // {'arm': tuya.enum(0), 'off': tuya.enum(1), 'alarm': tuya.enum(2),  'doorbell': tuya.enum(3)})],
                // [118, 'auto1', tuya.valueConverter.raw],
                // [119, 'auto2', tuya.valueConverter.raw],
                // [120, 'auto3', tuya.valueConverter.raw],
            ],
        },
    },
 {
        fingerprint: tuya.fingerprint('TS0601', ['_TZE200_2aaelwxk']),
        model: 'ZG-204ZM',
        vendor: 'TuYa',
        description: 'PIR 24Ghz human presence sensor',
        fromZigbee: [tuya.fz.datapoints],
        toZigbee: [tzDatapoints],
        exposes: [
            e.presence(),
            e.enum('motion_state', ea.STATE, ['none', 'large', 'small', 'static']).withDescription('Motion state'),
            e.illuminance_lux(),e.battery(),
            e.numeric('fading_time', ea.STATE_SET).withValueMin(0).withValueMax(28800).withValueStep(1).withUnit('s')
                .withDescription('Presence keep time'),
            e.numeric('static_detection_distance', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(0.01).withUnit('m')
                .withDescription('Static detection distance'),
            e.numeric('static_detection_sensitivity', ea.STATE_SET).withValueMin(0).withValueMax(10).withValueStep(1).withUnit('x')
                .withDescription('Static detection sensitivity'),
            e.binary('indicator', ea.STATE_SET, 'ON', 'OFF').withDescription('LED indicator mode'),
        ],
        meta: {
            tuyaDatapoints: [
                [1, 'presence', tuya.valueConverter.trueFalse1],
                [106, 'illuminance_lux', tuya.valueConverter.raw],
                [101, 'motion_state', tuya.valueConverterBasic.lookup({
                    'none': tuya.enum(0), 'large': tuya.enum(1), 'small': tuya.enum(2), 'static': tuya.enum(3),
                })],
                [102, 'fading_time', tuya.valueConverter.raw],
                [4, 'static_detection_distance', tuya.valueConverter.divideBy100],
                [2, 'static_detection_sensitivity', tuya.valueConverter.raw],
                [107, 'indicator', tuya.valueConverter.onOff],
                [121, 'battery', tuya.valueConverter.raw],
              //  [104, 'small_motion_detection_distance', tuya.valueConverter.divideBy100],
              //  [105, 'small_motion_detection_sensitivity', tuya.valueConverter.raw],
              //  [108, 'static_detection_distance', tuya.valueConverter.divideBy100],
              //  [109, 'static_detection_sensitivity', tuya.valueConverter.raw],
               
            ],
        },
    },

     {
        fingerprint: tuya.fingerprint('TS0601', ['_TZE200_3towulqd', '_TZE200_1ibpyhdc', '_TZE200_bh3n6gk8','_TZE200_ttcovulf']),
        model: 'ZG-204ZL',
        vendor: 'TuYa',
        description: 'Luminance motion sensor',
        fromZigbee: [tuya.fz.datapoints],
        toZigbee: [tzDatapoints],
        exposes: [
            e.occupancy(), e.illuminance().withUnit('lx'), e.battery(),
            e.enum('sensitivity', ea.STATE_SET, ['low', 'medium', 'high'])
                .withDescription('PIR sensor sensitivity (refresh and update only while active)'),
            e.enum('keep_time', ea.STATE_SET, ['10', '30', '60', '120'])
                .withDescription('PIR keep time in seconds (refresh and update only while active)'),
            e.numeric('illuminance_interval', ea.STATE_SET).withValueMin(1).withValueMax(720).withValueStep(1).withUnit('minutes')
                .withDescription('Brightness acquisition interval (refresh and update only while active)')

        ],
         meta: {
            tuyaDatapoints: [
                [1, 'occupancy', tuya.valueConverter.trueFalse0],
                [4, 'battery', tuya.valueConverter.raw],
                [9, 'sensitivity',tuya.valueConverterBasic.lookup({'low': tuya.enum(0), 'medium': tuya.enum(1), 'high': tuya.enum(2)})],
                [10, 'keep_time', tuya.valueConverterBasic.lookup({'10': tuya.enum(0), '30': tuya.enum(1), '60': tuya.enum(2),'120': tuya.enum(3)})],
                [12, 'illuminance', tuya.valueConverter.raw],
                [102, 'illuminance_interval', tuya.valueConverter.raw]

            ],
        },
    },

    {
        fingerprint: tuya.fingerprint('TS0601', ['_TZE200_qoy0ekbd', '_TZE200_znbl8dj5', '_TZE200_a8sdabtg', '_TZE200_dikkika5','_TZE200_ysm4dsb1']),
        model: 'ZG-227ZL',
        vendor: 'TuYa',
        description: 'Temperature & humidity LCD sensor',
        fromZigbee: [tuya.fz.datapoints],
        toZigbee: [tuya.tz.datapoints],
        configure: tuya.configureMagicPacket,
        exposes: [e.temperature(), e.humidity(), tuya.exposes.temperatureUnit(), tuya.exposes.temperatureCalibration(),
            tuya.exposes.humidityCalibration(), e.battery()],
        whiteLabel: [
            tuya.whitelabel('TuYa', 'ZG-227Z', 'Temperature and humidity sensor', ['_TZE200_a8sdabtg']),
            tuya.whitelabel('KOJIMA', 'KOJIMA-THS-ZG-LCD', 'Temperature and humidity sensor', ['_TZE200_dikkika5']),
            tuya.whitelabel('KOJIMA', 'KOJIMA-THS-ZG-LITE', 'Temperature and humidity sensor', ['_TZE200_ysm4dsb1']),
        ],
        meta: {
            tuyaDatapoints: [
                [1, 'temperature', tuya.valueConverter.divideBy10],
                [2, 'humidity', tuya.valueConverter.raw],
                [4, 'battery', tuya.valueConverter.raw],
                [9, 'temperature_unit', tuya.valueConverter.temperatureUnit],
                [23, 'temperature_calibration', tuya.valueConverter.divideBy10],
                [24, 'humidity_calibration', tuya.valueConverter.raw],
            ],
        },
    },
    {
        fingerprint: tuya.fingerprint('TS0601', ['_TZE200_n8dljorx', '_TZE200_pay2byax', '_TZE200_ijey4q29']),
        model: 'ZG-102ZL',
        vendor: 'TuYa',
        description: 'Luminance door sensor',
        fromZigbee: [tuya.fz.datapoints],
        toZigbee: [tzDatapoints],
        configure: tuya.configureMagicPacket,
        exposes: [e.contact(), e.illuminance().withUnit('lx'), e.battery(), 
            e.numeric('illuminance_interval', ea.STATE_SET).withValueMin(1).withValueMax(720).withValueStep(1).withUnit('minutes')
                .withDescription('Brightness acquisition interval (refresh and update only while active)')],
        meta: {
            tuyaDatapoints: [
                [1, 'contact', tuya.valueConverter.inverse],
                [101, 'illuminance', tuya.valueConverter.raw],
                [2, 'battery', tuya.valueConverter.raw],
                [102, 'illuminance_interval', tuya.valueConverter.raw],
            ],
        },
    },
    {
        fingerprint: tuya.fingerprint('TS0601', ['_TZE200_8isdky6j']),
        model: 'ZG-225Z',
        vendor: 'TuYa',
        description: 'Gas sensor',
        fromZigbee: [tuya.fz.datapoints],
        toZigbee: [tuya.tz.datapoints],
     
        exposes: [e.gas(), tuya.exposes.gasValue().withUnit('ppm')],
        meta: {
            tuyaDatapoints: [
                [1, 'gas', tuya.valueConverter.trueFalse0],
                [2, 'gas_value', tuya.valueConverter.raw],
            ],
        },
    },
];

module.exports = definition;