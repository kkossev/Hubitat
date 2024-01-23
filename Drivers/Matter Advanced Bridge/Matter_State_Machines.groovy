library(
    base: 'driver',
    author: 'Krassimir Kossev',
    category: 'matter',
    description: 'Matter State Machines',
    name: 'matterStateMachinesLib',
    namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Bridge/Matter_State_Machines.groovy',
    version: '1.0.0',
    documentationLink: ''
)
/*
  *  Matter State Machines Library
  *
  *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
  *  https://community.hubitat.com/t/project-zemismart-m1-matter-bridge-for-tuya-zigbee-devices-matter/127009
  *
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
  *  in compliance with the License. You may obtain a copy of the License at:
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
  *  for the specific language governing permissions and limitations under the License.
  *
  * ver. 1.0.0  2024-01-22 kkossev  - first version
  *
  *                                   TODO:
  *
*/

import groovy.transform.Field

/* groovylint-disable-next-line ImplicitReturnStatement */
@Field static final String matterStateMachinesLib = '1.0.0'
@Field static final String matterStateMachinesLibStamp   = '2024/01/22 8:10 PM'

// no metadata section for matterStateMachinesLib
@Field static final String  START   = 'START'
@Field static final String  STOP    = 'STOP'
//                          UNKNOWN = 'UNKNOWN'
@Field static final String  RUNNING = 'RUNNING'
@Field static final String  SUCCESS = 'SUCCESS'
@Field static final String  ERROR   = 'ERROR'

@Field static final Integer STATE_MACHINE_PERIOD = 100
@Field static final Integer STATE_MACHINE_MAX_RETRIES = 15

