"""Regenerate the helper test harness from the CURRENT driver source.

The pure helper methods are pulled out of Matter_Advanced_Device.groovy every run, so the
assertions in helpers_assertions.groovy can never drift away from the code they test.

    python tests/extract_helpers.py        # writes tests/_generated_helpers_test.groovy
"""
import io, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DRIVER = os.path.join(HERE, '..', 'Matter_Advanced_Device.groovy')
OUT = os.path.join(HERE, '_generated_helpers_test.groovy')

METHODS = ['parseNumber', 'swapOctetsAny', 'stringToHex', 'encodeWriteValue', 'encodeCmdFieldValue',
           'toIntList', 'formatMatterHex', 'hex2', 'hex4', 'decodeSpecificationVersion',
           'safeHexToInt', 'safeToInt', 'safeToLong', 'safeToDouble', 'isTrueish', 'fieldOf',
           'tagValue', 'firstProviderNodeId', 'truncateForDump']
TABLES = ['TLV_INT_WIDTH', 'TLV_TYPE_BY_NAME', 'WRITE_DATA_TYPES']
CONSTS = ['MAX_DUMP_VALUE_LEN']   # @Field constants the extracted helpers read
SIG = r'^(?:String|Integer|Long|Double|Boolean|Object|List<Integer>) ([A-Za-z][A-Za-z0-9]*)\('

lines = io.open(DRIVER, encoding='utf-8').read().split('\n')
blocks, i = {}, 0
while i < len(lines):
    m = re.match(SIG, lines[i])
    if m and m.group(1) in METHODS and m.group(1) not in blocks:
        name = m.group(1)
        if lines[i].rstrip().endswith('}'):
            blocks[name] = lines[i]; i += 1; continue
        j = i
        while j < len(lines) and lines[j] != '}':
            j += 1
        blocks[name] = '\n'.join(lines[i:j + 1]); i = j + 1; continue
    i += 1

missing = [m for m in METHODS if m not in blocks]
if missing:
    sys.exit('could not find these methods in the driver: %s' % missing)

whole = '\n'.join(lines)
tables = []
for t in TABLES:
    m = re.search(r'@Field static final (Map<[^>]+>) ' + t + r' = \[.*?\n\]', whole, re.S)
    if not m:
        sys.exit('could not find the %s table in the driver' % t)
    tables.append('@Field static final ' + m.group(0).split('@Field static final ', 1)[1])

consts = []
for c in CONSTS:
    hit = [l for l in lines if l.startswith('@Field static final') and (' ' + c + ' ') in l]
    if not hit:
        sys.exit('could not find the %s constant in the driver' % c)
    consts.append(hit[0].split('//')[0].rstrip())

harness = ('import groovy.transform.Field\n'
           '@Field static final Map settings = [:]\n'
           'def logDebug(m) {}\n'
           + '\n'.join(consts) + '\n'
           + '\n'.join(tables) + '\n'
           + '\n'.join(blocks[m] for m in METHODS) + '\n'
           + io.open(os.path.join(HERE, 'helpers_assertions.groovy'), encoding='utf-8').read())
io.open(OUT, 'w', encoding='utf-8', newline='\n').write(harness)
print('extracted %d methods, %d tables and %d constant(s) -> %s' % (len(blocks), len(tables), len(consts), os.path.basename(OUT)))
