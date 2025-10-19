/**
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Modified from DTH by a4refillpad
 *
 *  01.10.2017 first release
 *  01.11.2018 Adapted the code to work with QBKG03LM
 *  21.04.2019 handling cluster 0006 to update the app device state when the buttons are pressed manually
 *             used code parts from: https://github.com/dschich/Smartthings/blob/master/devicetypes/dschich/Aqara-Switch-QBKG12LM.src/Aqara-Switch-QBKG12LM.groovy  
 *  20.06.2019 - 12.08.2020 modified by @aonghus-mor 
 *  12.08.2020 modified by @aonghus-mor to recognise QBKG21LM & QBKG22LM (but not yet QBKG25LM).
 *  13.10.2020 New version by @aonghus-mor for new smartthigs app.
 *  27.10.2020 Adapted for the new 3 button switch QBKG25LM ( Thanks to @Chiu for his help).
 *  09.03.2021 Extensive revision by @aonghus-mor, including own child DH.
 *  03.07.2021 Added support for unwired switches, WXKG06LM & WXKG06LM, and simple switch, Lumi WS-USC01
 *  20.11.2021 Now works in decoupled mode for newer switches QBKG21LM - QBKG26LM (Thanks to @mwtay84 for his help)
*/
 
import groovy.json.JsonOutput
import physicalgraph.zigbee.zcl.DataType