void readSingeAttrStateMachine(Map data = null) {
    if (state['stateMachines'] == null) { state['stateMachines'] = [] }
    if (state['stateMachines']['readSingeAttrState'] == null) { state['stateMachines']['readSingeAttrState'] = 0 }
    if (state['stateMachines']['readSingeAttrRetry'] == null) { state['stateMachines']['readSingeAttrRetry'] = 0 }

    if (data != null) {
        if (data['action'] == START) {
            state['stateMachines']['readSingeAttrState']  = 1
            state['stateMachines']['readSingeAttrRetry']  = 0
            state['stateMachines']['readSingeAttrResult'] = UNKNOWN
            data['action'] = RUNNING
        }
    }
    logTrace "readSingeAttrStateMachine: data:${data}, state['stateMachines'] = ${state['stateMachines']}"

    Integer st =    state['stateMachines']['readSingeAttrState']
    Integer retry = state['stateMachines']['readSingeAttrRetry']
    String fingerprintName = getFingerprintName(data.endpoint)
    String attributeName = getAttributeName([cluster: HexUtils.integerToHexString(data.cluster, 2), attrId: HexUtils.integerToHexString(data.attribute, 2)])
    logTrace "readSingeAttrStateMachine: st:${st} retry:${retry} data:${data}"
    switch (st) {
        case 0:
            logDebug "readSingeAttrStateMachine: st:${st} - idle"
            unschedule('readSingeAttrStateMachine')
            break
        case 1: // start - first check if the endpoint and the cluster are in the fingerprint
            //logDebug "readSingeAttrStateMachine: st:${st} - checking whether attribute ${data.attribute} is in the fingerprint ${fingerprintName}"
            if (state[fingerprintName] == null) {
                logWarn "readAttributeSafe(): state[${fingerprintName}] is null !"
                logWarn "run steps A1 and A2 !"
                state['stateMachines']['readSingeAttrResult'] = ERROR
                data['action'] = ERROR
                st = 99
                break
            }
            logTrace "readSingeAttrStateMachine: st:${st} - found fingerprint ${fingerprintName}"
            // check whether the cluster is in the fingerprint ServerList
            List<String> serverList = state[fingerprintName]['ServerList']
            if (serverList == null) {
                logWarn "readAttributeSafe(): state[${fingerprintName}]['ServerList'] is null !"
                logWarn "run steps A1 and A2 !"
                state['stateMachines']['readSingeAttrResult'] = ERROR
                data['action'] = ERROR
                st = 99
                break
            }
            logTrace "readSingeAttrStateMachine: st:${st} - found serverList ${serverList}"
            // convert the serverList to a list of Integers
            List<Integer> serverListInt = serverList.collect { HexUtils.hexStringToInt(it) }
            logTrace "readSingeAttrStateMachine: st:${st} - found serverListInt ${serverListInt}"
            // check whether the cluster is in the fingerprint serverListInt
            if (!serverListInt.contains(data.cluster)) {
                logWarn "readAttributeSafe(): state[${fingerprintName}]['ServerList'] does not contain cluster ${data.cluster} (0x${HexUtils.integerToHexString(data.cluster, 2)}) !"
                logWarn "valid clusters are: ${serverList}"
                state['stateMachines']['readSingeAttrResult'] = ERROR
                data['action'] = ERROR
                st = 99
                break
            }
            // so far, so good, now check whether the attribute is in the attributeList
            List<String>  attributeList = state[fingerprintName]['AttributeList']
            if (attributeList == null) {
                logWarn "readAttributeSafe(): state[${fingerprintName}]['AttributeList'] is null !"
                logWarn "run steps A1 and A2 !"
                state['stateMachines']['readSingeAttrResult'] = ERROR
                data['action'] = ERROR
                st = 99
                break
            }
            logTrace "readSingeAttrStateMachine: st:${st} - found attributeList ${attributeList}"
            // convert the attributeList to a list of Integers
            List<Integer> attributeListInt = attributeList.collect { HexUtils.hexStringToInt(it) }
            logTrace "readSingeAttrStateMachine: st:${st} - found attributeListInt ${attributeListInt}"
            // check whether the attribute is in the fingerprint attributeListInt
            if (!attributeListInt.contains(data.attribute)) {
                logWarn "readAttributeSafe(): state[${fingerprintName}]['AttributeList'] does not contain attribute ${data.attribute} (0x${HexUtils.integerToHexString(data.attribute, 2)}) !"
                logWarn "valid attributes are: ${attributeList}"
                state['stateMachines']['readSingeAttrResult'] = ERROR
                data['action'] = ERROR
                st = 99
                break
            }
            List<Integer> toBeConfirmedList = [data.endpoint, data.cluster, data.attribute]
            state['stateMachines']['toBeConfirmed'] = toBeConfirmedList
            state['stateMachines']['Confirmation'] = false
            List<Map<String, String>> attributePaths = [matter.attributePath(data.endpoint, data.cluster, data.attribute)]
            sendToDevice(matter.readAttributes(attributePaths))
            st = 2; retry = 0
            break
        case 2: // waiting for the attribute value
            if (state['stateMachines']['Confirmation'] == true) {
                logTrace "readSingeAttrStateMachine: st:${st} - received ${attributeName} reading confirmation!"
                state['stateMachines']['readSingeAttrResult'] = SUCCESS
                st = 99
            }
            else {
                logTrace "readSingeAttrStateMachine: st:${st} - waiting for the attribute value"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "readSingeAttrStateMachine: st:${st} - timeout waiting for the attribute value !"
                    state['stateMachines']['readSingeAttrResult'] = ERROR
                    st = 99
                }
            }
            break
        case 99:
            logDebug "readSingeAttrStateMachine: st:${st} - THE END"
            st = 0
            break
        default :    // error
            st = 0
            break
    }
    if (st != 0) {
        state['stateMachines']['readSingeAttrState'] = st
        state['stateMachines']['readSingeAttrRetry'] = retry
        if (data != null) {
            data['action'] = RUNNING
        }
        runInMillis(STATE_MACHINE_PERIOD, readSingeAttrStateMachine, [overwrite: true, data: data])
    }
    else {
        //logWarn "readSingeAttrStateMachine: st:${st} - THE END"
    }
}


