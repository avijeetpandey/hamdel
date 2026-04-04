package com.avijeet.hamdel.adapter.outbound.kafka;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionIdPartitionerTest {

    private final SessionIdPartitioner partitioner = new SessionIdPartitioner();

    @Test
    void sameSessionId_alwaysMapsToSamePartition() {
        var cluster = mockCluster(12);
        int first  = partitioner.partition("heartbeat-events", "session-abc", null, null, null, cluster);
        int second = partitioner.partition("heartbeat-events", "session-abc", null, null, null, cluster);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void nullKey_returnsZero() {
        var cluster = mockCluster(12);
        int partition = partitioner.partition("heartbeat-events", null, null, null, null, cluster);
        assertThat(partition).isEqualTo(0);
    }

    @Test
    void partitionIsWithinBounds() {
        var cluster = mockCluster(12);
        for (int i = 0; i < 1000; i++) {
            int p = partitioner.partition("heartbeat-events", "session-" + i, null, null, null, cluster);
            assertThat(p).isBetween(0, 11);
        }
    }

    private org.apache.kafka.common.Cluster mockCluster(int numPartitions) {
        var partitions = new java.util.ArrayList<org.apache.kafka.common.PartitionInfo>();
        for (int i = 0; i < numPartitions; i++) {
            partitions.add(new org.apache.kafka.common.PartitionInfo(
                    "heartbeat-events", i, null, new org.apache.kafka.common.Node[0], new org.apache.kafka.common.Node[0]));
        }
        return new org.apache.kafka.common.Cluster(
                "test-cluster",
                java.util.List.of(new org.apache.kafka.common.Node(0, "localhost", 9092)),
                partitions,
                java.util.Set.of(),
                java.util.Set.of());
    }
}
