boolean aborted = false
// Simulation of the Matter Advanced Device collector state machine.
// The tick body is copied verbatim from the driver; everything around it is mocked.
import groovy.transform.Field

@Field static final Integer INFO_STATE_NEXT = 0, INFO_STATE_ATTR_LIST_WAIT = 2,
                            INFO_STATE_VALUES_WAIT = 4, INFO_STATE_END = 99
@Field static final Integer INFO_COLLECT_MAX_TICKS = 34, INFO_SETTLE_QUIET_TICKS = 2, INFO_MAX_CONSECUTIVE_MISSES = 5
@Field static final Integer READ_CHUNK_DELAY_MS = 500, READ_CHUNK_SIZE = 20

Map state = [:]
long now = 0L
List log = []
Map deviceAttrLists = [:]      // "ep/cluster" -> list of attribute ids the fake device has
List<String> visited = []

String hex4(n) { String.format('%04X', n as Integer) }
def sendInfoEvent(info, descriptionText = null) { }     // the _status_ banner - no effect on the state machine
Long safeToLong(o, d) { o == null ? d : (o as Long) }

// mock: returns after how many ms the last chunk goes out
int sendReads(Map state, int ep, int cluster, List attrs, long now) {
    int chunks = (int) Math.ceil(attrs.size() / (double) READ_CHUNK_SIZE)
    long computed = now + ((chunks - 1) * READ_CHUNK_DELAY_MS) + READ_CHUNK_DELAY_MS
    state.collect['notBefore'] = Math.max(safeToLong(state.collect['notBefore'], 0L), computed)
    return chunks
}

def runCollect = { List queue, Map answers, String label, boolean bufferFull = false ->
    // bufferFull models the dump sitting at MAX_INFO_BUFFER_LINES: the device still answers, so
    // c.replies climbs, but c.lines is frozen because nothing more can be remembered (B7)
    state.collect = [queue: queue, idx: 0, phase: INFO_STATE_NEXT, ticks: 0, quiet: 0,
                     label: label, gotAttrListFor: null, lines: 0, replies: 0, repliesAtEntry: 0,
                     misses: 0, notBefore: 0L]
    now = 0L
    visited = []
    int guard = 0
    aborted = false
    while (state.collect != null && guard++ < 5000) {
        Map c = state.collect
        c.ticks = (c.ticks ?: 0) + 1
        switch (c.phase as Integer) {
            case INFO_STATE_NEXT:
                if ((c.idx as Integer) >= (c.queue as List).size()) { c.phase = INFO_STATE_END ; break }
                Map entry = (c.queue as List)[c.idx as Integer] as Map
                sendInfoEvent("${c.label} (${(c.idx as Integer) + 1}/${(c.queue as List).size()}) - please wait")
                c.gotAttrListFor = null
                c.quiet = 0
                c.notBefore = 0L
                if (entry.attrs != null) {
                    visited << "${entry.ep}/${hex4(entry.cluster)}(fixed)"
                    sendReads(state, entry.ep, entry.cluster, entry.attrs, now)
                    c.replies = (c.replies as Integer) + 1  // the fake device answers fixed reads
                    if (!bufferFull) { c.lines = (c.lines as Integer) + 1 }
                    c.phase = INFO_STATE_VALUES_WAIT
                } else {
                    visited << "${entry.ep}/${hex4(entry.cluster)}(list)"
                    // the fake device answers the AttributeList read one tick later, if it knows it
                    if (answers.containsKey("${entry.ep}/${hex4(entry.cluster)}".toString())) {
                        c.pendingList = "${entry.ep}/${hex4(entry.cluster)}".toString()
                    }
                    c.phase = INFO_STATE_ATTR_LIST_WAIT
                    c.ticks = 0
                }
                break
            case INFO_STATE_ATTR_LIST_WAIT:
                Map waitingFor = (c.queue as List)[c.idx as Integer] as Map
                if (c.pendingList != null) { c.gotAttrListFor = hex4(waitingFor.cluster as Integer) ; c.pendingList = null }
                if (c.gotAttrListFor == hex4(waitingFor.cluster as Integer)) {
                    List attrs = (answers["${waitingFor.ep}/${hex4(waitingFor.cluster)}".toString()] ?: []).findAll { it != 0xFFFB }
                    if (attrs.isEmpty()) { c.idx = (c.idx as Integer) + 1 ; c.phase = INFO_STATE_NEXT }
                    else { sendReads(state, waitingFor.ep, waitingFor.cluster, attrs, now)
                           c.replies = (c.replies as Integer) + 1
                           if (!bufferFull) { c.lines = (c.lines as Integer) + 1 }
                           c.phase = INFO_STATE_VALUES_WAIT ; c.quiet = 0 ; c.ticks = 0 }
                } else if ((c.ticks as Integer) > INFO_COLLECT_MAX_TICKS) {
                    c.misses = (c.misses ?: 0) + 1
                    c.idx = (c.idx as Integer) + 1 ; c.phase = INFO_STATE_NEXT
                }
                break
            case INFO_STATE_VALUES_WAIT:
                c.quiet = (c.quiet ?: 0) + 1
                boolean allChunksSent = now >= safeToLong(c.notBefore, 0L)
                boolean settled = allChunksSent && (c.quiet as Integer) >= INFO_SETTLE_QUIET_TICKS
                if (settled || (c.ticks as Integer) > INFO_COLLECT_MAX_TICKS) {
                    c.misses = (c.replies as Integer) > (c.repliesAtEntry as Integer ?: 0) ? 0 : (c.misses ?: 0) + 1
                    c.repliesAtEntry = c.replies
                    c.idx = (c.idx as Integer) + 1 ; c.phase = INFO_STATE_NEXT ; c.ticks = 0 ; c.notBefore = 0L
                }
                break
            default: c.phase = INFO_STATE_END ; break
        }
        if ((c.misses ?: 0) >= INFO_MAX_CONSECUTIVE_MISSES) { c.phase = INFO_STATE_END ; aborted = true }
        if ((c.phase as Integer) == INFO_STATE_END) { state.collect = null ; break }
        state.collect = c
        now += 300
    }
    return [ticks: guard, seconds: now / 1000.0, visited: visited, aborted: aborted]
}