@Field static final Integer READ_ALL_ATTRIBUTES_STATE_IDLE                                 = 0
@Field static final Integer READ_ALL_ATTRIBUTES_STATE_INIT                                 = 1

@Field static final Integer READ_ALL_ATTRIBUTES_STATE_ERROR                                = 98
@Field static final Integer READ_ALL_ATTRIBUTES_STATE_END                                  = 99


/****************************************** readAllAttributesStateMachine ******************************************
 *
 * This is the state machine for discovering ALL ATTRIBUTES of a specific endpoint and cluster.
 *
 * @param data (optional) A map containing additional data to control the state machine execution.
 *             The following data keys are supported:
    *             - action: START
    *             - endpoint: the endpoint to be discovered
    *             - cluster: the cluster to be discovered
 */
 /*
void readAllAttributesStateMachine(Map data=null) {
    if (state['stateMachines'] == null) { state['stateMachines'] = [] }
    if (state['stateMachines']['readAllAttributesState'] == null) { state['stateMachines']['readAllAttributesState'] = DISCOVER_ALL_STATE_IDLE }
    if (state['stateMachines']['readAllAttributesRetry'] == null) { state['stateMachines']['readAllAttributesRetry'] = 0 }
    if (state['stateMachines']['readAllAttributesResult'] == null) { state['stateMachines']['readAllAttributesResult'] = UNKNOWN }

    if (data != null) {
        if (data['action'] == START) {
            state['stateMachines']['readAllAttributesState']  = READ_ALL_ATTRIBUTES_STATE_INIT
            state['stateMachines']['readAllAttributesRetry']  = 0
            state['stateMachines']['readAllAttributesResult'] = UNKNOWN
            data['action'] = RUNNING
        }
    }
    logDebug "readAllAttributesStateMachine: data:${data}, state['stateMachines'] = ${state['stateMachines']}"

    Integer st =    state['stateMachines']['readAllAttributesState']
    Integer retry = state['stateMachines']['readAllAttributesRetry']
    String fingerprintName = getFingerprintName(data.endpoint)
    //String attributeName = getAttributeName([cluster: HexUtils.integerToHexString(data.cluster, 2), attrId: HexUtils.integerToHexString(data.attribute, 2)])
    logDebug "readAllAttributesStateMachine: st:${st} retry:${retry} data:${data}"
    switch (st) {
        case READ_ALL_ATTRIBUTES_STATE_IDLE :
            logDebug "readAllAttributesStateMachine: st:${st} - idle -> unscheduling!"
            unschedule('readAllAttributesStateMachine')
            break
        case READ_ALL_ATTRIBUTES_STATE_INIT:
            logDebug "readAllAttributesStateMachine: st:${st} ep:- starting ..."
            // state[fingerprintName] will be created [:] when the Global Element 0xFFFB is read ...
            if (state.bridgeDescriptor == null) { state.bridgeDescriptor = [] } // or state['bridgeDescriptor'] = [:] ?
            state.states['isInfo'] = true
            // TODO
            st = DISCOVER_ALL_STATE_DESCIPTOR_ATTRIBUTE_LIST
            break



    }
}
*/


@Field static final Integer STATE_DISCOVER_GLOBAL_ELEMENTS_IDLE                       = 0
@Field static final Integer STATE_DISCOVER_GLOBAL_ELEMENTS_INIT                       = 1
@Field static final Integer STATE_DISCOVER_GLOBAL_ELEMENTS_ATTRIBUTE_LIST             = 2
@Field static final Integer STATE_DISCOVER_GLOBAL_ELEMENTS_ATTRIBUTE_LIST_WAIT        = 3
@Field static final Integer STATE_DISCOVER_GLOBAL_ELEMENTS_GLOBAL_ELEMENTS_WAIT       = 5