metadata 
{
    definition (	name: "Aqara Wall Switch", namespace: "aonghus-mor", author: "aonghus-mor",
    				//name: "testcode", namespace: "aonghus-mor", author: "aonghus-mor",
                	mnmn: "SmartThingsCommunity", 
                    vid: "0a242ce9-0299-3033-8860-aaab565eb04e",   // switch without neutral wire   
                    ocfDeviceType: "oic.d.switch"
                    //vid: "f7a15788-4d0f-323f-b061-010f145805a5", // switch with neutral wire
                    //ocfDeviceType: "oic.d.switch"
                    //vid: "52bbf611-e8b6-3530-89ac-9a4415b48045", // button (no battery)
                    //ocfDeviceType: "x.com.st.d.remotecontroller"
                    //vid: "1c4f60a8-b69f-37dd-9f1b-235e1d6f54bc",// button (with battery)
                    //ocfDeviceType: "x.com.st.d.remotecontroller" 
                    //vid: "2a0d4f73-869d-3e3e-93bf-8ef8e45de69d", // decoupled (no neutral)
                    //ocfDeviceType: "x.com.st.d.remotecontroller"
                    //vid: "5d9177d1-3af5-30b9-b32e-f2c109aa590b", // decoupled (with neutral)
                    //ocfDeviceType: "x.com.st.d.remotecontroller"
                )
    {
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Momentary"
        capability "Button"
        capability "Temperature Measurement"
        capability "Health Check"
        capability "Power Meter"
        capability "Energy Meter"
        capability "Battery"
        capability "Voltage Measurement"
        
        command "childOn"
        command "childOff"
        command "childToggle"
        command "childRefresh"
        
        attribute "lastCheckin", "string"
        attribute "lastPressType", "enum", ["soft","hard","both","held","released","refresh","double"]
        
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0001,0002,0003,0004,0005,0006,0010,000A", outClusters: "0019,000A", 
        		manufacturer: "LUMI", model: "lumi.ctrl_neutral2", deviceJoinName: "Aqara Switch QBKG03LM"
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.ctrl_neutral1", deviceJoinName: "Aqara Switch QBKG04LM"
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.ctrl_ln1.aq1", deviceJoinName: "Aqara Switch QBKG11LM"      
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.ctrl_ln2.aq1", deviceJoinName: "Aqara Switch QBKG12LM"       
    	fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b1lacn02", deviceJoinName: "Aqara Switch QBKG21LM"
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b2lacn02", deviceJoinName: "Aqara Switch QBKG22LM"
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b1nacn02", deviceJoinName: "Aqara Switch QBKG23LM"
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b2nacn02", deviceJoinName: "Aqara Switch QBKG24LM"
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.l3acn3", deviceJoinName: "Aqara Switch QBKG25LM" 
        fingerprint profileId: "0104", deviceId: "0051", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.n3acn3", deviceJoinName: "Aqara Switch QBKG26LM" 
        fingerprint profileId: "0104", deviceId: "5F01", inClusters: "0000,0003,0019,0012,FFFF", outClusters: "0000,0003,0004,0005,0019,0012,FFFF", 
        		manufacturer: "LUMI", model: "lumi.remote.b186acn01", deviceJoinName: "Aqara Switch WXKG03LM (2018)"
        fingerprint profileId: "0104", deviceId: "5F01", inClusters: "0000 0003 0019 FFFF 0012", outClusters: "0000 0004 0003 0005 0019 FFFF 0012", 
        		manufacturer: "LUMI", model: "lumi.remote.b186acn02", deviceJoinName: "Aqara Switch WXKG06LM"
        fingerprint profileId: "0104", deviceId: "5F01", inClusters: "0000,0003,0019,0012,FFFF", outClusters: "0000,0003,0004,0005,0019,0012,FFFF", 
        		manufacturer: "LUMI", model: "lumi.remote.b286acn01", deviceJoinName: "Aqara Switch WXKG02LM (2018)"
        fingerprint profileId: "0104", deviceId: "5F01", inClusters: "0000,0003,0019,0012,FFFF", outClusters: "0000,0003,0004,0005,0019,0012,FFFF", 
         		manufacturer: "LUMI", model: "lumi.remote.b286acn02", deviceJoinName: "Aqara Switch WXKG07LM (2020)"       
  		fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b1laus01", deviceJoinName: "Lumi WS-USC01" 
        fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b2laus01", deviceJoinName: "Lumi WS-USC02"      
        fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.l1aeu1", deviceJoinName: "Aqara Switch EU-01" 
        fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.l2aeu1", deviceJoinName: "Aqara Switch EU-02"              
    	fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b1lc04", deviceJoinName: "Aqara Switch QBKG38LM" 
        fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b2lc04", deviceJoinName: "Aqara Switch QBKG39LM"      
        fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b1nc01", deviceJoinName: "Aqara Switch QBKG40LM" 
        fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b2nc01", deviceJoinName: "Aqara Switch QBKG41LM"        }
	
    preferences 
    {	
        input name: "unwired", type: "bool", title: "Is this switch unwired?", required: true, displayDuringSetup: true
        input name: "decoupled", type: "bool", title: "Decoupled Mode?", required: false, displayDuringSetup: false
        input name: "tempOffset", type: "decimal", title:"Temperature Offset", 
        							description:"Adjust temperature by this many degrees", range:"*..*", required: false, displayDuringSetup: false                         
        input name: "infoLogging", type: "bool", title: "Display info log messages?", required: false, displayDuringSetup: false
		input name: "debugLogging", type: "bool", title: "Display debug log messages?", required: false, displayDuringSetup: false
    }
}


// Parse incoming device messages to generate events
def parse(String description)
{
   	displayDebugLog( "Parsing '${description}'" )
    
    def dat = new Date()
    def newcheck = dat.time
    state.lastCheckTime = state.lastCheckTime == null ? 0 : state.lastCheckTime
    def diffcheck = newcheck - state.lastCheckTime
    //displayDebugLog(newcheck + " " + state.lastCheckTime + " " + diffcheck)
    state.lastCheckTime = newcheck
  
   	def events = []
   
   	if (description?.startsWith('catchall:')) 
		events = events + parseCatchAllMessage(description)
	else if (description?.startsWith('read attr -')) 
		events = events + parseReportAttributeMessage(description)
    else if (description?.startsWith('on/off: '))
        parseCustomMessage(description) 
    
   	def now = ( location.timeZone != null ) ? dat.format("HH:mm:ss EEE dd MMM '('zzz')'", location.timeZone) : dat.format("HH:mm:ss EEE dd MMM '('zzz')'")
    events << createEvent(name: "lastCheckin", value: now, descriptionText: "Check-In", displayed: debugLogging)
    
    displayDebugLog( "Parse returned: $events" )
    return events
}

def updateTemp()
{
	// every half hour get the temperature
    def dat = new Date()
    def cmd = null
    if ( dat.time - state.lastTempTime > 1800000 ) 
    {
    	log.debug "Requesting Temperature"
        state.lastTempTime = dat.time
        cmd = [response(delayBetween(zigbee.readAttribute(0x0002,0),1000))]
    }
	return cmd
}

private def parseCatchAllMessage(String description) 
{
	def cluster = zigbee.parse(description)
	displayDebugLog( cluster )
    def events = []
    
    switch ( cluster.clusterId ) 
    {
    	case 0x0000: 
         	if ( cluster.command == 0x0a )
            {
            	if ( cluster.data[0] == 0x01 && cluster.data[1] == 0xff )
                {
                    Map dtMap = dataMap(cluster.data)
                    displayDebugLog( "Map: " + dtMap )
                    if ( ! state.numButtons )
                        getNumButtons()
                    events = events + setTemp( dtMap.get(3) ) +
                                    ( dtMap.get(152) != null ? getWatts( dtMap.get(152) ) : [] ) + 
                                    ( dtMap.get(149) != null ? getkWh( dtMap.get(149) ) : [] ) +
                                    ( dtMap.get(100) != null ? [] : getBattery( dtMap.get(1) ) )

                    displayDebugLog("Number of Switches: ${state.numSwitches}")
                    if ( dtMap.get(100) != null )
                    {
                        def onoff = (dtMap.get(100) ? "on" : "off")
                        switch ( state.numSwitches )
                        {
                            case 1:
                                displayInfoLog( "Hardware Switch is ${onoff}" )
                                displayDebugLog( 'Software Switch is ' + device.currentValue('switch') )
                                break
                            case 2:
                                def onoff2 = (dtMap.get(101) ? 'on' : 'off' )
                                //def child = getChild(2)
                                def child = getChildDevices()[0]
                                displayDebugLog( "Unwired Switches: ${state.unwiredSwitches}" )
                                displayDebugLog( "Decoupled Switches: ${state.decoupled}" )
                                displayDebugLog( "Hardware Switches are (" + onoff + "," + onoff2 +")" )
                                displayDebugLog( 'Software Switches are (' + device.currentValue('switch') + ',' + child.device.currentValue('switch') + ')' )

                                break
                            case 3:
                                def onoff2 = (dtMap.get(101) ? 'on' : 'off' )
                                def child2 = getChild(0)
                                def onoff3 = (dtMap.get(102) ? 'on' : 'off' )
                                def child3 = getChild(1)
                                displayDebugLog( "Unwired Switches: ${state.unwiredSwitches}" )
                                displayDebugLog( "Decoupled Switches: ${state.decoupled}" )
                                displayDebugLog( "Hardware Switches are (${onoff}, ${onoff2}, ${onoff3})" )
                                displayDebugLog( 'Software Switches are (' + device.currentValue('switch') + ',' + child2.device.currentValue('switch') + ',' + child3.device.currentValue('switch')+ ')' )

                                break

                            default:
                                displayDebugLog("Number of switches unrecognised: ${state.numSwitches}")
                        }
                    }
                }
            	else if ( cluster.data[0] == 0xf0 )
                {
                	state.holdDone = false
                    Map dtMap = dataMap(cluster.data)
                    displayDebugLog( "Map: " + dtMap )
    				runIn( 1, doHoldButton )
                }
                else
            	{
                    //Map dtMap = dataMap(cluster.data)
                    //displayDebugLog( "Map: " + dtMap )
                    displayDebugLog('CatchAll ignored.')
            	}
            }
        	break
        case 0x0006: 	
			//if ( state.oldOnOff )
            	//events = events + parseSwitchOnOff( [endpoint:cluster.sourceEndpoint.toString(), value: cluster.data[0].toString()] )
            //else
            	displayDebugLog('CatchAll message ignored!')
            break
    }
    return events
}

private def setTemp(int temp)
{ 
    def event = []
    temp = temp ? temp : 0
    if ( state.tempNow != temp || state.tempOffset != tempOffset )
    {
      	state.tempNow = temp
        state.tempOffset = tempOffset ? tempOffset : 0
        if ( getTemperatureScale() != "C" ) 
            temp = celsiusToFahrenheit(temp)
        state.tempNow2 = temp + state.tempOffset     
        event << createEvent(name: "temperature", value: state.tempNow2, unit: getTemperatureScale())
        displayDebugLog("Temperature is now ${state.tempNow2}°")          	
	}
    displayDebugLog("setTemp: ${event}")
    return event
}

private def getWatts(float pwr)
{
	def event = []
    pwr = pwr ? pwr : 0.0
    state.power = state.power ? state.power : 0.0
    if ( abs( pwr - (float)state.power ) > 1e-4 )
    {	
    	state.power = (float)pwr
    	event << createEvent(name: 'power', value: pwr, unit: 'W')
    }
    displayDebugLog("Instantaneous Power: ${pwr} W")
	return event
}

private def getkWh(float enrgy)
{
	def event = []
    enrgy = enrgy ? enrgy : 0.0
    state.energy = state.energy ? state.energy : 0.0
    if ( abs( enrgy - (float)state.energy ) > 1e-4 )
    {	
    	state.energy = (float)enrgy
        if ( state.energy < 1.0 )
			event << createEvent(name: 'energy', value: (enrgy * 1000), unit: 'Wh')
        else
        	event << createEvent(name: 'energy', value: enrgy, unit: 'kWh')
    }
    displayDebugLog("Accumulated Energy: ${enrgy} kWh")
	return event
}

// Convert raw 4 digit integer voltage value into percentage based on minVolts/maxVolts range
private def getBattery(rawValue) 
{
    state.oldValue = state.oldValue ? state.oldValue : 0
    def event = []
    if ( rawValue != state.oldValue || state.Nhours == 5 )
    {
        state.Nhours = 0
        state.oldValue = rawValue
        def rawVolts = rawValue / 1000
        def minVolts = voltsmin ? voltsmin : 2.5
        def maxVolts = voltsmax ? voltsmax : 3.0
        def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.min(100, Math.round(pct * 100))
        def descText = "Battery at ${roundedPct}% (${rawVolts} Volts)"
        displayInfoLog(": $descText")
        event << createEvent(name: 'battery', value: roundedPct, unit: '%', descriptionText: "${descText}", isStateChange:true, displayed:true)
        event << createEvent(name: 'voltage', value: rawVolts, unit: "V (${roundedPct}%)", displayed: true, isStateChange: true)
        displayDebugLog("Battery: ${event}")
    }
    else
    	state.Nhours = state.Nhours + 1
	return event
}

private String decodeHexString(String hexString) 
{
    String charString = ''
    try
    {
    	if (hexString.length() % 2 == 1) 
    	{
        	throw new IllegalArgumentException(
          	"Invalid hexadecimal String supplied.");
    	}
        
    	for (int i = 0; i < hexString.length(); i += 2) 
        	charString += String.valueOf((char)Integer.parseInt(hexString[i..i+1],16))
    }
    catch(Exception e)
    {
    	displayDebugLog( "${e}")
    }

    return charString;
}

private def abs(x) { return ( x > 0 ? x : -x ) } 

private def parseReportAttributeMessage(String description) 
{
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
     }
	 def events = []
    
    switch (Integer.parseInt(descMap.cluster, 16)) 
    {
    	case 0x0000:
        	displayDebugLog( "Basic Cluster: $descMap" )
            switch (Integer.parseInt(descMap.attrId, 16) )
            {
            	case 0x0005:
                	displayDebugLog("Device Type: ${decodeHexString(descMap.value)}")
                    break
                case 0x0007:  
                	if ( descMap.value != "03" )
            			state.batteryPresent = false
                	break
                case 0xFF22:
                	state.hasFF22 = true
                case 0xFF23:
                case 0xFF24:
                	displayInfoLog("Decoupled Mode - attrId: ${descMap.attrId} -  ${descMap.value}")
            }
            break
    	case 0x0001: //battery
        	if ( descMap.value == "0000" )
            	state.batteryPresent = false
        	else if (descMap.attrId == "0020")
				events = events + getBatteryResult(convertHexToInt(descMap.value / 2))
            break
 		case 0x0002: // temperature
        	if ( descMap.attrId == '0000' ) 
            	events = events + setTemp( convertHexToInt(descMap.value) )
            break
 		case 0x0006:  //button press
            state.flag = ( descMap.value[1] == 'c' ) ? 'double' : 'hard' 
        	events = events + parseSwitchOnOff(descMap) 
            break
        case 0x000C: //analog input
        	if ( descMap.attrId == "0055" )
            {
            	int x = Integer.parseInt(descMap.value, 16)
            	float y = Float.intBitsToFloat(x)
                events = events + getWatts(y)
            }
        	break
        case 0x0012: //Multistate Input
        	Map vals = ['0000' : 'held', '0001': 'hard', '0002': 'double']
            state.flag = vals[descMap.value]
            events = events + parseSwitchOnOff(descMap)
            break
        case 0xFCC0: //Opple
        	state.hasFCC0 = true
        	switch ( Integer.parseInt(descMap.attrId, 16) )
            {
                case 0x00EE:
                	displayDebugLog("0x00EE meaning: ${Integer.parseInt(descMap.value, 16)}")
                    break
                case 0x00F6:
                	displayDebugLog("0x00F6 meaning: ${Integer.parseInt(descMap.value[2..3]+descMap.value[0..1], 16)}")
                    break
                case 0x00F7:
            		Map myMap = parseFCC0F7(descMap.value)
            		displayDebugLog("FCC07 Map: ${myMap}")
                	events = events + setTemp(myMap.get(3))
            }
        	break
 		//case "0008":
        //	if ( descMap.attrId == "0000")
    	//		event = createEvent(name: "switch", value: "off")
        //    break
 		default:
        	displayDebugLog( "unknown cluster in $descMap" )
    }
	return events
}

