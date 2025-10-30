#include drozovyk.encapsulation1

library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Clock specific helpers",
    name: "clock1",
    namespace: "drozovyk"   
)

@Field static Map weekDays = [
    (0):'',      // is a legal value. Device treats is as unknown/unset day of the week
    (1):'Mon', 
    (2):'Tue', 
    (3):'Wed', 
    (4):'Thu', 
    (5):'Fri', 
    (6):'Sat', 
    (7):'Sun'
]

static Number getWeekDay(Calendar clock) {
    Integer dayOfWeek = clock.get(Calendar.DAY_OF_WEEK); 
    // calendar returns range [1:Sunday, .., 7:Saturday]
    // but z-wave device expects days according to the 'weekDays'
    dayOfWeek -= 1
    
    if(0 == dayOfWeek) {
        dayOfWeek = 7
    }
    
    return dayOfWeek;
}

private String getClockReportString() {
    return encapsulate(zwave.clockV1.clockGet())    
}

// A Z-Wave Plus node SHOULD issue a Clock Report Command via the Lifeline Association Group if they suspect to have inaccurate 
// time and/or weekdays (e.g. after battery removal). A controlling node SHOULD compare the received time and weekday with its current time 
// and set the time again at the supporting node if a deviation is observed (e.g. different weekday or more than a minute difference)

// So report may be initiated by device without request to sync clock. But it looks like pretty rare behavior.
void zwaveEvent(hubitat.zwave.commands.clockv1.ClockReport cmd, Short ep = 0) {    
    day = weekDays[cmd.weekday as Integer];
    
    def now = Calendar.instance
    def dayOfWeek = getWeekDay(now)
    
    if(cmd.weekday != dayOfWeek || cmd.hour != now.get(Calendar.HOUR_OF_DAY) || cmd.minute != now.get(Calendar.MINUTE)) {
        def newCmd = encapsulate(zwave.clockV1.clockSet(hour: now.get(Calendar.HOUR_OF_DAY), minute: now.get(Calendar.MINUTE), weekday: dayOfWeek))
        sendHubCommand(new hubitat.device.HubAction(newCmd, hubitat.device.Protocol.ZWAVE))
        logInfo("Updating device clock settings due to mismatch: was ${day}, ${cmd.hour}:${cmd.minute}; set to ${weekDays[dayOfWeek]}, ${now.get(Calendar.HOUR_OF_DAY)}:${now.get(Calendar.MINUTE)}")
    } else {
        logInfo("Device clock settings are correct: ${day}, ${cmd.hour}:${cmd.minute}")
    }
}