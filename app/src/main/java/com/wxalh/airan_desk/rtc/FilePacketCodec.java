package com.wxalh.airan_desk.rtc;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;
import org.webrtc.DataChannel;

@SuppressWarnings({"deprecation", "unchecked"})
final class FilePacketCodec {
    private static final String TAG = "FilePacketCodec";
    private static final int FRAGMENT_SIZE = 8192;
    private static final int HEADER_SIZE = 32;
    private static final int PAYLOAD_SIZE = 8160;
    private static final long BUFFER_HIGH_WATERMARK_BYTES = 2L * 1024L * 1024L;
    private static final long BUFFER_LOW_WATERMARK_BYTES = 512L * 1024L;
    private static final long BUFFER_WAIT_TIMEOUT_MS = 30000L;
    private static final long BUFFER_WAIT_SLEEP_MS = 5L;
    private static final int MAX_HEADER_SIZE = 1024 * 1024;
    private static final long REASSEMBLY_TIMEOUT_MS = 10L * 60L * 1000L;
    private final Context context;
    private final Listener listener;
    private final Map<String, Reassembly> buffers = new HashMap<String, Reassembly>();
    private final Set<String> cancelledTransferIds = new HashSet<String>();

    FilePacketCodec(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    boolean sendPacket(DataChannel channel, JSONObject header, File payload) {
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            this.notifyError("file channel is not open");
            return false;
        }
        FileInputStream input = null;
        try {
            String transferId = header.optString("transferId");
            byte[] headerBytes = header.toString().getBytes("UTF-8");
            long payloadSize = payload == null ? 0L : payload.length();
            long totalSize = 4L + (long)headerBytes.length + payloadSize;
            long totalFragments = Math.max(1L, (totalSize + 8160L - 1L) / 8160L);
            UUID id = UUID.randomUUID();
            byte[] uuidBytes = FilePacketCodec.uuidToBytes(id);
            ByteArrayOutputStream prefix = new ByteArrayOutputStream();
            FilePacketCodec.writeInt(prefix, headerBytes.length);
            prefix.write(headerBytes);
            byte[] pending = prefix.toByteArray();
            int pendingOffset = 0;
            if (payload != null) {
                input = new FileInputStream(payload);
            }
            byte[] fileBuf = new byte[8160];
            long payloadBytesSent = 0L;
            for (long index = 0L; index < totalFragments; ++index) {
                int want;
                int read;
                if (this.isTransferCancelled(transferId)) {
                    throw new IllegalStateException("file transfer cancelled: " + transferId);
                }
                ByteArrayOutputStream fragmentPayload = new ByteArrayOutputStream(8160);
                while (pendingOffset < pending.length && fragmentPayload.size() < 8160) {
                    int copy = Math.min(8160 - fragmentPayload.size(), pending.length - pendingOffset);
                    fragmentPayload.write(pending, pendingOffset, copy);
                    pendingOffset += copy;
                }
                while (input != null && fragmentPayload.size() < 8160 && (read = input.read(fileBuf, 0, Math.min(fileBuf.length, want = 8160 - fragmentPayload.size()))) > 0) {
                    fragmentPayload.write(fileBuf, 0, read);
                    payloadBytesSent += (long)read;
                }
                byte[] payloadBytes = fragmentPayload.toByteArray();
                ByteBuffer packet = ByteBuffer.allocate(32 + payloadBytes.length);
                packet.put(uuidBytes);
                packet.putLong(totalFragments);
                packet.putLong(index);
                packet.put(payloadBytes);
                packet.flip();
                this.waitForBufferedAmount(channel, transferId);
                if (!channel.send(new DataChannel.Buffer(packet, true))) {
                    throw new IllegalStateException("data channel send failed");
                }
                this.notifySendProgress(header, payloadBytesSent, payloadSize);
            }
            boolean bl = true;
            return bl;
        }
        catch (Exception e) {
            this.notifyError("send packet failed: " + e.getMessage());
            boolean bl = false;
            return bl;
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                }
                catch (Exception exception) {}
            }
        }
    }

    private void notifySendProgress(JSONObject header, long sentBytes, long totalBytes) {
        if (this.listener != null) {
            this.listener.onSendProgress(header, sentBytes, totalBytes);
        }
    }

    synchronized void cancelTransfer(String transferId) {
        if (transferId != null && transferId.length() > 0) {
            this.cancelledTransferIds.add(transferId);
        }
    }

    synchronized void clearTransferCancel(String transferId) {
        if (transferId != null && transferId.length() > 0) {
            this.cancelledTransferIds.remove(transferId);
        }
    }

    synchronized void cancelAllReassemblies() {
        for (Reassembly reassembly : this.buffers.values()) {
            try {
                reassembly.output.close();
            }
            catch (Exception exception) {
                // empty catch block
            }
            try {
                reassembly.file.delete();
            }
            catch (Exception exception) {}
        }
        this.buffers.clear();
    }

    private synchronized boolean isTransferCancelled(String transferId) {
        return transferId != null && transferId.length() > 0 && this.cancelledTransferIds.contains(transferId);
    }

    void onFragment(ByteBuffer data) {
        Reassembly completed = null;
        try {
            synchronized (this) {
                this.cleanupExpiredReassembliesLocked();
                if (data.remaining() < 32) {
                    this.notifyError("file fragment too small");
                    return;
                }
                byte[] uuidBytes = new byte[16];
                data.get(uuidBytes);
                String id = FilePacketCodec.bytesToUuid(uuidBytes).toString();
                long total = data.getLong();
                long index = data.getLong();
                if (total <= 0L || total > 1000000L || index < 0L || index >= total) {
                    this.notifyError("invalid file fragment index");
                    return;
                }
                Reassembly reassembly = this.buffers.get(id);
                if (reassembly == null) {
                    reassembly = new Reassembly(this.createTempFile("airan_packet_", ".bin"), total);
                    this.buffers.put(id, reassembly);
                }
                if (reassembly.received.contains(index)) {
                    reassembly.lastUpdatedMs = System.currentTimeMillis();
                    return;
                }
                byte[] payload = new byte[data.remaining()];
                data.get(payload);
                reassembly.output.getChannel().position(index * 8160L);
                reassembly.output.write(payload);
                reassembly.received.add(index);
                reassembly.fragmentSizes.put(index, payload.length);
                reassembly.lastUpdatedMs = System.currentTimeMillis();
                this.notifyReceiveProgress(reassembly, false);
                if ((long)reassembly.received.size() == reassembly.totalFragments) {
                    this.buffers.remove(id);
                    completed = reassembly;
                }
            }
            if (completed != null) {
                this.complete(completed);
            }
        }
        catch (Exception e) {
            this.notifyError("receive packet failed: " + e.getMessage());
            if (completed != null) {
                completed.deleteQuietly();
            }
        }
    }

    private void complete(Reassembly reassembly) throws Exception {
        int read;
        this.notifyReceiveProgress(reassembly, true);
        reassembly.output.close();
        FileInputStream input = null;
        File payload = null;
        try {
            input = new FileInputStream(reassembly.file);
            byte[] sizeBytes = new byte[4];
            if (input.read(sizeBytes) != 4) {
                throw new IllegalStateException("missing header size");
            }
            int headerSize = (sizeBytes[0] & 0xFF) << 24 | (sizeBytes[1] & 0xFF) << 16 | (sizeBytes[2] & 0xFF) << 8 | sizeBytes[3] & 0xFF;
            long remaining = Math.max(0L, reassembly.file.length() - 4L);
            if (headerSize <= 0 || headerSize > MAX_HEADER_SIZE || (long)headerSize > remaining) {
                throw new IllegalStateException("invalid packet header size: " + headerSize);
            }
            byte[] headerBytes = new byte[headerSize];
            if (input.read(headerBytes) != headerSize) {
                throw new IllegalStateException("missing packet header");
            }
            JSONObject header = new JSONObject(new String(headerBytes, "UTF-8"));
            payload = this.createTempFile("airan_payload_", ".bin");
            FileOutputStream output = new FileOutputStream(payload);
            try {
                byte[] buf = new byte[32768];
                while ((read = input.read(buf)) >= 0) {
                    output.write(buf, 0, read);
                }
            } finally {
                output.close();
            }
            this.listener.onPacket(header, payload);
            payload = null;
        } finally {
            if (input != null) {
                input.close();
            }
            reassembly.file.delete();
            if (payload != null) {
                payload.delete();
            }
        }
    }

    private void notifyReceiveProgress(Reassembly reassembly, boolean done) {
        if (this.listener == null || reassembly == null) {
            return;
        }
        try {
            JSONObject header = this.ensureReassemblyHeader(reassembly);
            if (header == null) {
                return;
            }
            long total = header.optLong("transferTotalBytes", header.optLong("file_size", reassembly.payloadFileSize));
            long base = header.optLong("transferBaseBytes", 0L);
            long current = this.receivedPayloadBytes(reassembly);
            long received = total > 0L ? Math.min(total, base + current) : current;
            this.listener.onReceiveProgress(header, Math.max(0L, received), Math.max(0L, total), done);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private JSONObject ensureReassemblyHeader(Reassembly reassembly) throws Exception {
        if (reassembly.header != null) {
            return reassembly.header;
        }
        if (!reassembly.received.contains(0L)) {
            return null;
        }
        try (RandomAccessFile input = new RandomAccessFile(reassembly.file, "r");){
            if (input.length() < 4L) {
                JSONObject jSONObject = null;
                return jSONObject;
            }
            input.seek(0L);
            int headerSize = input.readInt();
            if (headerSize <= 0 || headerSize > MAX_HEADER_SIZE) {
                JSONObject jSONObject = null;
                return jSONObject;
            }
            int prefixSize = 4 + headerSize;
            if (!this.hasContiguousBytes(reassembly, prefixSize)) {
                JSONObject jSONObject = null;
                return jSONObject;
            }
            byte[] headerBytes = new byte[headerSize];
            input.readFully(headerBytes);
            reassembly.header = new JSONObject(new String(headerBytes, "UTF-8"));
            reassembly.prefixSize = prefixSize;
            reassembly.payloadFileSize = reassembly.header.optLong("file_size", 0L);
            JSONObject jSONObject = reassembly.header;
            return jSONObject;
        }
    }

    private boolean hasContiguousBytes(Reassembly reassembly, int requiredBytes) {
        int requiredFragments = (requiredBytes + 8160 - 1) / 8160;
        for (long i = 0L; i < (long)requiredFragments; ++i) {
            Integer size = reassembly.fragmentSizes.get(i);
            if (size == null) {
                return false;
            }
            if (i >= (long)(requiredFragments - 1) || size >= 8160) continue;
            return false;
        }
        int lastBytes = requiredBytes - (requiredFragments - 1) * 8160;
        Integer lastSize = reassembly.fragmentSizes.get((long)requiredFragments - 1L);
        return lastSize != null && lastSize >= lastBytes;
    }

    private long receivedPayloadBytes(Reassembly reassembly) {
        if (reassembly.header == null || reassembly.prefixSize <= 0) {
            return 0L;
        }
        long received = 0L;
        for (Map.Entry<Long, Integer> entry : reassembly.fragmentSizes.entrySet()) {
            long payloadStart;
            long fragmentStart = entry.getKey() * 8160L;
            long fragmentEnd = fragmentStart + (long)entry.getValue().intValue();
            if (fragmentEnd <= (payloadStart = Math.max(fragmentStart, (long)reassembly.prefixSize))) continue;
            received += fragmentEnd - payloadStart;
        }
        if (reassembly.payloadFileSize > 0L) {
            received = Math.min(received, reassembly.payloadFileSize);
        }
        return Math.max(0L, received);
    }

    private File createTempFile(String prefix, String suffix) throws Exception {
        File dir = new File(this.context.getCacheDir(), "transfers");
        if (!dir.exists() && !dir.mkdirs()) {
            dir = this.context.getCacheDir();
        }
        return File.createTempFile(prefix, suffix, dir);
    }

    private void cleanupExpiredReassembliesLocked() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Reassembly>> iterator = this.buffers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Reassembly> entry = iterator.next();
            Reassembly reassembly = entry.getValue();
            if (reassembly != null && now - reassembly.lastUpdatedMs > REASSEMBLY_TIMEOUT_MS) {
                reassembly.deleteQuietly();
                iterator.remove();
            }
        }
    }

    private void waitForBufferedAmount(DataChannel channel, String transferId) throws Exception {
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            throw new IllegalStateException("file channel closed");
        }
        if (channel.bufferedAmount() < BUFFER_HIGH_WATERMARK_BYTES) {
            return;
        }
        long start = System.currentTimeMillis();
        while (channel.state() == DataChannel.State.OPEN && channel.bufferedAmount() > BUFFER_LOW_WATERMARK_BYTES) {
            if (this.isTransferCancelled(transferId)) {
                throw new IllegalStateException("file transfer cancelled: " + transferId);
            }
            if (System.currentTimeMillis() - start > BUFFER_WAIT_TIMEOUT_MS) {
                throw new IllegalStateException("timed out waiting for file channel backpressure to drain: buffered=" + channel.bufferedAmount());
            }
            Thread.sleep(BUFFER_WAIT_SLEEP_MS);
        }
        if (channel.state() != DataChannel.State.OPEN) {
            throw new IllegalStateException("file channel closed");
        }
    }

    private void notifyError(String message) {
        Log.e((String)TAG, (String)message);
        if (this.listener != null) {
            this.listener.onError(message);
        }
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value >> 24 & 0xFF);
        output.write(value >> 16 & 0xFF);
        output.write(value >> 8 & 0xFF);
        output.write(value & 0xFF);
    }

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static final class Reassembly {
        final File file;
        final FileOutputStream output;
        final long totalFragments;
        final Set<Long> received = new HashSet<Long>();
        final Map<Long, Integer> fragmentSizes = new HashMap<Long, Integer>();
        long lastUpdatedMs;
        JSONObject header;
        int prefixSize;
        long payloadFileSize;

        Reassembly(File file, long totalFragments) throws Exception {
            this.file = file;
            this.output = new FileOutputStream(file);
            this.totalFragments = totalFragments;
            this.lastUpdatedMs = System.currentTimeMillis();
        }

        void deleteQuietly() {
            try {
                this.output.close();
            }
            catch (Exception exception) {}
            try {
                this.file.delete();
            }
            catch (Exception exception) {}
        }
    }

    static interface Listener {
        public void onPacket(JSONObject var1, File var2);

        public void onSendProgress(JSONObject var1, long var2, long var4);

        public void onReceiveProgress(JSONObject var1, long var2, long var4, boolean var6);

        public void onError(String var1);
    }
}