private Map parseFCC0F7(String mystring)
{
	Map myMap
    try
    {
        List mybytes = [0x01, 0xff, 0x42]
    	List mybytes1 = mystring.decodeHex()
        int i = mybytes1.size()
        while ( i )
        {
        	i -= 1
            mybytes += [(Byte)mybytes1[i] & 0xff]
        }
        myMap = dataMap(mybytes)
    }
    catch(Exception e) 
    {
		displayDebugLog( "${e}")
    }
    return myMap
}

def parseSwitchOnOff(Map descMap)
{
    //parse messages on read attr cluster 0x0006 or 0x0012
    def events = []
    int endp = Integer.parseInt(descMap.endpoint, 16)
    int endpcode = state.endpoints.indexOf(endp)
    state.lastEndpcode = endpcode
	if ( endpcode > 2 || state.flag == 'double' )
    {
    	//def action = state.flag == 'double' ? 'double' : ( state.flag == 'held' ? 'held' : 'pushed' )
        def action = state.flag == 'hard' ? 'pushed' : state.flag
    	switch ( endpcode )
        {
        	case 0:
            case 3:
            case 7:
            	events << createEvent( name: 'button', value: action, data:[buttonNumber: 1], isStateChange: true)
                break
            case 1..2:
            case 4..5:
            case 8..9:
            	int idx = endpcode - ( endpcode < 3 ? 1 : ( endpcode < 6 ? 4 : 8 ) )
                Map button = [name: 'button', value: action, data:[buttonNumber: 1], isStateChange: true]
            	getChild(idx).sendEvent( button)
                displayDebugLog("Child ${idx+1}   ${button}")
                break
            case 6:
            	//events << createEvent(name: 'button', value: 'pushed', data:[buttonNumber: 2], isStateChange: true )
                events << createEvent(name: 'button', value: action, data:[buttonNumber: 2], isStateChange: true )
                break
            default:
            	displayDebugLog("Invalid read attr code")
        }
     }
     else if ( !state.unwiredSwitches[endpcode] )
     {
     	def onoff = descMap.value[-1] == "1" ? "on" : "off"
     	switch ( endpcode )
        {
        	case 0:
            	events << createEvent( name: 'switch', value: onoff, isStateChange: true)
                break
            case 1..2:
            	Map sw = [name: 'switch', value: onoff, isStateChange: true]
            	getChild(endpcode-1).sendEvent( sw)
                displayDebugLog("{Child ${endpcode}   ${sw}")
                break
            default:
            	displayDebugLog("invalid rad attr code")
        }
    } 
    else
    	displayDebugLog("read attr endpoint ${endp} ignored.")   	
	return events
}

