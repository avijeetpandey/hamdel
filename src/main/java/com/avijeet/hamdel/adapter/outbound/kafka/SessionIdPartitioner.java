package com.avijeet.hamdel.adapter.outbound.kafka;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;

import java.util.Map;

/**
 * Custom Kafka Partitioner that routes messages by session_id hash,
 * ensuring strict chronological ordering per session.
 */
public class SessionIdPartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                         Object value, byte[] valueBytes, Cluster cluster) {
        int numPartitions = cluster.partitionCountForTopic(topic);
        if (key == null || numPartitions <= 0) {
            return 0;
        }
        // Use murmur2 consistent hashing — same algorithm as Kafka's default but forced on sessionId
        int hash = murmur2(key.toString().getBytes());
        return Math.abs(hash) % numPartitions;
    }

    @Override
    public void close() { }

    @Override
    public void configure(Map<String, ?> configs) { }

    /**
     * Murmur2 hash — same as DefaultPartitioner to remain consistent with Kafka internals.
     */
    private int murmur2(final byte[] data) {
        int length = data.length;
        int seed = 0x9747b28c;
        final int m = 0x5bd1e995;
        final int r = 24;
        int h = seed ^ length;
        int lengthAligned = length >> 2;
        for (int i = 0; i < lengthAligned; i++) {
            int offset = i << 2;
            int k = (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8)
                    | ((data[offset + 2] & 0xFF) << 16)
                    | ((data[offset + 3] & 0xFF) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }
        int remaining = length & 3;
        int offset = lengthAligned << 2;
        if (remaining == 3) h ^= (data[offset + 2] & 0xFF) << 16;
        if (remaining >= 2) h ^= (data[offset + 1] & 0xFF) << 8;
        if (remaining >= 1) { h ^= (data[offset] & 0xFF); h *= m; }
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h;
    }
}
