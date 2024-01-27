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
  * ver. 1.0.0  2024-01-27 kkossev  - first version
  *
  *                                   TODO:
  *
*/

import groovy.transform.Field

/* groovylint-disable-next-line ImplicitReturnStatement */
@Field static final String matterStateMachinesLib = '1.0.0'
@Field static final String matterStateMachinesLibStamp   = '2024/01/27 8:31 AM'

// no metadata section for matterStateMachinesLib
@Field static final String  START   = 'START'
@Field static final String  STOP    = 'STOP'
//                          UNKNOWN = 'UNKNOWN'
@Field static final String  RUNNING = 'RUNNING'
@Field static final String  SUCCESS = 'SUCCESS'
@Field static final String  ERROR   = 'ERROR'

@Field static final Integer STATE_MACHINE_PERIOD = 250      // milliseconds
@Field static final Integer STATE_MACHINE_MAX_RETRIES = 15

void initializeStateMachineVars() {
    if (state['stateMachines'] == null) { state['stateMachines'] = [] }
    if (state['stateMachines']['readSingeAttrState'] == null) { state['stateMachines']['readSingeAttrState'] = 0 }
    if (state['stateMachines']['readSingeAttrRetry'] == null) { state['stateMachines']['readSingeAttrRetry'] = 0 }
    if (state['stateMachines']['discoverGlobalElementsState'] == null) { state['stateMachines']['discoverGlobalElementsState'] = STATE_DISCOVER_GLOBAL_ELEMENTS_IDLE }
    if (state['stateMachines']['discoverGlobalElementsRetry'] == null) { state['stateMachines']['discoverGlobalElementsRetry'] = 0 }
    if (state['stateMachines']['discoverGlobalElementsResult'] == null) { state['stateMachines']['discoverGlobalElementsResult'] = UNKNOWN }
    if (state['stateMachines']['discoverAllState'] == null) { state['stateMachines']['discoverAllState'] = DISCOVER_ALL_STATE_IDLE }
    if (state['stateMachines']['discoverAllRetry'] == null) { state['stateMachines']['discoverAllRetry'] = 0 }
    if (state['stateMachines']['discoverAllResult'] == null) { state['stateMachines']['discoverAllResult'] = UNKNOWN }
}

void readSingeAttrStateMachine(Map data = null) {
    initializeStateMachineVars()

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
                logWarn 'run steps A1 and A2 !'
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
                logWarn 'run steps A1 and A2 !'
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
                logWarn 'run steps A1 and A2 !'
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
                logTrace "readSingeAttrStateMachine: st:${st} - waiting for the attribute value (retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "readSingeAttrStateMachine: st:${st} - timeout waiting for the attribute value (retry=${retry})!"
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
}

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
 * Uses AND FILLS IN the common for all state machines state['stateMachines']['toBeConfirmed']  - [endpoint, cluster, 0xFFFB] and state['stateMachines']['Confirmation'] !
 *
 * The calling function must check the state['stateMachines']['discoverGlobalElementsResult'] to determine the result of the state machine execution.
 */