private def parseCustomMessage(String description) 
{
	displayDebugLog( "Parsing Custom Message: $description" )
    if (description == 'on/off: 0')
    {
    	if ( state.oldOnOff )
        	off()
        else
        {
        	state.holdDone = false
    		runIn( 1, doHoldButton )  // delay 1 second to make sure 'double' has been identified before implementing 'held'
        }
    }
    else if (description == 'on/off: 1')
    {
    	if ( state.oldOnOff )
        	on()
    	else
    		doHoldButton()
   	}
}

def doHoldButton()
{
	displayDebugLog("doHoldButton   Hold Done: ${state.holdDone}   Last EndPcode: ${state.lastEndpcode}")
    if ( !state.holdDone )  // avoid this function being called twice.
   	{
        state.holdDone = true
        if ( state.flag != 'double' )
        {
            Map button = [name: 'button', value: 'held', data:[buttonNumber: 1], isStateChange: true]
            switch( state.lastEndpcode )
            {
                case 0:
                case 3:
                    //events << createEvent( button )
                    sendEvent(button)
                    displayDebugLog(button)
                break
                case 1..2:
                case 4..5:
                	int ch = state.lastEndpcode % 3 - 1
                    getChild(ch).sendEvent( button )
                    displayDebugLog("Child ${state.lastEndpcode}  ${button}")
                break
                default:
                    displayDebugLog("Unexpected custom message")
            }
        }
        state.lastEndpcode = null
    }
}

