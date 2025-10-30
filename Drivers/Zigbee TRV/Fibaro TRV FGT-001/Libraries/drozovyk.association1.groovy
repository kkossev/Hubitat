#include drozovyk.encapsulation1

library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Association helpers",
    name: "association1",
    namespace: "drozovyk"   
)

// Driver MUST declare such map according to its specs
//@Field static Map<Short, String> associationGroups = [
//    (1): "groupLifeline", 
//    (2): "groupRetransmit",
//    (3): "groupSwitch1",
//    (4): "groupSwitch2"
//]

// Association
void zwaveEvent(hubitat.zwave.commands.associationv1.AssociationGroupingsReport cmd, Short ep = 0) {
    List<String> cmds = []
    for (Integer group = cmd.supportedGroupings; group > 0; group--) {
        cmds.add(encapsulate(zwave.associationV1.associationGet(groupingIdentifier: group)))
    }
    
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

private void getDeviceGroups() {
    String cmd = encapsulate(zwave.associationV1.associationGroupingsGet())
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

void zwaveEvent(hubitat.zwave.commands.associationv1.AssociationReport cmd, Short ep = 0) {
    // For some reason 'containsKey' and 'getAt' fails. Don't know why.
    def groupEntry = associationGroups.find({
        return it.key == cmd.groupingIdentifier
    })
    
    if(groupEntry) {
        state[groupEntry.value] = cmd.nodeId
    }
}

private List<String> getDeviceGroupNodesCommandList() {
    List<String> commandList= []
    
    associationGroups.each({
        commandList << encapsulate(zwave.associationV1.associationGet(groupingIdentifier: it.key))
    })
    
    return commandList
}

private void addDeviceNodeToGroup(groupId, nodeId) {
    String cmd = encapsulate(zwave.associationV1.associationSet(groupingIdentifier: groupId, nodeId : nodeId))
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

private void removeDeviceNodeFromGroup(groupId, nodeId) {
    String cmd = encapsulate(zwave.associationV1.associationRemove(groupingIdentifier: groupId, nodeId : nodeId))
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

// Multi Channel Association
void zwaveEvent(hubitat.zwave.commands.multichannelassociationv3.MultiChannelAssociationGroupingsReport cmd, Short ep = 0) {
    List<String> cmds = []
    for (Integer group = cmd.supportedGroupings; group > 0; group--) {
        cmds.add(encapsulate(zwave.multiChannelAssociationV3.multiChannelAssociationGet(groupingIdentifier: group)))
    }
    
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

private void getDeviceMultiChannelGroups() {
    String cmd = encapsulate(hubitat.zwave.commands.multiChannelAssociationV3.multiChannelAssociationGroupingsGet())
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

void zwaveEvent(hubitat.zwave.commands.multichannelassociationv3.MultiChannelAssociationReport cmd, Short ep = 0) {
    // For some reason 'containsKey' and 'getAt' fails. Don't know why.
    def groupEntry = associationGroups.find({
        return it.key == cmd.groupingIdentifier
    })
    
    if(groupEntry) {
        state[groupEntry.value] = cmd.nodeId + cmd.multiChannelNodeIds
    }
}

private List<String> getDeviceMultiChannelGroupNodesCommandList() {
    List<String> commandList= []
    
    associationGroups.each({
        commandList << encapsulate(zwave.multiChannelAssociationV3.multiChannelAssociationGet(groupingIdentifier: it.key))
    })
    
    return commandList
}

private void addDeviceEndpointToGroup(groupId, nodeId, endpointId) {
    // bitAddress -> choose to use single id or an id bitfield
    String cmd = encapsulate(zwave.multiChannelAssociationV3.multiChannelAssociationSet(groupingIdentifier: groupId, multiChannelNodeIds : [[nodeId:nodeId as Short, bitAddress: 0, endPointId: endpointId as Short]]))
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

private void removeDeviceEndpointFromGroup(groupId, nodeId, endpointId) {
    // bitAddress -> choose to use single id or an id bitfield (each bit corresponds to some id [0..6])
    String cmd = encapsulate(zwave.multiChannelAssociationV3.multiChannelAssociationRemove(groupingIdentifier: groupId, multiChannelNodeIds : [[nodeId:nodeId as Short, bitAddress: 0, endPointId: endpointId as Short]]))
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}
