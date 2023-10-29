package org.shailesh;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A Snowflake-style unique ID generator.
 */
public enum UniqueIdGenerator {
    INSTANCE;  // The single instance

    // Custom epoch (Jan 1, 2023, in milliseconds, can be adjusted)
    private static final long CUSTOM_EPOCH = 1672531200000L;

    private final long workerIdBits = 10L;
    private final long sequenceBits = 12L;

    private final long maxWorkerId;
    private final long sequenceMask;

    private  long workerId;

    private final AtomicLong lastTimestamp = new AtomicLong(-1L);
    private final AtomicLong sequence = new AtomicLong(0L);

    // Private constructor to initialize the singleton.
    private UniqueIdGenerator() {
        // Initialize with a default worker ID (will be overridden when generating IDs).
        this.workerId = 0;

        // Calculate maxWorkerId and sequenceMask based on workerIdBits and sequenceBits
        this.maxWorkerId = -1L ^ (-1L << workerIdBits);
        this.sequenceMask = -1L ^ (-1L << sequenceBits);
    }

    /**
     * Sets the worker ID to be used when generating IDs.
     *
     * @param workerId The worker ID to set.
     */
    public void setWorkerId(long workerId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException("Worker ID can't be greater than " + maxWorkerId + " or less than 0");
        }
        this.workerId = workerId;
    }

    /**
     * Generates a new unique ID.
     *
     * @return A new unique ID.
     */
    public synchronized long generate() {
        long timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;

        // Ensure the clock doesn't move backward
        long last = lastTimestamp.get();
        while (timestamp < last) {
            // Simulate a clock moving backward by waiting until it catches up
//            Thread.yield();
            timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;
        }

        // If we are generating IDs within the same millisecond, increment the sequence
        if (lastTimestamp.get() == timestamp) {
            long newSequence = (sequence.incrementAndGet() & sequenceMask);

            // If the sequence overflows, wait for the next millisecond
            if (newSequence == 0) {
                timestamp = tilNextMillis(lastTimestamp.get());
            }
        } else {
            // If it's a new millisecond, reset the sequence to 0
            sequence.set(0);
        }

        lastTimestamp.set(timestamp);

        // Generate and return the unique ID
        return ((timestamp << (sequenceBits + workerIdBits)) | (workerId << sequenceBits) | sequence.get());
    }

    /**
     * Waits until the next millisecond if the current timestamp is the same as the previous one.
     *
     * @param lastTimestamp The previous timestamp.
     * @return The next timestamp.
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;
        }
        return timestamp;
    }

    // Setter for lastTimestamp for testing purposes
    public void setLastTimestamp(long timestamp) {
        lastTimestamp.set(timestamp);
    }
}