int fails = 0
def expect = { String label, boolean ok, String detail -> if (ok) println "ok   ${label} - ${detail}" else { println "FAIL ${label} - ${detail}"; fails++ } }

// 1. discovery stage 1: one fixed-attribute entry
def r = runCollect([[ep:0, cluster:0x001D, attrs:[0,1,2,3,4]]], [:], 'stage1')
expect('stage 1 terminates', r.visited.size() == 1, "visited ${r.visited}, ${r.seconds}s")

// 2. discovery stage 2: 4 endpoints + basic info + ota, all fixed
def q2 = (1..4).collect { [ep: it, cluster: 0x001D, attrs: [0,1,3,4]] } + [[ep:0, cluster:0x0028, attrs:[1,3,5,7,8,9,10,15,18,21]], [ep:0, cluster:0x002A, attrs:[0,1,2,3]]]
r = runCollect(q2, [:], 'stage2')
expect('stage 2 visits every entry', r.visited.size() == 6, "visited ${r.visited.size()} of 6, ${r.seconds}s")

// 3. getInfo: wildcard entries, device answers every AttributeList
Map answers = ['0/001D':[0,1,2,3,4,0xFFFB], '0/0028':(0..22).toList(), '1/0006':[0,0x4003,0xFFFB], '1/0008':(0..17).toList()]
def q3 = [[ep:0,cluster:0x001D],[ep:0,cluster:0x0028],[ep:1,cluster:0x0006],[ep:1,cluster:0x0008]]
r = runCollect(q3, answers, 'getInfo')
expect('getInfo visits every cluster', r.visited.size() == 4, "visited ${r.visited}, ${r.seconds}s")

// 4. a cluster the device never answers - must time out and move on, not hang
def q4 = [[ep:0,cluster:0x001D],[ep:9,cluster:0x1234],[ep:1,cluster:0x0006]]
r = runCollect(q4, answers, 'silent cluster')
expect('silent cluster is skipped', r.visited.size() == 3, "visited ${r.visited}, ${r.seconds}s")

// 5. a big cluster needing 4 chunks - must not advance before the last chunk is sent
def bigAnswers = ['0/0035': (0..62).toList()]
r = runCollect([[ep:0,cluster:0x0035]], bigAnswers, 'big cluster')
expect('63-attribute cluster completes', r.visited.size() == 1 && r.seconds >= 1.5, "${r.seconds}s elapsed (needs >= 1.5s for 4 chunks)")

// 6. worst case: 60 clusters, none answering - must still terminate
def q6 = (1..60).collect { [ep: it % 5, cluster: 0x9000 + it] }
r = runCollect(q6, [:], 'all silent')
expect('60 silent clusters abort early', r.aborted && r.visited.size() <= 8 && r.seconds < 120, "aborted=${r.aborted} after ${r.visited.size()} clusters, ${r.seconds}s")

// 7. B7 invariant: miss detection must depend on REPLIES, never on the printed line count. The
//    line cap that first exposed this is gone (the dump flushes per cluster now), but a cluster
//    whose replies produce no new lines - all duplicates, or AttributeList only - still must not
//    be counted as silent.
def q7 = (1..12).collect { [ep: it, cluster: 0x001D, attrs: [0, 1, 3, 4]] }
r = runCollect(q7, [:], 'no new lines', true)
expect('replies without lines do not abort', !r.aborted && r.visited.size() == 12,
       "aborted=${r.aborted}, visited ${r.visited.size()} of 12")

println ''
println (fails == 0 ? 'ALL PASS' : fails + ' FAILURE(S)')