@Field static final Integer STATE_DISCOVER_GLOBAL_ELEMENTS_ERROR                      = 98
@Field static final Integer STATE_DISCOVER_GLOBAL_ELEMENTS_END                        = 99

/**
 * This state machine checks and discoveres the GLOBAL ELEMENTS of a specific endpoint and cluster.
 * 
 * Must be called this way : disoverGlobalElementsStateMachine([action: START, endpoint: Integer, cluster: Integer])
 * where:
 *      - action: START 
 *      - endpoint: the endpoint to be discovered
 *      - cluster: the cluster to be discovered
 *      - debug: true/false
 *
 * The following keys are updated when the state machine is running:
 *      state['stateMachines']['discoverGlobalElementsState']  - the current internal state (see the constants above)
 *      state['stateMachines']['discoverGlobalElementsRetry']  - the current number of retries (0..STATE_MACHINE_MAX_RETRIES)
 *      state['stateMachines']['discoverGlobalElementsResult'] - the result of the state machine execution (UNKNOWN, RUNNING, SUCCESS, ERROR)
 * 
 * Uses the common for all state machines state['stateMachines']['toBeConfirmed']  - [endpoint, cluster, 0xFFFB] and state['stateMachines']['Confirmation'] !
 * 
 * The calling function must check the state['stateMachines']['discoverGlobalElementsResult'] to determine the result of the state machine execution.
 */

