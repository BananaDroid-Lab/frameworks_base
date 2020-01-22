/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util.proto;

import android.util.AggStats;
import android.util.Duration;

import java.io.IOException;

/**
 * This class contains a list of helper functions to write common proto in
 * //frameworks/base/core/proto/android/base directory
 * @hide
 */
public class ProtoUtils {

    /**
     * Dump AggStats to ProtoOutputStream
     */
    public static void toAggStatsProto(ProtoOutputStream proto, long fieldId,
            long min, long average, long max) {
        final long aggStatsToken = proto.start(fieldId);
        proto.write(AggStats.MIN, min);
        proto.write(AggStats.AVERAGE, average);
        proto.write(AggStats.MAX, max);
        proto.end(aggStatsToken);
    }

    /**
     * Dump Duration to ProtoOutputStream
     */
    public static void toDuration(ProtoOutputStream proto, long fieldId, long startMs, long endMs) {
        final long token = proto.start(fieldId);
        proto.write(Duration.START_MS, startMs);
        proto.write(Duration.END_MS, endMs);
        proto.end(token);
    }

    /**
     * Helper function to write bit-wise flags to proto as repeated enums
     */
    public static void writeBitWiseFlagsToProtoEnum(ProtoOutputStream proto, long fieldId,
            int flags, int[] origEnums, int[] protoEnums) {
        if (protoEnums.length != origEnums.length) {
            throw new IllegalArgumentException("The length of origEnums must match protoEnums");
        }
        int len = origEnums.length;
        for (int i = 0; i < len; i++) {
            // handle zero flag case.
            if (origEnums[i] == 0 && flags == 0) {
                proto.write(fieldId, protoEnums[i]);
                return;
            }
            if ((flags & origEnums[i]) != 0) {
                proto.write(fieldId, protoEnums[i]);
            }
        }
    }

    /**
     * Provide debug data about the current field as a string
     */
    public static String currentFieldToString(ProtoInputStream proto) throws IOException {
        StringBuilder sb = new StringBuilder();

        final int fieldNumber = proto.getFieldNumber();
        final int wireType = proto.getWireType();
        long fieldConstant;

        sb.append("Offset : 0x" + Integer.toHexString(proto.getOffset()));
        sb.append("\nField Number : 0x" + Integer.toHexString(proto.getFieldNumber()));
        sb.append("\nWire Type : ");
        switch (wireType) {
            case ProtoStream.WIRE_TYPE_VARINT:
                sb.append("varint");
                fieldConstant = ProtoStream.makeFieldId(fieldNumber,
                        ProtoStream.FIELD_COUNT_SINGLE | ProtoStream.FIELD_TYPE_INT64);
                sb.append("\nField Value : 0x" + Long.toHexString(proto.readLong(fieldConstant)));
                break;
            case ProtoStream.WIRE_TYPE_FIXED64:
                sb.append("fixed64");
                fieldConstant = ProtoStream.makeFieldId(fieldNumber,
                        ProtoStream.FIELD_COUNT_SINGLE | ProtoStream.FIELD_TYPE_FIXED64);
                sb.append("\nField Value : 0x" + Long.toHexString(proto.readLong(fieldConstant)));
                break;
            case ProtoStream.WIRE_TYPE_LENGTH_DELIMITED:
                sb.append("length delimited");
                fieldConstant = ProtoStream.makeFieldId(fieldNumber,
                        ProtoStream.FIELD_COUNT_SINGLE | ProtoStream.FIELD_TYPE_BYTES);
                sb.append("\nField Bytes : " + proto.readBytes(fieldConstant));
                break;
            case ProtoStream.WIRE_TYPE_START_GROUP:
                sb.append("start group");
                break;
            case ProtoStream.WIRE_TYPE_END_GROUP:
                sb.append("end group");
                break;
            case ProtoStream.WIRE_TYPE_FIXED32:
                sb.append("fixed32");
                fieldConstant = ProtoStream.makeFieldId(fieldNumber,
                        ProtoStream.FIELD_COUNT_SINGLE | ProtoStream.FIELD_TYPE_FIXED32);
                sb.append("\nField Value : 0x" + Integer.toHexString(proto.readInt(fieldConstant)));
                break;
            default:
                sb.append("unknown(" + proto.getWireType() + ")");
        }
        return sb.toString();
    }
}