private def getChild(int i)
{
    def children = getChildDevices()
    def child
    if ( children.size() == 1 )
        child = children[0]
    else
    {
    	def idx = state.childDevices[i]	
    	for (child1 in children)
    	{	
        	if ( child1.deviceNetworkId == idx )
			{
            	child = child1
				break
            }
        }
    }
    return child
}

def childOn(String dni) 
{
    int idx = state.childDevices.indexOf(dni) + 1
    int endp = state.endpoints[idx]
    def cmd = zigbee.command(0x0006, 0x01, "", [destEndpoint: endp] )
    displayDebugLog("ChildOn ${dni}  ${idx}  ${cmd}" )
    cmd 
}

def childOff(String dni) 
{
 	int idx = state.childDevices.indexOf(dni) + 1
    int endp = state.endpoints[idx]
    def cmd = zigbee.command(0x0006, 0x00, "", [destEndpoint: endp] )
    displayDebugLog( "ChildOff ${dni}  ${idx}  ${cmd}")
    cmd 
}

def childToggle(String dni) 
{
 	int idx = state.childDevices.indexOf(dni) + 1
    int endp = state.endpoints[idx]
    def cmd = zigbee.command(0x0006, 0x02, "", [destEndpoint: endp] )
    displayDebugLog( "ChildToggle ${dni}  ${idx}  ${cmd}")
    cmd 
}

private def childFromNetworkId(String dni)
{
	def child
	def children = getChildDevices()
    if (children.size()  == 1)
    	child = children[0]
    else
    {
    	for ( child1 in children )
    	{
        	if ( child1.deviceNetworkId == dni )
            {
            	child = child1
                break
            }
        }
	}
	return child
}

def childRefresh(String dni, Map sets) 
{
    log.info "${device.displayName} Child Refresh: ${dni} ${state.childDevices} ${sets}"
    def child = childFromNetworkId(dni)
    def idx
    try
    {
    	if ( state.childDevices == null )
        	buildChildDevices()
        idx = state.childDevices.indexOf(dni) + 1
        if ( state.unwiredSwitches == null )
            state.unwiredSwitches = []
        state.unwiredSwitches[idx] = ( sets.unwired == null ? false : sets.unwired )
        if ( state.decoupled == null )
            state.decoupled = []
        state.decoupled[idx] = ( sets.decoupled == null ? false : sets.decoupled )
    }
    catch(Exception e) 
    {
		log.debug "${device.displayName} ${e}"
    }
    displayDebugLog("Child Refresh: ${idx} ${child.deviceNetworkId}   ${state.unwiredSwitches}   ${state.decoupled}")
	def cmds = []
    if ( !state.refreshOn )
    	cmds += refresh()
    return cmds
}

def on() 
{
    displayDebugLog("Switch 1 pressed on")
    if ( !state.decoupled[0] )
    {
    	Map button = [name: 'button', value: 'pushed', data:[buttonNumber: 1], isStateChange: true]
    	sendEvent(button)
    	displayDebugLog(button)
    }
    def cmd = []
    if ( ! state.unwiredSwitches[0] )
    	cmd = zigbee.command(0x0006, 0x01, "", [destEndpoint: state.endpoints[0]] )
    displayDebugLog( cmd )
    return cmd 
}

def off() 
{
    displayDebugLog("Switch 1 pressed off")
    if ( !state.decoupled[0] )
    {
    	Map button = [name: 'button', value: 'pushed', data:[buttonNumber: 1], isStateChange: true]
    	sendEvent( button )
    	displayDebugLog(button)
     }
    def cmd = []
    if ( !state.unwiredSwitches[0] )
    	cmd = zigbee.command(0x0006, 0x00, "", [destEndpoint: state.endpoints[0]] )
    
    displayDebugLog(cmd)
    cmd
}

def push()
{	
	displayDebugLog("Momentary pressed")
	Map button = [name: 'button', value: 'pushed', data:[buttonNumber: 1], isStateChange: true]
    sendEvent( button ) 
    displayDebugLog(button)
    def cmd = state.decoupled[0] ? [] : zigbee.command(0x0006, 0x02, "", [destEndpoint: state.endpoints[0]] )
    displayDebugLog( cmd )
    cmd
}

