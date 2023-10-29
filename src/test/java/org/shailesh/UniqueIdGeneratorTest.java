package org.shailesh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UniqueIdGeneratorTest {

    private UniqueIdGenerator generator;

    @BeforeEach
    public void setup() {
        generator = UniqueIdGenerator.INSTANCE;
    }

    @Test
    public void testGenerateUniqueId() {
        long id = generator.generate();
        assertTrue(id > 0);
    }

    @Test
    public void testSetWorkerIdAndGenerateUniqueId() {
        long customWorkerId = 1;
        generator.setWorkerId(customWorkerId);

        long id = generator.generate();
        assertTrue(id > 0);
    }

    @Test
    public void testInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> generator.setWorkerId(-1));
    }

//    @Test
//    public void testClockMovedBackwards() {
//        // Manually set the lastTimestamp to a future time to simulate a clock moving backward.
//        generator.setWorkerId(0);
//        generator.setLastTimestamp(System.currentTimeMillis() + 1000);
//
//        assertThrows(IllegalStateException.class, () -> generator.generate());
//    }

    @Test
    public void testUniqueIdsGeneratedConcurrently() {
        // Generate unique IDs concurrently and ensure they are different.
        int numIds = 1000;
        long[] ids = new long[numIds];

        Thread[] threads = new Thread[numIds];
        for (int i = 0; i < numIds; i++) {
            final int index = i;
            threads[i] = new Thread(() -> ids[index] = generator.generate());
            threads[i].start();
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int i = 0; i < numIds; i++) {
            for (int j = i + 1; j < numIds; j++) {
                assertNotEquals(ids[i], ids[j]);
            }
        }
    }
}
