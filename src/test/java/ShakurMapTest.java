import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.shakur.ShakurMap;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ShakurMapTest {

    private ShakurMap<String, Integer> map;

    @BeforeEach
    public void setUp() {
        map = new ShakurMap<>();
    }

    @Test
    public void testPutAndGet() {
        map.put("one", 1);
        assertEquals(1, map.get("one"));
        map.put("two", 2);
        assertEquals(2, map.get("two"));
    }

    @Test
    public void testSize() {
        assertEquals(0, map.size());
        map.put("one", 1);
        assertEquals(1, map.size());
        map.put("two", 2);
        assertEquals(2, map.size());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(map.isEmpty());
        map.put("one", 1);
        assertFalse(map.isEmpty());
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    public void testContainsKey() {
        map.put("one", 1);
        assertTrue(map.containsKey("one"));
        assertFalse(map.containsKey("two"));
    }

    @Test
    public void testContainsValue() {
        map.put("one", 1);
        assertTrue(map.containsValue(1));
        assertFalse(map.containsValue(2));
    }

    @Test
    public void testRemove() {
        map.put("one", 1);
        assertEquals(1, map.remove("one"));
        assertNull(map.remove("two"));
        assertFalse(map.containsKey("one"));
    }

    @Test
    public void testPutAll() {
        Map<String, Integer> otherMap = Map.of("three", 3, "four", 4, "five", 5);
        map.putAll(otherMap);
        assertEquals(3, map.size());
        assertEquals(3, map.get("three"));
        assertEquals(4, map.get("four"));
    }

    @Test
    public void testClear() {
        map.put("one", 1);
        map.put("two", 2);
        map.clear();
        assertTrue(map.isEmpty());
        assertNull(map.get("one"));
        assertNull(map.get("two"));
    }

    @Test
    public void testKeySet() {
        map.put("one", 1);
        map.put("two", 2);
        Set<String> keySet = map.keySet();
        assertTrue(keySet.contains("one"));
        assertTrue(keySet.contains("two"));
    }

    @Test
    public void testValues() {
        map.put("one", 1);
        map.put("two", 2);
        var values = map.values();
        assertTrue(values.contains(1));
        assertTrue(values.contains(2));
    }

    @Test
    public void testEntrySet() {
        map.put("one", 1);
        map.put("two", 2);
        var entrySet = map.entrySet();
        assertTrue(entrySet.contains(Map.entry("one", 1)));
        assertTrue(entrySet.contains(Map.entry("two", 2)));
    }

    @Test
    public void testResize() {
        for (int i = 0; i < 20; i++) {
            map.put("key" + i, i);
        }
        assertEquals(20, map.size());
        assertNotNull(map.get("key19"));
    }
}
