
int fails = 0
def check = { String label, Object got, Object want ->
    if (got != want) { println "FAIL ${label}: got ${got}  want ${want}"; fails++ }
    else { println "ok   ${label} = ${got}" }
}
check('parseNumber 0x0006', parseNumber('0x0006'), 6)
check('parseNumber 0006',   parseNumber('0006'),   6)
check('parseNumber 0010',   parseNumber('0010'),   16)
check('parseNumber 10',     parseNumber('10'),     10)
check('parseNumber 6',      parseNumber('6'),      6)
check('parseNumber FFFB',   parseNumber('FFFB'),   65531)
check('parseNumber 002A',   parseNumber('002A'),   42)
check('parseNumber OnOff',  parseNumber('OnOff'),  null)
check('parseNumber blank',  parseNumber(''),       null)
check('parseNumber 300',    parseNumber('300'),    300)
check('swap 1234',      swapOctetsAny('1234'), '3412')
check('swap 16 digits', swapOctetsAny('1122334455667788'), '8877665544332211')
check('swap 2 digits',  swapOctetsAny('0A'), '0A')
check('hex4 6',     hex4(6),   '0006')
check('hex2 26',    hex2(26),  '1A')
check('hex4 FFFB',  hex4(65531), 'FFFB')
check('write UINT16 10',  encodeWriteValue(0x05, '10'),   '000A')
check('write UINT8 2',    encodeWriteValue(0x04, '2'),    '02')
check('write UINT64 1',   encodeWriteValue(0x07, '1'),    '0000000000000001')
check('write BOOL true',  encodeWriteValue(0x09, ''),     '')
check('write NULL',       encodeWriteValue(0x14, ''),     '')
check('write UTF8 AB',    encodeWriteValue(0x0C, 'AB'),   '4142')
check('write bad number', encodeWriteValue(0x05, 'zz'),   null)
check('write INT16 -1',   encodeWriteValue(0x01, '-1'),   'FFFF')
check('cmdField UINT16 10',  encodeCmdFieldValue(0x05, '10'),  '0A00')
check('cmdField UINT8 10',   encodeCmdFieldValue(0x04, '10'),  '0A')
check('cmdField UINT16 300', encodeCmdFieldValue(0x05, '300'), '2C01')
check('toIntList string', toIntList('[0006, 0008, 001D]'), [6, 8, 29])
check('toIntList empty',  toIntList('[]'), [])
check('toIntList null',   toIntList(null), [])
check('toIntList list',   toIntList(['0006','FFFB']), [6, 65531])
check('specVersion', decodeSpecificationVersion('17039360'), '1.4.0.0')   // the platform sends decimal, not hex - GRILLPLATS log 2026-08-15
check('safeHexToInt 0x1A', safeHexToInt('0x1A'), 26)
check('safeHexToInt 64',   safeHexToInt('64'), 100)
check('safeToInt 64',      safeToInt('64'), 64)
check('isTrueish true',    isTrueish('true'), true)
check('isTrueish 01',      isTrueish('01'), true)
check('isTrueish 00',      isTrueish('00'), false)
// OTA event payloads exactly as the GRILLPLATS sent them, 2026-08-15 23:49
// DownloadError [3:null, 0:0, 1:0, 2:null] - fields 0 and 1 are PRESENT and zero
check('fieldOf zero field 0',  fieldOf([3:null, 0:0, 1:0, 2:null], 0), 0)
check('fieldOf zero field 1',  fieldOf([3:null, 0:0, 1:0, 2:null], 1), 0)
check('fieldOf null field',    fieldOf([3:null, 0:0, 1:0, 2:null], 2), null)
// StateTransition Idle->Querying reason Success, and Querying->Idle reason Failure
check('fieldOf newState',      fieldOf([3:null, 0:1, 1:2, 2:1], 1), 2)
check('fieldOf reason',        fieldOf([3:null, 0:2, 1:1, 2:2], 2), 2)
check('fieldOf reason unknown', fieldOf([0:1, 1:0, 2:0], 2), 0)
check('fieldOf string key',    fieldOf(['0':5], 0), 5)
check('fieldOf nested',        fieldOf([wrapper:[0:7]], 0), 7)
check('fieldOf absent',        fieldOf([1:9], 0), null)
check('fieldOf null data',     fieldOf(null, 0), null)
check('fieldOf list',          fieldOf([11, 22, 33], 1), 22)
// tagValue - the three list-of-struct shapes a real device returns. The [[tag:N, value:V]] shape is
// the one the DIRIGERA actually sent for DeviceTypeList/TagList on 2026-08-16.
check('tagValue tag pairs',    tagValue([[tag:1, value:8124], [tag:2, value:0]], 1), 8124)
check('tagValue tag pairs ep',  tagValue([[tag:1, value:8124], [tag:2, value:0]], 2), 0)
check('tagValue int keys',     tagValue([1: 8124, 2: 0], 1), 8124)
check('tagValue string keys',  tagValue(['1': 8124, '2': 0], 1), 8124)
check('tagValue single pair',  tagValue([tag:1, value:5377], 1), 5377)
check('tagValue absent tag',   tagValue([[tag:2, value:0]], 1), null)
check('tagValue null item',    tagValue(null, 1), null)
// B3: node ids are often tiny - chip-tool's own OTA example commissions the provider as node 1
check('providerNodeId node 1', firstProviderNodeId([[[tag:1, value:1], [tag:2, value:0]]]), 1L)
check('providerNodeId large',  firstProviderNodeId([[[tag:1, value:8467], [tag:2, value:0]]]), 8467L)
check('providerNodeId zero',   firstProviderNodeId([[[tag:1, value:0], [tag:2, value:0]]]), 0L)
check('providerNodeId empty',  firstProviderNodeId([]), null)
check('providerNodeId absent', firstProviderNodeId([[[tag:2, value:0]]]), null)
check('providerNodeId not a list', firstProviderNodeId('[]'), null)

// truncateForDump - the dump listing must not carry a 1300-character certificate
check('truncate short',    truncateForDump('OnOff = false'), 'OnOff = false')
check('truncate null',     truncateForDump(null), null)
check('truncate at limit', truncateForDump('x' * 200), 'x' * 200)
check('truncate one over', truncateForDump('x' * 201).startsWith('x' * 200 + '... (201 chars'), true)
check('truncate cert',     truncateForDump('A' * 1300).length() < 300, true)

println ''
println (fails == 0 ? 'ALL PASS' : fails + ' FAILURE(S)')