private def clearState()
{
	displayDebugLog(state)
    def unwiredSwitches = state.unwiredSwitches
    def decoupled = state.decoupled
    def tempNow = state.tempNow
    def tempNow2 = state.tempNow2
    state = null
    state.unwiredSwitches = unwiredSwitches
	state.decoupled = decoupled
	state.tempNow = tempNow
    state.tempNow2 = tempNow2
    displayDebugLog(state)
}

def refresh() 
{
	//settings.infoLogging = true
    //settings.debugLogging = true
    displayInfoLog( "refreshing" )
    clearState()
    def dat = new Date()
    state.lastTempTime = dat.time
   	displayDebugLog(settings)
    
    //state.unwired = parseUnwiredSwitch()
    state.tempNow = state.tempNow == null ? 0 : state.tempNow
    state.tempNow2 = state.tempNow2 == null ? 0 : state.tempNow2
    state.tempOffset = tempOffset == null ? 0 : tempOffset
    
    //state.final = 'off'
    
    if ( state.unwiredSwitches == null )
        state.unwiredSwitches = []
    if ( settings.unwired == null )
    	settings.unwired = false
    state.unwiredSwitches[0] = settings.unwired
    displayDebugLog("Unwired: ${state.unwiredSwitches}")
    
    if ( state.decoupled == null )
    	state.decoupled = []
    if ( settings.decoupled == null )
    	settings.decoupled = false
    state.decoupled[0] = settings.decoupled
    displayDebugLog("Decoupled: ${state.decoupled}")
    
    getNumButtons()
    if ( state.numSwitches > 1 )
    {
    	def childDevices = getChildDevices()
		displayDebugLog("Children: ${childDevices}: ${childDevices.size()}")
        /*try 
        {
        	displayDebugLog("Deleting Children")
            for ( child in childDevices )
            	deleteChildDevice("${child.deviceNetworkId}")
    	} 
        catch(Exception e) 
        { 
        	displayDebugLog("${e}") 
        }
    	childDevices = getChildDevices()
        */
   		if (childDevices.size() == 0) 
    	{
			displayInfoLog( "Creating Children" )
            //state.childDevices = [device.name]
			try 
    		{
    			if ( state.numSwitches > 1)
    			{
                	state.childDevices = []
                	for ( int i = 1; i < state.numSwitches; i++ )
                    {
                    	def networkId = "${device.deviceNetworkId}-${i}"
    					addChildDevice( "Aqara Wall Switch Child", networkId , null,[label: "${device.displayName}-(${i})"])  
                        state.childDevices[i-1] = networkId
                    }
                }
			} 
        	catch(Exception e) 
        	{
				displayDebugLog( "${e}")
        	}
			displayInfoLog("Child created")
		}    
        else
        	buildChildDevices()
        displayDebugLog("Children(b): ${state.childDevices}")
        
        displayDebugLog("Unwired Switches: ${state.unwiredSwitches}")
        
        childDevices = getChildDevices()
        state.refreshOn = true
        for (child in childDevices)
        {	
            child.sendEvent(name: 'checkInterval', value: 3000)
            displayDebugLog("${child}  ${child.deviceNetworkId}")
            //child.refresh()
        }
        state.refreshOn = false
    }    
    displayDebugLog("Devices: ${state.childDevices}")
    displayDebugLog("Unwired Switches: ${state.unwiredSwitches}")
    
    sendEvent(name: 'supportedButtonValues', value: ['pushed', 'held', 'double'], isStateChange: true)
    //sendEvent(name: 'supportedButtonValues', value: ["down","down_hold","down_2x"].encodeAsJSON(), isStateChange: true)
    sendEvent( name: 'checkInterval', value: 3000, data: [ protocol: 'zigbee', hubHardwareId: device.hub.hardwareID ] )
    
    //state.unwiredSwitches = [unwired]
    def cmds = []
    if ( state.hasFCC0 )
    	cmds += setOPPLE()
    if ( state.endpoints[0] != null )
    	cmds += zigbee.readAttribute(0x0001, 0) + 
    			zigbee.readAttribute(0x0002, 0) +
                setDecoupled() + 
                showDecoupled()
   	cmds += 
            zigbee.readAttribute(0xFCC0, 0x00F6, [mfgCode: "0x115F"]) +
            zigbee.readAttribute(0xFCC0, 0x00F7, [mfgCode: "0x115F"]) +
            zigbee.readAttribute(0x0000, 0xFF22, [mfgCode: "0x115F"])
     
	displayDebugLog("State: ${state}")
    displayDebugLog( cmds )
     //updated()
     state.flag = null
     cmds
}

private def buildChildDevices()
{	
    state.childDevices = []
    def childDevices = getChildDevices()
    if ( state.numSwitches == 2 )
    state.childDevices[0] = childDevices[0].deviceNetworkId
    else
    {
        if ( childDevices[0].deviceNetworkId[-1].toInteger() < childDevices[1].deviceNetworkId[-1].toInteger() )
        {
            state.childDevices[0] = childDevices[0].deviceNetworkId
            state.childDevices[1] = childDevices[1].deviceNetworkId
        }
        else
        {
            state.childDevices[0] = childDevices[1].deviceNetworkId
            state.childDevices[1] = childDevices[0].deviceNetworkId
        }
    }
}