void disoverGlobalElementsStateMachine(Map data) {
    if (state['stateMachines'] == null) { state['stateMachines'] = [] }
    if (state['stateMachines']['discoverGlobalElementsState'] == null) { state['stateMachines']['discoverGlobalElementsState'] = STATE_DISCOVER_GLOBAL_ELEMENTS_IDLE }
    if (state['stateMachines']['discoverGlobalElementsRetry'] == null) { state['stateMachines']['discoverGlobalElementsRetry'] = 0 }
    if (state['stateMachines']['discoverGlobalElementsResult'] == null) { state['stateMachines']['discoverGlobalElementsResult'] = UNKNOWN }

    if (data != null) {
        if (data['action'] == START) {
            state['stateMachines']['discoverGlobalElementsState']  = STATE_DISCOVER_GLOBAL_ELEMENTS_INIT
            state['stateMachines']['discoverGlobalElementsRetry']  = 0
            state['stateMachines']['discoverGlobalElementsResult'] = RUNNING
            data['action'] = RUNNING    // something different than START or STOP - TODO !
        }
    }
    if (data.debug) { logDebug "disoverGlobalElementsStateMachine: data:${data}, state['stateMachines'] = ${state['stateMachines']}" }

    Integer st =    state['stateMachines']['discoverGlobalElementsState']
    Integer retry = state['stateMachines']['discoverGlobalElementsRetry']
    //String fingerprintName = getFingerprintName(data.endpoint)
    //String attributeName = getAttributeName([cluster: HexUtils.integerToHexString(data.cluster, 2), attrId: HexUtils.integerToHexString(data.attribute, 2)])
    if (data.debug) { logDebug "disoverGlobalElementsStateMachine: st:${st} retry:${retry} data:${data}" }
    switch (st) {
        case STATE_DISCOVER_GLOBAL_ELEMENTS_IDLE :  // should not happen !
            logWarn "disoverGlobalElementsStateMachine: st:${st} - idle -> unscheduling!"
            state['stateMachines']['discoverGlobalElementsResult'] = ERROR
            unschedule('disoverGlobalElementsStateMachine')
            break
        case STATE_DISCOVER_GLOBAL_ELEMENTS_INIT :
            if (data.endpoint != 0 || data.cluster != 0x001D) {
                // check whether the endpoint and the cluster are correct
                logWarn "disoverGlobalElementsStateMachine: st:${st} - TODO - check endpoint and cluster !!!"
                // TODO  !!!!!!!!
            }
            else {
                if (data.debug) { logDebug "disoverGlobalElementsStateMachine: st:${st} - starting ..." }
            }
            st = STATE_DISCOVER_GLOBAL_ELEMENTS_ATTRIBUTE_LIST
            // continue with the next state
        case STATE_DISCOVER_GLOBAL_ELEMENTS_ATTRIBUTE_LIST :
            state['stateMachines']['toBeConfirmed'] = [data.endpoint, data.cluster, 0xFFFB];
            state['stateMachines']['Confirmation'] = false
            readAttribute(data.endpoint, data.cluster, 0xFFFB)
            retry = 0; st = STATE_DISCOVER_GLOBAL_ELEMENTS_ATTRIBUTE_LIST_WAIT
            break
        case STATE_DISCOVER_GLOBAL_ELEMENTS_ATTRIBUTE_LIST_WAIT:
            if (state['stateMachines']['Confirmation'] == true) {
                if (data.debug) { logDebug "disoverGlobalElementsStateMachine: st:${st} - received reading confirmation!" }
                // here we have bridgeDescriptor/fingerprintNN : {AttributeList=[00, 01, 02, 03, FFF8, FFF9, FFFB, FFFC, FFFD]}
                // read all the attributes from the ['AttributeList']
                List<Map<String, String>> attributePaths = []
                String fingerprintName = getFingerprintName(data.endpoint)
                String stateClusterName = getStateClusterName([cluster: HexUtils.integerToHexString(data.cluster,2), attrId: 'FFFB'])
                List<Integer> attributeList = state[fingerprintName][stateClusterName].collect { HexUtils.hexStringToInt(it) }
                attributeList.each { attrId ->
                    attributePaths.add(matter.attributePath(data.endpoint, data.cluster, attrId))
                }
                state['stateMachines']['Confirmation'] = false
                state['stateMachines']['toBeConfirmed'] = [data.endpoint, data.cluster, attributeList.last()]
                sendToDevice(matter.readAttributes(attributePaths))
                retry = 0; st = STATE_DISCOVER_GLOBAL_ELEMENTS_GLOBAL_ELEMENTS_WAIT
            }
            else {
                logTrace "disoverGlobalElementsStateMachine: st:${st} - waiting for the attribute value"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "disoverGlobalElementsStateMachine: st:${st} - timeout waiting for the attribute value !"
                    st = STATE_DISCOVER_GLOBAL_ELEMENTS_ERROR
                }
            }
            break
        case STATE_DISCOVER_GLOBAL_ELEMENTS_GLOBAL_ELEMENTS_WAIT:
            if (state['stateMachines']['Confirmation'] == true) {
                if (data.debug) { logDebug "disoverGlobalElementsStateMachine: st:${st} - received reading confirmation!" }
                st = STATE_DISCOVER_GLOBAL_ELEMENTS_END
            }
            else {
                logTrace "disoverGlobalElementsStateMachine: st:${st} - waiting for the attribute value"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "disoverGlobalElementsStateMachine: st:${st} - timeout waiting for the attribute value !"
                    st = STATE_DISCOVER_GLOBAL_ELEMENTS_ERROR
                }
            }
            break
        case STATE_DISCOVER_GLOBAL_ELEMENTS_ERROR : // 98 - error
            logWarn "disoverGlobalElementsStateMachine: st:${st} - error"
            sendInfoEvent("ERROR during the Matter Bridge and Devices discovery")
            state.states['isInfo'] = false
            st = 0
            break
        case STATE_DISCOVER_GLOBAL_ELEMENTS_END : // 99 - end
            state.states['isInfo'] = false
            state['stateMachines']['discoverGlobalElementsResult'] = SUCCESS
            if (data.debug) { logDebug "disoverGlobalElementsStateMachine: st:${st} - THE END" }
            st = 0
            break
        default :    // error
            state.states['isInfo'] = false
            state['stateMachines']['discoverGlobalElementsResult'] = ERROR
            st = 0
            break
    }
    if (st != 0) {
        state['stateMachines']['discoverGlobalElementsState'] = st
        state['stateMachines']['discoverGlobalElementsRetry'] = retry
        if (data != null) {
            data['action'] = RUNNING
        }
        runInMillis(STATE_MACHINE_PERIOD, disoverGlobalElementsStateMachine, [overwrite: true, data: data])
    }
    else {
        state.states['isInfo'] = false
        //logWarn "disoverGlobalElementsStateMachine: st:${st} - THE END"
    }
}

