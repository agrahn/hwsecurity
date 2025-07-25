/*
 * Copyright (C) 2018-2021 Confidential Technologies GmbH
 *
 * You can purchase a commercial license at https://hwsecurity.dev.
 * Buying such a license is mandatory as soon as you develop commercial
 * activities involving this program without disclosing the source code
 * of your own applications.
 *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.cotech.hw.internal.iso7816;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import de.cotech.hw.util.Arrays;


/** TLV data structure and parser implementation.
 *
 * This class is used for parsing and working with TLV packets as specified in
 * the Functional Specification of the OpenPGP application on ISO Smart Card
 * Operating Systems, and ISO 7816-4.
 *
 * Objects of this class are immutable data structs parsed from BER-TLV data.
 * They include at least the T, L and V fields they were originally parsed
 * from, subclasses may also carry more specific information.
 *
 * @see { @linktourl http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816-4_annex-d.aspx}
 *
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public class Iso7816TLV {

    public final int mT;
    public final int mL;
    public final byte[] mV;

    private Iso7816TLV(int T, int L, byte[] V) {
        mT = T;
        mL = L;
        mV = V;
    }

    public String prettyPrint() {
        return prettyPrint(0);
    }

    public String prettyPrint(int indent) {
        // lol
        String padding = "                                                  ".substring(0, indent*2);
        return padding + String.format(Locale.ROOT, "tag T %4x L %04d", mT, mL);
    }

    /** Read a single Iso7816 TLV packet from a given ByteBuffer, either
     * recursively or flat.
     *
     * This is a convenience wrapper for readSingle with ByteBuffer argument.
     */
    public static Iso7816TLV readSingle(byte[] data, boolean recursive) throws IOException {
        return readSingle(ByteBuffer.wrap(data), recursive);
    }

    /** Read a single Iso7816 TLV packet from a given ByteBuffer, either
     * recursively or flat.
     *
     * If the recursive flag is true, a composite packet will be parsed and
     * returned as an Iso7816CompositeTLV. Otherwise, a regular Iso7816TLV
     * object will be returned regardless of actual packet type.
     *
     * This method is fail-fast, if any parsing error occurs it will throw an
     * exception.
     *
     */
    public static Iso7816TLV readSingle(ByteBuffer data, boolean recursive) throws IOException {

        int T = data.get() & 0xff;
        boolean composite = (T & 0x20) == 0x20;
        if ((T & 0x1f) == 0x1f) {
            int T2 = data.get() & 0xff;
            if ((T2 & 0x1f) == 0x1f) {
                throw new IOException("Only tags up to two bytes are supported!");
            }
            T = (T << 8) | (T2 & 0x7f);
        }

        // Log.d(Constants.TAG, String.format("T %02x", T));

        // parse length, according to ISO 7816-4 (openpgp card 2.0 specs, page 24)
        int L = data.get() & 0xff;
        if (L == 0x81) {
            L = data.get() & 0xff;
        } else if (L == 0x82) {
            L = data.get() & 0xff;
            L = (L << 8) | (data.get() & 0xff);
        } else if (L >= 0x80) {
            throw new IOException("Invalid length field!");
        }

        // Log.d(Constants.TAG, String.format("L %02x", L));

        // read L bytes into new buffer
        byte[] V = new byte[L];
        data.get(V);

        // if we are supposed to parse composites, do that
        if (recursive && composite) {
            // Log.d(Constants.TAG, "parsing composite TLV");
            Iso7816TLV[] subs = readList(V, true);
            return new Iso7816CompositeTLV(T, L, V, subs);
        }

        return new Iso7816TLV(T, L, V);

    }

    /** Parse a list of TLV packets from byte data, recursively or flat.
     *
     * This method is fail-fast, if any parsing error occurs it will throw an
     * exception.
     *
     */
    public static Iso7816TLV[] readList(byte[] data, boolean recursive) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data);

        ArrayList<Iso7816TLV> result = new ArrayList<>();

        // read while data is available. this will fail if there is trailing data!
        while (buf.hasRemaining()) {
            // skip 0x00 and 0xFF filler bytes
            buf.mark();
            byte peek = buf.get();
            if (peek == 0xff || peek == 0x00) {
                continue;
            }
            buf.reset();

            Iso7816TLV packet = readSingle(buf, recursive);
            result.add(packet);
        }

        Iso7816TLV[] resultX = new Iso7816TLV[result.size()];
        result.toArray(resultX);
        return resultX;
    }

    /** This class represents a composite TLV packet.
     *
     * Note that only actual composite TLV packets are instances of this class.
     * A regular non-composite Iso7816TLV object may however be a composite
     * packet if it was parsed non-recursively.
     *
     */
    public static class Iso7816CompositeTLV extends Iso7816TLV {

        public final Iso7816TLV[] mSubs;

        public Iso7816CompositeTLV(int T, int L, byte[] V, Iso7816TLV[] subs) {
            super(T, L, V);

            mSubs = subs;
        }

        public String prettyPrint(int indent) {
            StringBuilder result = new StringBuilder();
            // lol
            result.append("                                                  ".substring(0, indent*2));
            result.append(String.format(Locale.ROOT, "composite tag T %4x L %04d", mT, mL));
            for (Iso7816TLV sub : mSubs) {
                result.append('\n');
                result.append(sub.prettyPrint(indent+1));
            }
            return result.toString();
        }

    }

    /** Recursively searches for a specific tag in a composite TLV packet structure, depth first. */
    public static Iso7816TLV findRecursive(Iso7816TLV in, int tag) {
        if (in.mT == tag) {
            return in;
        } else if (in instanceof Iso7816CompositeTLV) {
            for (Iso7816TLV sub : ((Iso7816CompositeTLV) in).mSubs) {
                Iso7816TLV result = findRecursive(sub, tag);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public static Iso7816TLV find(Iso7816TLV in, int tag) {
        if (in.mT == tag) {
            return in;
        } else if (in instanceof Iso7816CompositeTLV) {
            for (Iso7816TLV sub : ((Iso7816CompositeTLV) in).mSubs) {
                if (sub.mT == tag) {
                    return sub;
                }
            }
        }
        return null;
    }

    public static List<Iso7816TLV> findAll(Iso7816TLV in, int tag) {
        if (in.mT == tag) {
            return Collections.singletonList(in);
        } else if (in instanceof Iso7816CompositeTLV) {
            ArrayList<Iso7816TLV> result = new ArrayList<>();
            for (Iso7816TLV sub : ((Iso7816CompositeTLV) in).mSubs) {
                if (sub.mT == tag) {
                    result.add(sub);
                }
            }
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    public static byte[] encodeLength(int len) {
        if (len < 0) {
            throw new IllegalArgumentException("length is negative");
        } else if (len >= 16777216) {
            throw new IllegalArgumentException("length is too big: " + len);
        }
        byte[] res;
        if (len < 128) {
            res = new byte[1];
            res[0] = (byte) len;
        } else if (len < 256) {
            res = new byte[2];
            res[0] = -127;
            res[1] = (byte) len;
        } else if (len < 65536) {
            res = new byte[3];
            res[0] = -126;
            res[1] = (byte) (len / 256);
            res[2] = (byte) (len % 256);
        } else {
            res = new byte[4];

            res[0] = -125;
            res[1] = (byte) (len / 65536);
            res[2] = (byte) (len / 256);
            res[3] = (byte) (len % 256);
        }
        return res;
    }

    public static byte[] encode(int tag, byte[] content) {
        byte[] contentWithLength = Arrays.concatenate(encodeLength(content.length), content);
        return Arrays.prepend(contentWithLength, (byte) (tag & 0xff));
    }
}