private def setDecoupled()
{
	displayDebugLog("Decoupled: ${state.decoupled}   ${decoupled}" )
    def cmds = []
    if ( state.hasFCC0 ) //if ( false )
    {
        for ( int i = 0; i < state.decoupled.size(); i++ )
    		cmds += zigbee.writeAttribute(0xFCC0, 0x0200, DataType.UINT8, 
            								state.decoupled[i] ? 0x00 : 0x01, 
                                            [destEndpoint: state.endpoints[i], mfgCode: "0x115F"] )
    }
    else
    {	
    	def codea = [0xFF22, 0xFF23, 0xFF24]
        def codeb = [0x12, 0x22, 0x32] 
    	for ( byte i = 0; i < state.decoupled.size(); i++ )
    		cmds += zigbee.writeAttribute(0x0000, codea[i], DataType.UINT8, state.decoupled[i] ? 0xFE : codeb[i], 
            							[destEndpoint: 0x01, mfgCode: "0x115F"])
    }
    return cmds
}

private def showDecoupled()
{
	def cmds = []
    if ( state.hasFCC0 )//if ( false )
    {
    	cmds += zigbee.readAttribute(0xFCC0, 0x0009, [mfgCode: "0x115F"]) 
        for ( int i = 0; i < state.decoupled.size(); i++ )
        	cmds += zigbee.readAttribute(0xFCC0, 0x0200, 
        							[sourceEndpoint: state.endpoints[i], mfgCode: "0x115F"])
    }
    else
    {
    	def codea = [0xFF22, 0xFF23, 0xFF24]
    	for ( byte i = 0; i < state.decoupled.size(); i++ )
    		cmds += zigbee.readAttribute(0x0000, codea[i], [sourceEndpoint: 0x01, mfgCode: "0x115F"])
    }
    return cmds
}

private def setOPPLE()
{
	displayDebugLog("Setting OPPLE Mode")
    def cmds = 	[]
    cmds += zigbee.readAttribute(0x0000, 0x0001) +
        		zigbee.readAttribute(0x0000, 0x0005) + 
        		zigbee.writeAttribute(0xFCC0, 0x0009, DataType.UINT8, 0x01, [mfgCode: "0x115F"]) +
                zigbee.writeAttribute(0xFCC0, 0x00F6, DataType.UINT16, 1000,  [mfgCode: "0x115F"]) 
    return cmds
}

def installed()
{
	displayDebugLog('installed')
    settings.infoLogging = true
    response(refresh())
}

def configure()
{
	displayDebugLog('configure')
    settings.infoLogging = true
	response(refresh())
}

def updated()
{
    displayDebugLog('updated')
    if ( getDataValue("onOff") != null )
    {
    	updateDataValue("onOff", "catchall")
    	response(configure())
    }
    else
    	response(refresh())
}

def ping()
{
	displayDebugLog("Pinged")
    return zigbee.readAttribute(0x0002, 0)
}

def poll()
{
	displayDebugLog("Polled")
    return zigbee.readAttribute(0x0002, 0)
}

