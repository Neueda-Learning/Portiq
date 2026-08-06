package com.portiq.security.webauthn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal CBOR decoder covering only what WebAuthn attestation objects and COSE keys use:
 * unsigned/negative integers, byte strings, text strings, arrays, and maps.
 */
public class CborReader {

    private final byte[] data;
    private int pos;

    public CborReader(byte[] data, int offset) {
        this.data = data;
        this.pos = offset;
    }

    public int position() {
        return pos;
    }

    public Object readValue() {
        int initialByte = data[pos++] & 0xFF;
        int majorType = initialByte >> 5;
        int additionalInfo = initialByte & 0x1F;
        long length = readLength(additionalInfo);

        switch (majorType) {
            case 0:
                return length;
            case 1:
                return -1L - length;
            case 2: {
                byte[] bytes = new byte[(int) length];
                System.arraycopy(data, pos, bytes, 0, (int) length);
                pos += (int) length;
                return bytes;
            }
            case 3: {
                byte[] bytes = new byte[(int) length];
                System.arraycopy(data, pos, bytes, 0, (int) length);
                pos += (int) length;
                return new String(bytes, StandardCharsets.UTF_8);
            }
            case 4: {
                List<Object> list = new ArrayList<>();
                for (long i = 0; i < length; i++) {
                    list.add(readValue());
                }
                return list;
            }
            case 5: {
                Map<Object, Object> map = new LinkedHashMap<>();
                for (long i = 0; i < length; i++) {
                    Object key = readValue();
                    Object value = readValue();
                    map.put(key, value);
                }
                return map;
            }
            case 6:
                return readValue();
            case 7:
                if (additionalInfo == 20) return Boolean.FALSE;
                if (additionalInfo == 21) return Boolean.TRUE;
                if (additionalInfo == 22) return null;
                return length;
            default:
                throw new IllegalStateException("Unsupported CBOR major type: " + majorType);
        }
    }

    private long readLength(int additionalInfo) {
        if (additionalInfo < 24) {
            return additionalInfo;
        } else if (additionalInfo == 24) {
            return data[pos++] & 0xFF;
        } else if (additionalInfo == 25) {
            long v = ((data[pos] & 0xFFL) << 8) | (data[pos + 1] & 0xFFL);
            pos += 2;
            return v;
        } else if (additionalInfo == 26) {
            long v = ((data[pos] & 0xFFL) << 24) | ((data[pos + 1] & 0xFFL) << 16)
                    | ((data[pos + 2] & 0xFFL) << 8) | (data[pos + 3] & 0xFFL);
            pos += 4;
            return v;
        } else if (additionalInfo == 27) {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[pos++] & 0xFFL);
            }
            return v;
        }
        throw new IllegalStateException("Unsupported CBOR length encoding: " + additionalInfo);
    }
}