/*
 ********************************************* discoverAllStateMachine  *********************************************
 */

@Field static final Integer DISCOVER_ALL_STATE_IDLE                                 = 0
@Field static final Integer DISCOVER_ALL_STATE_INIT                                 = 1

@Field static final Integer DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS                = 101
@Field static final Integer DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS_WAIT           = 102

@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_ATTRIBUTE_LIST             = 2
@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_ATTRIBUTE_LIST_WAIT        = 3
@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_GLOBAL_ELEMENTS            = 4
@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_GLOBAL_ELEMENTS_WAIT       = 5
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST          = 6
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST_WAIT     = 7
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES        = 8
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES_WAIT   = 9
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_EXTENDED_INFO_ATTR_VALUES_RESULT = 10
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_EXTENDED_INFO_ATTR_VALUES_RESULT_WAIT = 11
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_IDENTIFY                      = 12
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_IDENTIFY_WAIT                 = 13
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS           = 14
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS_WAIT      = 15

@Field static final Integer DISCOVER_ALL_STATE_GET_PARTS_LIST_START                 = 20

@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_CLUSTER                    = 70
@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_CLUSTER_WAIT               = 71

@Field static final Integer DISCOVER_ALL_STATE_ERROR                                = 98
@Field static final Integer DISCOVER_ALL_STATE_END                                  = 99


/****************************************** discoverAllStateMachine ******************************************
 *
 * This is the main state machine for discovering the Matter Bridge and the bridged devices.
 *
 * @param data (optional) A map containing additional data to control the state machine execution.
 *             The following keys are supported:    TODO !!!
    *             - action: START, STOP, RUNNING
    *             - endpoint: the endpoint to be discovered
    *             - cluster: the cluster to be discovered
    *             - attribute: the attribute to be discovered
 */