void disoverGlobalElementsStateMachine(Map data) {
    initializeStateMachineVars()
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
            if (data.debug) { logDebug "disoverGlobalElementsStateMachine: st:${st} endpoint ${data.endpoint} attribute ${data.attribute}- starting ..." }
            st = STATE_DISCOVER_GLOBAL_ELEMENTS_ATTRIBUTE_LIST
            // continue with the next state
        case STATE_DISCOVER_GLOBAL_ELEMENTS_ATTRIBUTE_LIST :
            state['stateMachines']['toBeConfirmed'] = [data.endpoint, data.cluster, 0xFFFB]
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
                String stateClusterName = getStateClusterName([cluster: HexUtils.integerToHexString(data.cluster, 2), attrId: 'FFFB'])
                //logWarn "disoverGlobalElementsStateMachine: st:${st} - fingerprintName:${fingerprintName}, stateClusterName:${stateClusterName}, state[fingerprintName][stateClusterName]:${state[fingerprintName][stateClusterName]}"
                List<Integer> attributeList = []
                if (state[fingerprintName] != null && state[fingerprintName][stateClusterName] != null) {
                    attributeList = state[fingerprintName][stateClusterName].collect { HexUtils.hexStringToInt(it) }
                } else {
                    logWarn "disoverGlobalElementsStateMachine: st:${st} - attributeList is null !"
                    st = STATE_DISCOVER_GLOBAL_ELEMENTS_ERROR
                    break
                }
                if (attributeList == null) {
                    logWarn "disoverGlobalElementsStateMachine: st:${st} - attributeList is null !"
                    st = STATE_DISCOVER_GLOBAL_ELEMENTS_ERROR
                    break
                }
                attributeList.each { attrId ->
                    attributePaths.add(matter.attributePath(data.endpoint, data.cluster, attrId))
                }
                state['stateMachines']['Confirmation'] = false
                state['stateMachines']['toBeConfirmed'] = [data.endpoint, data.cluster, attributeList.last()]
                sendToDevice(matter.readAttributes(attributePaths))
                retry = 0; st = STATE_DISCOVER_GLOBAL_ELEMENTS_GLOBAL_ELEMENTS_WAIT
            }
            else {
                logTrace "disoverGlobalElementsStateMachine: st:${st} - waiting for the attribute value (retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "disoverGlobalElementsStateMachine: st:${st} - timeout waiting for the attribute value (retry=${retry})!"
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
                logTrace "disoverGlobalElementsStateMachine: st:${st} - waiting for the attribute value (retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "disoverGlobalElementsStateMachine: st:${st} - timeout waiting for the attribute value (retry=${retry})!"
                    st = STATE_DISCOVER_GLOBAL_ELEMENTS_ERROR
                }
            }
            break
        case STATE_DISCOVER_GLOBAL_ELEMENTS_ERROR : // 98 - error
            logWarn "disoverGlobalElementsStateMachine: st:${st} - error"
            sendInfoEvent('ERROR during the Matter Bridge and Devices discovery')
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
    }
}

/*
 ********************************************* discoverAllStateMachine  *********************************************
 */

@Field static final Integer DISCOVER_ALL_STATE_IDLE                                     = 0
@Field static final Integer DISCOVER_ALL_STATE_INIT                                     = 1

@Field static final Integer DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS                    = 101
@Field static final Integer DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS_WAIT               = 102

@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_ATTRIBUTE_LIST                 = 2
@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_ATTRIBUTE_LIST_WAIT            = 3
@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_GLOBAL_ELEMENTS                = 4
@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_GLOBAL_ELEMENTS_WAIT           = 5
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST              = 6
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST_WAIT         = 7
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES            = 8
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES_WAIT       = 9
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_EXTENDED_INFO_ATTR_VALUES_RESULT = 10
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_EXTENDED_INFO_ATTR_VALUES_RESULT_WAIT = 11
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_IDENTIFY                          = 12
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_IDENTIFY_WAIT                     = 13
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS               = 14
@Field static final Integer DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS_WAIT          = 15

@Field static final Integer DISCOVER_ALL_STATE_GET_PARTS_LIST_START                     = 20
@Field static final Integer DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_STATE         = 21
@Field static final Integer DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_WAIT_STATE    = 22
@Field static final Integer DISCOVER_ALL_STATE_GET_BRIDGED_DEVICE_BASIC_INFO_STATE      = 23
@Field static final Integer DISCOVER_ALL_STATE_GET_BRIDGED_DEVICE_BASIC_INFO_WAIT_STATE  = 24
@Field static final Integer DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_START                 = 25
@Field static final Integer DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE     = 26
@Field static final Integer DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_WAIT                  = 27
@Field static final Integer DISCOVER_ALL_STATE_SUBSCRIBE_KNOWN_CLUSTERS                 = 30