private getNumButtons()
{
    String model = device.getDataValue("model")
    state.oldOnOff = false
    state.hasFCC0 = false 
    state.hasFF22 = false
    switch ( model ) 
    {
    	case "lumi.ctrl_neutral1": //QBKG04LM
        case "lumi.switch.b1lacn02": //QBKG21LM
        	state.numSwitches = 1
     		state.numButtons = 1
            state.endpoints = [0x02,null,null,0x04,null,null,null]
            break
        case "lumi.ctrl_ln1.aq1": //QBKG11LM
        	state.numSwitches = 1
     		state.numButtons = 1
            state.endpoints = [0x01,null,null,0x04,null,null,null]
			break
		case "lumi.switch.b1nacn02": //QBKG23LM
            state.numSwitches = 1
     		state.numButtons = 1
            state.endpoints = [0x01,null,null,0x05,null,null,null]
            break
        case "lumi.ctrl_neutral2": //QBKG03LM
        	state.numSwitches = 2
        	state.numButtons = 2
            state.endpoints = [0x02,0x03,0xF3,0x04,0x05,0xF5,0x06]
            break
        case "lumi.switch.b2lacn02": //QBKG22LM 
        	state.numSwitches = 2
        	state.numButtons = 2
            state.endpoints = [0x02,0x03,0xF3,0x2A,0x2B,0xF5,0x06]
            break
        case "lumi.ctrl_ln2.aq1": //QBKG12LM      
        case "lumi.switch.b2nacn02": //QBKG24LM
           	state.numSwitches = 2
        	state.numButtons = 2
            state.endpoints = [0x01,0x02,0xF3,0x05,0x06,0xF5,0xF6]
            break
        case "lumi.switch.l3acn3": //QBKG25LM
        case "lumi.switch.n3acn3": //QBKG26LM
            state.numSwitches = 3
            state.numButtons = 4
            state.endpoints = [0x01,0x02,0x03,0x29,0x2A,0x2B,0xF6, 0x33,0x34,0x35]
            state.hasFCC0 = true
            break
        case "lumi.remote.b186acn01": //WXKG03LM
        case "lumi.remote.b186acn02": //WXKG06LM
       		state.numSwitches = 1
     		state.numButtons = 1
            state.endpoints = [null,null,null,0x01,null,null,null]
            break
        case "lumi.remote.b286acn01": //WXKG02LM (2018)
        case "lumi.remote.b286acn02": //WXKG07LM (2020) 
        	state.numSwitches = 2
        	state.numButtons = 2
            state.endpoints = [null,null,null,0x01,0x02,null,0x03]
            break
        case "lumi.switch.b1laus01": //Lumi WS-USC01
        case "lumi.switch.l1aeu1": //Aqara Switch EU-01
        	state.numSwitches = 1
            state.numButtons = 1
            state.endpoints = null
			state.oldOnOff = true
            state.hasFCC0 = true
			break
        case "lumi.switch.b2laus01": //Lumi WS-USC02
        case "lumi.switch.l2aeu1": //Aqara Switch EU-02
        case "lumi.switch.b2lc04": //QBKG39LM
        	state.numSwitches = 2
            state.numButtons = 2
            //state.endpoints = [0x01,0x02,0xF3,0x05,0x06,0xF5,0xF6]
            state.endpoints = [0x01,0x02,0xF3,0x29,0x2a,0xF5,0xF6, 0x33,0x34]
			state.hasFCC0 = true
			break
        case "lumi.switch.b1lc04": //QBKG38LM
        case "lumi.switch.b1nc01": //QBKG40LM
        case "lumi.switch.b2nc01": //QBKG41LM
        	state.numSwitches = 1
            state.numButtons = 1
            state.endpoints = [0x01,0xF02,0xF3,0x29,0xF4,0xF5,0xF6, 0x33,0xF7]
			state.hasFCC0 = true
			break
        case "lumi.remote.b486opcn01":
        	state.numSwitches = 2
        	state.numButtons = 2
            state.endpoints = [null,null,null,0x01,0x02,0x03,null]  
            state.hasFCC0 = true
            break
        default:
        	displayDebugLog("Unknown device model: " + model)
            state.numSwitches = 2
        	state.numButtons = 2
            state.endpoints = [null,null,null,0x01,0x02,null,null]  
    }
    displayDebugLog("endpoints: ${state.endpoints}")
    sendEvent(name: 'numberOfButtons', value: state.numButtons, displayed: false )
    displayDebugLog( "Setting Number of Buttons to ${state.numButtons}" )
}

private Integer convertHexToInt(hex) 
{
	int result = Integer.parseInt(hex,16)
    return result
}

private int max(int a, int b)
{
	return (a > b) ? a : b
}

private Map dataMap(data)
{
	// convert the catchall data from check-in to a map.
	Map resultMap = [:]
	int maxit = data.size()
    int it = max(data.indexOf((short)0xff), data.indexOf(255)) + 3
    if ( data.get(it-4) == 0x01 )
        while ( it < maxit )
        {
            int lbl = 0x00000000 | data.get(it)
            byte type = data.get(it+1)
            switch ( type)
            {
                case DataType.BOOLEAN: 
                    resultMap.put(lbl, (boolean)data.get(it+2))
                    it = it + 3
                    break
                case DataType.UINT8:
                    resultMap.put(lbl, (short)(0x0000 | data.get(it+2)))
                    it = it + 3
                    break
                case DataType.UINT16:
        			int x = (0x00000000 | (data.get(it+3)<<8) | data.get(it+2))
                    resultMap.put(lbl, lbl == 10 ? Integer.toHexString(x) : x )
                    it = it + 4
                    break
                case DataType.UINT32:
                    long x = 0x0000000000000000
                    for ( int i = 0; i < 4; i++ )
                        x |= data.get(it+i+2) << 8*i
                    resultMap.put(lbl, x )
                    it = it + 6
                    break
                  case DataType.UINT40:
                    long x = 0x000000000000000
                    for ( int i = 0; i < 5; i++ )
                        x |= data.get(it+i+2) << 8*i
                    resultMap.put(lbl, x )
                    it = it + 7
                    break  
                case DataType.UINT64:
                    long x = 0x0000000000000000
                    for ( int i = 0; i < 8; i++ )
                        x |= data.get(it+i+2) << 8*i
                    resultMap.put(lbl, x )
                    it = it + 10
                    break 
                case DataType.INT8:
                    resultMap.put(lbl, (short)(data.get(it+2)))
                    it = it + 3
                    break
                 case DataType.INT16:
                    resultMap.put(lbl, (int)((data.get(it+3)<<8) | data.get(it+2)))
                    it = it + 4
                    break
                case DataType.FLOAT4:
                    int x = 0x00000000 
                    for ( int i = 0; i < 4; i++ ) 
                        x |= data.get(it+i+2) << 8*i
                    float y = Float.intBitsToFloat(x) 
                    resultMap.put(lbl,y)
                    it = it + 6
                    break
                default: displayDebugLog( "unrecognised type in dataMap: " + zigbee.convertToHexString(type) )
                    return resultMap
            }
        }
    else
    	displayDebugLog("catchall data unrecognised.")
    return resultMap
}

private def displayDebugLog(message) 
{
	if (debugLogging)
		log.debug "${device.displayName} ${message}"
}

private def displayInfoLog(message) 
{
	//if (infoLogging || state.prefsSetCount < 3)
    if (infoLogging)
		log.info "${device.displayName} ${message}"
}