void discoverAllStateMachine(Map data = null) {
    if (state['stateMachines'] == null) { state['stateMachines'] = [] }
    if (state['stateMachines']['discoverAllState'] == null) { state['stateMachines']['discoverAllState'] = DISCOVER_ALL_STATE_IDLE }
    if (state['stateMachines']['discoverAllRetry'] == null) { state['stateMachines']['discoverAllRetry'] = 0 }
    if (state['stateMachines']['discoverAllResult'] == null) { state['stateMachines']['discoverAllResult'] = UNKNOWN }

    if (data != null) {
        if (data['action'] == START) {
            state['stateMachines']['discoverAllState']  = DISCOVER_ALL_STATE_INIT
            state['stateMachines']['discoverAllRetry']  = 0
            state['stateMachines']['discoverAllResult'] = UNKNOWN
            data['action'] = RUNNING
        }
    }
    logTrace "discoverAllStateMachine: data:${data}, state['stateMachines'] = ${state['stateMachines']}"

    Integer st =    state['stateMachines']['discoverAllState']
    Integer retry = state['stateMachines']['discoverAllRetry']
    //String fingerprintName = getFingerprintName(data.endpoint)
    //String attributeName = getAttributeName([cluster: HexUtils.integerToHexString(data.cluster, 2), attrId: HexUtils.integerToHexString(data.attribute, 2)])
    logTrace "discoverAllStateMachine: st:${st} retry:${retry} data:${data}"
    switch (st) {
        case DISCOVER_ALL_STATE_IDLE :
            logDebug "discoverAllStateMachine: st:${st} - idle -> unscheduling!"
            unschedule('discoverAllStateMachine')
            break
        case DISCOVER_ALL_STATE_INIT: // start (collectBasicInfo())
            sendInfoEvent("Starting Matter Bridge and Devices discovery ...")
            if (state.bridgeDescriptor == null) { state.bridgeDescriptor = [] } // or state['bridgeDescriptor'] = [:] ?
            state.states['isInfo'] = true
            state['stateMachines']['discoverAllResult'] = RUNNING
            // TODO
            //st = DISCOVER_ALL_STATE_DESCIPTOR_ATTRIBUTE_LIST
            st = DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS
            // continue with the next state
        case DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS :
            disoverGlobalElementsStateMachine([action: START, endpoint: 0, cluster: 0x001D, debug: false])
            retry = 0; st = DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS_WAIT
            break
        case DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS_WAIT:
            if (state['stateMachines']['discoverGlobalElementsResult']  == SUCCESS) {
                logDebug "discoverAllStateMachine: st:${st} - received discoverGlobalElementsResult confirmation!"
                st = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value !"
                    st = DISCOVER_ALL_STATE_ERROR
                }
            }
            break
        case DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST :
            // Basic Info cluster 0x0028
            readAttribute(0, 0x0028, 0xFFFB)
            state['stateMachines']['toBeConfirmed'] = [0, 0x0028, 0xFFFB];  state['stateMachines']['Confirmation'] = false
            retry = 0; st = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST_WAIT
            break
        case DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST_WAIT :
            if (state['stateMachines']['Confirmation'] == true) {
                logTrace "discoverAllStateMachine: st:${st} - received reading confirmation!"
                st = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value !"
                    st = DISCOVER_ALL_STATE_ERROR
                }
            }
            break
        case DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES :
            // here we have bridgeDescriptor :  0028_FFFB=[00, 01, 02, 03, 04, 05, 06, 07, 08, 09, 0A, 0B, 0C, 0D, 0E, 0F, 10, 11, 12, 13, FFF8, FFF9, FFFB, FFFC, FFFD]
            // read all the attributes from the bridgeDescriptor['0028_FFFB']
            List<Map<String, String>> attributePaths = []
            List<Integer> attributeList = state.bridgeDescriptor['0028_FFFB'].collect { HexUtils.hexStringToInt(it) }
            attributeList.each { attrId ->
                attributePaths.add(matter.attributePath(0, 0x0028, attrId))
            }
            state.states['isInfo'] = true
            state.states['cluster'] = "0028"
            state.tmp = null
            state['stateMachines']['Confirmation'] = false
            state['stateMachines']['toBeConfirmed'] = [0, 0x0028, attributeList.last()]
            sendToDevice(matter.readAttributes(attributePaths))
            retry = 0; st = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES_WAIT
            break
        case DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES_WAIT :
            if (state['stateMachines']['Confirmation'] == true) {
                logTrace "discoverAllStateMachine: st:${st} - received bridgeDescriptor Basic Info reading confirmation!"

                logRequestedClusterAttrResult([cluster:0x28, endpoint:0])

                sendInfoEvent("(A1) Matter Bridge discovery completed")
                
                // check if the Indentify cluster 03 is in the ServerList
                List<String> serverList = state.bridgeDescriptor['ServerList']
                if (serverList?.contains("03")) {
                    
                    state.states['isInfo'] = true
                    state.states['cluster'] = "0003"
                    state.tmp = null
                    state['stateMachines']['Confirmation'] = false
                    state['stateMachines']['toBeConfirmed'] = [0, 0x0003, serverList.last()]
                    
                    disoverGlobalElementsStateMachine([action: START, endpoint: 0, cluster: 0x0003, debug: false])
                    retry = 0; st = DISCOVER_ALL_STATE_BRIDGE_IDENTIFY_WAIT
                }
                else {
                    st = DISCOVER_ALL_STATE_END
                }
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value !"
                    st = DISCOVER_ALL_STATE_ERROR
                }
            }
            break
        case DISCOVER_ALL_STATE_BRIDGE_IDENTIFY :
        case DISCOVER_ALL_STATE_BRIDGE_IDENTIFY_WAIT :
            if (state['stateMachines']['discoverGlobalElementsResult']  == SUCCESS) {
                logDebug "discoverAllStateMachine: st:${st} - received Indentify cluster confirmation!"
                logRequestedClusterAttrResult([cluster: 0x0003, endpoint: 0])

                // check if the General Diagnostics cluster 0x0033 is in the ServerList
                List<String> serverList = state.bridgeDescriptor['ServerList']
                if (serverList?.contains("33")) {
                    
                    state.states['isInfo'] = true
                    state.tmp = null
                    state['stateMachines']['Confirmation'] = false
                    state['stateMachines']['toBeConfirmed'] = [0, 0x0033, serverList.last()]
                    state.states['cluster'] = "0033"
                    
                    disoverGlobalElementsStateMachine([action: START, endpoint: 0, cluster: 0x0033, debug: false])
                    retry = 0; st = DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS_WAIT
                }
                else {
                    st = DISCOVER_ALL_STATE_END
                }
                st = DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS_WAIT
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value !"
                    st = DISCOVER_ALL_STATE_ERROR
                }
            }
            break
        case DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS :
        case DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS_WAIT :
            if (state['stateMachines']['discoverGlobalElementsResult']  == SUCCESS) {
                logDebug "discoverAllStateMachine: st:${st} - received General Diagnostics confirmation!"
                logRequestedClusterAttrResult([cluster: 0x0033, endpoint: 0])
                st = DISCOVER_ALL_STATE_END
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value !"
                    st = DISCOVER_ALL_STATE_ERROR
                }
            }
            break


        // TODO (optional) - explore the Bridge Extended Info
        case DISCOVER_ALL_STATE_BRIDGE_EXTENDED_INFO_ATTR_VALUES_RESULT :
            // TODO 
            st = DISCOVER_ALL_STATE_BRIDGE_EXTENDED_INFO_ATTR_VALUES_RESULT_WAIT
            break
        case DISCOVER_ALL_STATE_BRIDGE_EXTENDED_INFO_ATTR_VALUES_RESULT_WAIT :
            // TODO 
            st = DISCOVER_ALL_STATE_END
            break
        
        case DISCOVER_ALL_STATE_GET_PARTS_LIST_START :
            // TODO 
            sendInfoEvent("(A2) Starting Bridged Devices discovery")
            // for each bridged device endpoint in the state.bridgeDescriptor['PartsList'] we need to read the ServerList 

            st = DISCOVER_ALL_STATE_END
            break








        case DISCOVER_ALL_STATE_ERROR : // 98 - error
            logDebug "discoverAllStateMachine: st:${st} - error"
            sendInfoEvent("ERROR during the Matter Bridge and Devices discovery")
            state.states['isInfo'] = false
            state['stateMachines']['discoverAllResult'] = ERROR
            st = 0
            break
        case DISCOVER_ALL_STATE_END : // 99 - end
            state.states['isInfo'] = false
            logDebug "discoverAllStateMachine: st:${st} - THE END"
            sendInfoEvent("*** END of the Matter Bridge and Devices discovery ***")
            state['stateMachines']['discoverAllResult'] = SUCCESS
            st = 0
            break
        default :    // error
            state.states['isInfo'] = false
            st = 0
            break
    }
    if (st != 0) {
        state['stateMachines']['discoverAllState'] = st
        state['stateMachines']['discoverAllRetry'] = retry
        if (data != null) {
            data['action'] = RUNNING
        }
        runInMillis(STATE_MACHINE_PERIOD * 3, discoverAllStateMachine, [overwrite: true, data: data])
    }
    else {
        state.states['isInfo'] = false
        //logWarn "discoverAllStateMachine: st:${st} - THE END"
    }
}



