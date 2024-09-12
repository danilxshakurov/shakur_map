import org.junit.jupiter.api.Test;

import ru.shakur.ShakurMap;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentTest {

    private static final int THREAD_COUNT = 100;
    private static final int ELEMENT_COUNT = 1000;

    @Test
    public void testPutThreadSafety() throws InterruptedException {

        ShakurMap<Integer, String> map = new ShakurMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ELEMENT_COUNT; j++) {
                        map.put(threadId * ELEMENT_COUNT + j, "Value " + (threadId * ELEMENT_COUNT + j));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        for (int i = 0; i < THREAD_COUNT; i++) {
            for (int j = 0; j < ELEMENT_COUNT; j++) {
                String expectedValue = "Value " + (i * ELEMENT_COUNT + j);
                assertEquals(expectedValue, map.get(i * ELEMENT_COUNT + j), "Value mismatch for key: " + (i * ELEMENT_COUNT + j));
            }
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testGetThreadSafety() throws InterruptedException {

        ShakurMap<Integer, String> map = new ShakurMap<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            for (int j = 0; j < ELEMENT_COUNT; j++) {
                map.put(i * ELEMENT_COUNT + j, "Value " + (i * ELEMENT_COUNT + j));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ELEMENT_COUNT; j++) {
                        String expectedValue = "Value " + (threadId * ELEMENT_COUNT + j);
                        assertEquals(expectedValue, map.get(threadId * ELEMENT_COUNT + j), "Value mismatch for key: " + (threadId * ELEMENT_COUNT + j));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testRemoveThreadSafety() throws InterruptedException {
        ShakurMap<Integer, String> map = new ShakurMap<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            for (int j = 0; j < ELEMENT_COUNT; j++) {
                map.put(i * ELEMENT_COUNT + j, "Value " + (i * ELEMENT_COUNT + j));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ELEMENT_COUNT; j++) {
                        map.remove(threadId * ELEMENT_COUNT + j);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        for (int i = 0; i < THREAD_COUNT; i++) {
            for (int j = 0; j < ELEMENT_COUNT; j++) {
                assertNull(map.get(i * ELEMENT_COUNT + j), "Map should not contain key: " + (i * ELEMENT_COUNT + j));
            }
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testClearThreadSafety() throws InterruptedException {
        ShakurMap<Integer, String> map = new ShakurMap<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            for (int j = 0; j < ELEMENT_COUNT; j++) {
                map.put(i * ELEMENT_COUNT + j, "Value " + (i * ELEMENT_COUNT + j));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    map.clear();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertEquals(0, map.size(), "Map should be empty.");

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testContainsKeyThreadSafety() throws InterruptedException {
        ShakurMap<Integer, String> map = new ShakurMap<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            for (int j = 0; j < ELEMENT_COUNT; j++) {
                map.put(i * ELEMENT_COUNT + j, "Value " + (i * ELEMENT_COUNT + j));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ELEMENT_COUNT; j++) {
                        assertTrue(map.containsKey(threadId * ELEMENT_COUNT + j), "Key not found: " + (threadId * ELEMENT_COUNT + j));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void testContainsValueThreadSafety() throws InterruptedException {
        ShakurMap<Integer, String> map = new ShakurMap<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            for (int j = 0; j < ELEMENT_COUNT; j++) {
                map.put(i * ELEMENT_COUNT + j, "Value " + (i * ELEMENT_COUNT + j));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {

                    System.out.println("Thread " + threadId + " started");

                    for (int j = 0; j < ELEMENT_COUNT; j++) {
                        assertTrue(map.containsValue("Value " + (threadId * ELEMENT_COUNT + j)), "Value not found: " + "Value " + (threadId * ELEMENT_COUNT + j));
                    }

                    System.out.println("Thread " + threadId + " finished");

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        System.out.println("\nAll threads completed");
    }
}
