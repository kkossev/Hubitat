package hubitat.matter
class DataType {
    static final int INT8=0x00, INT16=0x01, INT32=0x02, INT64=0x03
    static final int UINT8=0x04, UINT16=0x05, UINT32=0x06, UINT64=0x07
    static final int BOOLEAN_FALSE=0x08, BOOLEAN_TRUE=0x09
    static final int FLOAT4=0x0A, FLOAT8=0x0B
    static final int UTF81=0x0C, UTF82=0x0D, UTF84=0x0E, UTF88=0x0F
    static final int STRING_OCTET1=0x10, STRING_OCTET2=0x11, STRING_OCTET4=0x12, STRING_OCTET8=0x13
    static final int NULL=0x14, STRUCTURE=0x15, ARRAY=0x16, LIST=0x17
}