@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_CLUSTER                        = 70
@Field static final Integer DISCOVER_ALL_STATE_DESCIPTOR_CLUSTER_WAIT                   = 71

@Field static final Integer DISCOVER_ALL_STATE_NEXT_STATE                               = 80
@Field static final Integer DISCOVER_ALL_STATE_ERROR                                    = 98
@Field static final Integer DISCOVER_ALL_STATE_END                                      = 99

void testObject(Object p) {
    logDebug "test: p:${p}"
    // print the object properties
    p.properties.each { prop ->
        logDebug "test: prop:${prop}"
    }
}

/****************************************** discoverAllStateMachine ******************************************
 *
 * This is the main state machine for discovering the Matter Bridge and the bridged devices.
 *
 * @param data (optional) A map containing additional data to control the state machine execution.
 *             The following keys are supported:    TODO !!!
    *             - action: START, STOP, RUNNING
    *             - endpoint: the endpoint to be discovered (0 for the bridge)      // TODO - check how the method is called !
    *             - cluster: the cluster to be discovered
    *             - attribute: the attribute to be discovered
 */
void discoverAllStateMachine(Map data = null) {
    initializeStateMachineVars()
    state['states']['isDiscovery'] = true

    if (data != null) {
        if (data['action'] == START) {
            if (data['goToState'] == null) {
                state['stateMachines']['discoverAllState']  = DISCOVER_ALL_STATE_INIT
            }
            else {
                state['stateMachines']['discoverAllState']  = data['goToState']
            }
            state['stateMachines']['discoverAllRetry']  = 0
            state['stateMachines']['discoverAllResult'] = UNKNOWN
            data['action'] = RUNNING
        }
    }
    logTrace "discoverAllStateMachine: data:${data}, state['stateMachines'] = ${state['stateMachines']}"

    Integer st =    state['stateMachines']['discoverAllState']
    Integer retry = state['stateMachines']['discoverAllRetry']
    Integer stateMachinePeriod = STATE_MACHINE_PERIOD              // can be changed, depending on the expected execution time of the different states
    //String fingerprintName = getFingerprintName(data.endpoint)
    //String attributeName = getAttributeName([cluster: HexUtils.integerToHexString(data.cluster, 2), attrId: HexUtils.integerToHexString(data.attribute, 2)])
    logTrace "discoverAllStateMachine: st:${st} retry:${retry} data:${data}"
    switch (st) {
        case DISCOVER_ALL_STATE_IDLE :
            logDebug "discoverAllStateMachine: st:${st} - idle -> unscheduling!"
            unschedule('discoverAllStateMachine')
            break
        case DISCOVER_ALL_STATE_INIT: // start (collectBasicInfo())
            sendInfoEvent('Starting Matter Bridge and Devices discovery ...')
            if (state.bridgeDescriptor == null) { state.bridgeDescriptor = [] } // or state['bridgeDescriptor'] = [:] ?
            state.states['isInfo'] = true
            state['stateMachines']['discoverAllResult'] = RUNNING
            // TODO
            //st = DISCOVER_ALL_STATE_DESCIPTOR_ATTRIBUTE_LIST
            st = DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS
            // continue with the next state
        case DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS :
            disoverGlobalElementsStateMachine([action: START, endpoint: 0, cluster: 0x001D, debug: false])
            stateMachinePeriod = STATE_MACHINE_PERIOD * 2
            retry = 0; st = DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS_WAIT
            break
        case DISCOVER_ALL_STATE_BRIDE_GLOBAL_ELEMENTS_WAIT:
            if (state['stateMachines']['discoverGlobalElementsResult']  == SUCCESS) {
                logDebug "discoverAllStateMachine: st:${st} - received discoverGlobalElementsResult confirmation!"
                st = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value (retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value (retry=${retry})!"
                    st = DISCOVER_ALL_STATE_ERROR
                }
            }
            break
        case DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST :
            // Basic Info cluster 0x0028
            readAttribute(0, 0x0028, 0xFFFB)
            // here we fill in 'toBeConfirmed' and 'Confirmation', because the readAttribute() is called directly !
            state['stateMachines']['toBeConfirmed'] = [0, 0x0028, 0xFFFB];  state['stateMachines']['Confirmation'] = false
            retry = 0; st = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST_WAIT
            break
        case DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST_WAIT :
            if (state['stateMachines']['Confirmation'] == true) {
                logTrace "discoverAllStateMachine: st:${st} - received reading confirmation!"
                st = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value (retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value (retry=${retry})!"
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
            state.states['cluster'] = '0028'
            state.tmp = null
            state['stateMachines']['Confirmation'] = false
            state['stateMachines']['toBeConfirmed'] = [0, 0x0028, attributeList.last()]
            // here we fill in 'toBeConfirmed' and 'Confirmation', because the readAttributes() is called directly !
            sendToDevice(matter.readAttributes(attributePaths))
            retry = 0; st = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES_WAIT
            break
        case DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_VALUES_WAIT :
            if (state['stateMachines']['Confirmation'] == true) {
                logTrace "discoverAllStateMachine: st:${st} - received bridgeDescriptor Basic Info reading confirmation!"
                logRequestedClusterAttrResult([cluster:0x28, endpoint:0])

                // check if the Indentify cluster 03 is in the ServerList
                List<String> serverList = state.bridgeDescriptor['ServerList']
                if (serverList?.contains('03')) {
                    state.states['isInfo'] = true
                    state.states['cluster'] = '0003'
                    state.tmp = null
                    // Do not call 'toBeConfirmed' and 'Confirmation' here - it is filled in in the disoverGlobalElementsStateMachine() !
                    disoverGlobalElementsStateMachine([action: START, endpoint: 0, cluster: 0x0003, debug: false])
                    stateMachinePeriod = STATE_MACHINE_PERIOD * 2
                    retry = 0; st = DISCOVER_ALL_STATE_BRIDGE_IDENTIFY_WAIT
                }
                else {
                    logWarn "discoverAllStateMachine: st:${st} - Identify cluster 0x0003 is not in the ServerList !"
                    st = DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS
                }
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value (retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value (retry=${retry})!"
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
                if (serverList?.contains('33')) {
                    stateMachinePeriod = 100        // go quickly ....
                    st = DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS
                }
                else {
                    st = DISCOVER_ALL_STATE_NEXT_STATE
                }
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value (retry=${retry}"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value (retry=${retry})!"
                    st = DISCOVER_ALL_STATE_ERROR
                }
            }
            break
        case DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS :
            // check if the General Diagnostics cluster 0x0033 is in the ServerList
            List<String> serverList = state.bridgeDescriptor['ServerList']
            logDebug "discoverAllStateMachine: DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS - serverList:${serverList}"
            if (serverList?.contains('33')) {
                logDebug "discoverAllStateMachine: st:${st} - found General Diagnostics cluster 0x0033 in the ServerList !"
                state.states['isInfo'] = true
                state.tmp = null
                state.states['cluster'] = '0033'
                // do not call 'toBeConfirmed' and 'Confirmation' here - it is filled in in the disoverGlobalElementsStateMachine() !
                disoverGlobalElementsStateMachine([action: START, endpoint: 0, cluster: 0x0033, debug: false])
                stateMachinePeriod = STATE_MACHINE_PERIOD * 2
                retry = 0; st = DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS_WAIT
            }
            else {
                logWarn "discoverAllStateMachine: st:${st} - General Diagnostics cluster 0x0033 is not in the ServerList !"
                st = DISCOVER_ALL_STATE_NEXT_STATE
            }
        case DISCOVER_ALL_STATE_BRIDGE_GENERAL_DIAGNOSTICS_WAIT :
            if (state['stateMachines']['discoverGlobalElementsResult']  == SUCCESS) {
                logDebug "discoverAllStateMachine: st:${st} - received General Diagnostics confirmation!"
                logRequestedClusterAttrResult([cluster: 0x0033, endpoint: 0])
                st = DISCOVER_ALL_STATE_GET_PARTS_LIST_START
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value retry=${retry})!"
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
            logDebug "discoverAllStateMachine: st:${st} - Starting Bridged Devices discovery ...<br><br><br><br><br><br><br><br><br>"
            sendInfoEvent('(A1) Matter Bridge discovery completed')
            sendInfoEvent('(A2) Starting Bridged Devices discovery')
            logDebug "discoverAllStateMachine: st:${st} - Getting the PartList ... state.bridgeDescriptor['PartsList'] = ${state.bridgeDescriptor['PartsList']}"
            Integer partsListCount = state.bridgeDescriptor['PartsList']?.size() ?: 0
            if (partsListCount == 0) {
                logWarn "discoverAllStateMachine: st:${st} - PartsList is empty !"
                st = DISCOVER_ALL_STATE_ERROR
                break
            }
            state['stateMachines']['discoverAllPartsListIndex'] = 0
            st = DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_STATE
            stateMachinePeriod = 100       // go quickly ....
            break
        case DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_STATE :
            Integer partsListCount = state.bridgeDescriptor['PartsList']?.size() ?: 0
            Integer partsListIndex = state['stateMachines']['discoverAllPartsListIndex']
            if (partsListIndex >= partsListCount) {
                logDebug "discoverAllStateMachine: st:${st} - all parts discovered (total #${partsListCount}) !"
                st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_START    // we are done with the parts list !
                break
            }
            String partEndpoint = state.bridgeDescriptor['PartsList'][partsListIndex]
            Integer partEndpointInt = HexUtils.hexStringToInt(partEndpoint)
            logDebug "discoverAllStateMachine: st:${st} - partEndpoint = ${partEndpoint} partEndpointInt = ${partEndpointInt}"
            state.states['isInfo'] = true
            state.states['cluster'] = '001D'     // HexUtils.integerToHexString(partEndpointInt, 2)
            state.tmp = null
            // do not call 'toBeConfirmed' and 'Confirmation' here - it is filled in in the disoverGlobalElementsStateMachine() !
            disoverGlobalElementsStateMachine([action: START, endpoint: partEndpointInt, cluster: 0x001D, debug: false])
            /////////////////////////////////////////////////////////////////////////////////////////////////////////////
            stateMachinePeriod = STATE_MACHINE_PERIOD * 3
            retry = 0; st = DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_WAIT_STATE
            break
        case DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_WAIT_STATE :
            Integer partsListIndex = state['stateMachines']['discoverAllPartsListIndex']
            String partEndpoint = state.bridgeDescriptor['PartsList'][partsListIndex]
            Integer partEndpointInt = HexUtils.hexStringToInt(partEndpoint)
            String fingerprintName = getFingerprintName(partEndpointInt)
            if (state['stateMachines']['discoverGlobalElementsResult']  == SUCCESS) {
                    //logWarn "AFTER : state.states['cluster'] = ${state.states['cluster']}"
                logDebug "discoverAllStateMachine: st:${st} - ['PartsList'][$partEndpoint] confirmation!"
                logRequestedClusterAttrResult([cluster: 0x001D, endpoint: partEndpointInt])
                //state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                sendInfoEvent("Found bridged device part #${partsListIndex} fingerprint ${fingerprintName}")
                // for each child device that has the BridgedDeviceBasicInformationCluster '39' in the ServerList ->  read the BridgedDeviceBasicInformationCluster attributes
                st = DISCOVER_ALL_STATE_GET_BRIDGED_DEVICE_BASIC_INFO_STATE
                //            st = DISCOVER_ALL_STATE_END
            }
            else {
                logTrace "discoverAllStateMachine: st:${st} - waiting for the attribute value retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - fingerprint${fingerprintName} timeout waiting for cluster 0x1D reading results !"
                    //st = DISCOVER_ALL_STATE_ERROR
                    // continue with the next device, even if there is an error
                    sendInfoEvent("ERROR discovering bridged device #${partsListIndex}")
                    state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                    st = DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_STATE
                }
            }
            break

        case DISCOVER_ALL_STATE_GET_BRIDGED_DEVICE_BASIC_INFO_STATE :   // 0x0039 is NOT OBLIGATORY !
            // for each child device that has the BridgedDeviceBasicInformationCluster '39' in the ServerList ->  read the BridgedDeviceBasicInformationCluster attributes
            Integer partsListIndex = state['stateMachines']['discoverAllPartsListIndex']
            String partEndpoint = state.bridgeDescriptor['PartsList'][partsListIndex]
            Integer partEndpointInt = HexUtils.hexStringToInt(partEndpoint)
            String fingerprintName = getFingerprintName(partEndpointInt)
            logDebug "discoverAllStateMachine: st:${st} - Getting the BridgedDeviceBasicInformationCluster attributes for endpoint ${partEndpoint} ...<br><br><br>"
            if (state[fingerprintName]['ServerList'].contains('39')) {
                state.states['isInfo'] = true
                state.states['cluster'] = '0039'     // HexUtils.integerToHexString(partEndpointInt, 2)
                state.tmp = null
                // do not call 'toBeConfirmed' and 'Confirmation' here - it is filled in in the disoverGlobalElementsStateMachine() !
                disoverGlobalElementsStateMachine([action: START, endpoint: partEndpointInt, cluster: 0x0039, debug: false])
                stateMachinePeriod = STATE_MACHINE_PERIOD * 2
                retry = 0; st = DISCOVER_ALL_STATE_GET_BRIDGED_DEVICE_BASIC_INFO_WAIT_STATE
            }
            else {
                logDebug "discoverAllStateMachine: st:${st} - fingerprint ${fingerprintName} BridgedDeviceBasicInformationCluster '39' is not in the ServerList ! (this is not obligatory)"
                state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                st = DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_STATE
                // check the next bridged device ...
            }
            break
        case DISCOVER_ALL_STATE_GET_BRIDGED_DEVICE_BASIC_INFO_WAIT_STATE :
            Integer partsListIndex = state['stateMachines']['discoverAllPartsListIndex'] ?: 0
            String partEndpoint = state.bridgeDescriptor['PartsList'][partsListIndex] ?: 0
            Integer partEndpointInt = HexUtils.hexStringToInt(partEndpoint)
            String fingerprintName = getFingerprintName(partEndpointInt)
            if (state['stateMachines']['discoverGlobalElementsResult']  == SUCCESS) {
                logDebug "discoverAllStateMachine: st:${st} - fingerprint ${fingerprintName} received BridgedDeviceBasicInformationCluster confirmation!"
                logRequestedClusterAttrResult([cluster: 0x0039, endpoint: partEndpointInt])
                state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                st = DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_STATE
            }
            else {
                logDebug "discoverAllStateMachine: st:${st} - waiting for the attribute value retry=${retry})"
                retry++
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value retry=${retry})!"
                    //st = DISCOVER_ALL_STATE_ERROR
                    // continue with the next device, even if there is an error
                    sendInfoEvent("ERROR discovering bridged device #${partsListIndex}")
                    state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                    st = DISCOVER_ALL_STATE_GET_PARTS_LIST_NEXT_DEVICE_STATE
                }
            }
            break

        // ------------------------------- preparing for subscription to the known clusters atteributes -> filling in state[fingerprintName]['Subscribe']  -------------------------------
        case DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_START :
            sendInfoEvent('(A2) Bridged Devices discovery completed')
            sendInfoEvent('(A3) Starting capabilities discovery')
            // next step is for each child device -  check the ServerList for useful clusters ..
            state['stateMachines']['discoverAllPartsListIndex'] = 0
            st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE
            stateMachinePeriod = 100       // go quickly ....
            break
        case DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE :
            Integer partsListIndex = state['stateMachines']['discoverAllPartsListIndex'] ?: 0
            Integer partsListCount = state.bridgeDescriptor['PartsList']?.size() ?: 0
            if (partsListIndex >= partsListCount) {
                logDebug "discoverAllStateMachine: st:${st} - all parts discovered (total #${partsListCount}) !"
                st = DISCOVER_ALL_STATE_SUBSCRIBE_KNOWN_CLUSTERS  // last step
                break
            }

            String partEndpoint = state.bridgeDescriptor['PartsList'][partsListIndex]
            Integer partEndpointInt = HexUtils.hexStringToInt(partEndpoint)
            String fingerprintName = getFingerprintName(partEndpointInt)
            logDebug "discoverAllStateMachine: st:${st} - fingerprint ${fingerprintName} Getting the SupportedClusters for endpoint ${partEndpoint} ...<br><br><br>"
            if (fingerprintName == null || state[fingerprintName] == null || state[fingerprintName]['ServerList'] == null) {
                logWarn "discoverAllStateMachine: st:${st} - fingerprintName ${fingerprintName} ServerList is empty !"
                state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE
                break
            }
            Integer supportedClustersCount = state[fingerprintName]['ServerList']?.size() ?: 0
            Integer supportedClustersIndex = state['stateMachines']['discoverAllSupportedClustersIndex'] ?: 0
            if (supportedClustersCount == 0) {
                logWarn "discoverAllStateMachine: st:${st} - fingerprintName ${fingerprintName} ServerList is empty !"
                state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE
                break
            }
            //List<Integer> supportedClusters = SupportedMatterClusters.collect { it.key }
            List<Integer> supportedClusters = SupportedMatterClusters*.key      // The spread-dot operator (*.) is used to invoke an action on all items of an aggregate object.
            List<Integer> serverListCluster = state[fingerprintName]['ServerList'].collect { HexUtils.hexStringToInt(it) }
            logTrace "discoverAllStateMachine: st:${st} - ServerListCluster = ${serverListCluster}"
            // check if any of the SupportedMatterClusters is in the ServerList
            // keep it simple for now - just check for the first one
            // find the first element in the supportedClusters that is in the ServerList
            Integer supportedCluster = supportedClusters.find { serverListCluster.contains(it) }
            if (supportedCluster != null) {
                logDebug "discoverAllStateMachine: st:${st} - fingerprintName ${fingerprintName} <b>found ${supportedCluster}</b> from the SupportedMatterClusters ${supportedClusters} in the ServerList ${ServerListCluster}"
                if (state[fingerprintName]['Subscribe'] == null) { state[fingerprintName]['Subscribe'] = [] }
                state[fingerprintName]['Subscribe'].add(HexUtils.integerToHexString(supportedCluster, 2))
                logInfo "discoverAllStateMachine: st:${st} - added subsubscription to cluster ${HexUtils.integerToHexString(supportedCluster, 2)} ..."

                // convert the figerprint data to a map needed for the createChildDevice() method
                logDebug "fingerPrintToData: fingerprintName:${fingerprintName}"
                Map deviceData = fingerprintToData(fingerprintName)
                logDebug "fingerPrintToData: deviceData:${deviceData}"
                boolean result = createChildDevices(deviceData)
                if (result == false) {
                    logWarn "discoverAllStateMachine: st:${st} - createChildDevice(${deviceData}) returned ${result}"
                    state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                    st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE
                    break
                }
                else {
                    logDebug "discoverAllStateMachine: st:${st} - createChildDevice(${deviceData}) returned ${result}"
                    // fingerPrintToData: deviceData:[id:08, fingerprintName:fingerprint08, product_name:Humidity Sensor, name:Device#08, ServerList:[1D, 03, 0405]]
                    sendInfoEvent("Created child device ${deviceData.name} (${deviceData.product_name})")
                }
                // now discove the supportedCluster attributes
                state.states['isInfo'] = true
                state.states['cluster'] = HexUtils.integerToHexString(supportedCluster, 2)
                state.tmp = null
                // do not call 'toBeConfirmed' and 'Confirmation' here - it is filled in in the disoverGlobalElementsStateMachine() !
                disoverGlobalElementsStateMachine([action: START, endpoint: partEndpointInt, cluster: supportedCluster, debug: false])
                stateMachinePeriod = STATE_MACHINE_PERIOD * 2
                retry = 0; st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_WAIT
            }
            else {
                logDebug "discoverAllStateMachine: st:${st} - fingerprintName ${fingerprintName} SupportedMatterClusters ${supportedClusters} are not in the ServerList ${ServerListCluster} !"
                state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE
            }
            break

        case DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_WAIT :
            Integer partsListIndex = state['stateMachines']['discoverAllPartsListIndex'] ?: 0
            String partEndpoint = state.bridgeDescriptor['PartsList'][partsListIndex] ?: 0
            Integer partEndpointInt = HexUtils.hexStringToInt(partEndpoint)
            String fingerprintName = getFingerprintName(partEndpointInt)
            if (state['stateMachines']['discoverGlobalElementsResult']  == SUCCESS) {
                logDebug "discoverAllStateMachine: st:${st} - fingerprint ${fingerprintName} received SupportedClusters confirmation!"
                logRequestedClusterAttrResult([cluster: HexUtils.hexStringToInt(state.states['cluster']), endpoint: partEndpointInt])
                state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE
            }
            else {
                logDebug "discoverAllStateMachine: st:${st} - waiting for the attribute value (retry=${retry})"
                retry++
                stateMachinePeriod = STATE_MACHINE_PERIOD * 2
                if (retry > STATE_MACHINE_MAX_RETRIES) {
                    logWarn "discoverAllStateMachine: st:${st} - timeout waiting for the attribute value retry=${retry})!"
                    //st = DISCOVER_ALL_STATE_ERROR
                    // continue with the next device, even if there is an error
                    sendInfoEvent("ERROR discovering bridged device #${partsListIndex}")
                    state['stateMachines']['discoverAllPartsListIndex'] = partsListIndex + 1
                    st = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_NEXT_DEVICE
                }
            }
            break

        case DISCOVER_ALL_STATE_SUBSCRIBE_KNOWN_CLUSTERS :
            a5SubscribeKnownClustersAttributes()
            reSubscribe()
            st = DISCOVER_ALL_STATE_NEXT_STATE
            break

        case DISCOVER_ALL_STATE_NEXT_STATE :
            logWarn "discoverAllStateMachine: st:${st} - DISCOVER_ALL_STATE_NEXT_STATE - anything else left?"
            st = DISCOVER_ALL_STATE_END
            break

        case DISCOVER_ALL_STATE_ERROR : // 98 - error
            logDebug "discoverAllStateMachine: st:${st} - error"
            sendInfoEvent('ERROR during the Matter Bridge and Devices discovery')
            state.states['isInfo'] = false
            state['stateMachines']['discoverAllResult'] = ERROR
            st = 0
            break
        case DISCOVER_ALL_STATE_END : // 99 - end
            state.states['isInfo'] = false
            logDebug "discoverAllStateMachine: st:${st} - THE END"
            sendInfoEvent('*** END of the Matter Bridge and Devices discovery ***')
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
        runInMillis(stateMachinePeriod, discoverAllStateMachine, [overwrite: true, data: data])
    }
    else {
        state.states['isInfo'] = false
        state['states']['isDiscovery'] = false
    }
}
