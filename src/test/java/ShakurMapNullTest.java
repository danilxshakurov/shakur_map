import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.shakur.ShakurMap;

import static org.junit.jupiter.api.Assertions.*;

public class ShakurMapNullTest {

    private ShakurMap<String, Integer> map;

    @BeforeEach
    public void setUp() {
        map = new ShakurMap<>();
    }

    @Test
    public void testPutNullKey() {
        map.put(null, 1);
        assertEquals(1, map.get(null));
        assertTrue(map.containsKey(null));
        assertEquals(1, map.size());
    }

    @Test
    public void testPutNullValue() {
        map.put("key", null);
        assertNull(map.get("key"));
        assertTrue(map.containsValue(null));
        assertEquals(1, map.size());
    }

    @Test
    public void testPutNullKeyAndValue() {
        map.put(null, null);
        assertNull(map.get(null));
        assertTrue(map.containsKey(null));
        assertTrue(map.containsValue(null));
        assertEquals(1, map.size());
    }

    @Test
    public void testRemoveNullKey() {
        map.put(null, 1);
        assertEquals(1, map.remove(null));
        assertFalse(map.containsKey(null));
        assertEquals(0, map.size());
    }

    @Test
    public void testRemoveNullValue() {
        map.put("key", null);
        map.remove("key");
        assertFalse(map.containsKey("key"));
        assertEquals(0, map.size());
    }

    @Test
    public void testContainsNullKey() {
        map.put(null, 1);
        assertTrue(map.containsKey(null));
        map.remove(null);
        assertFalse(map.containsKey(null));
    }

    @Test
    public void testContainsNullValue() {
        map.put("key", null);
        assertTrue(map.containsValue(null));
        map.put("key", 2);
        assertFalse(map.containsValue(null));
    }

    @Test
    public void testClearWithNullKeyAndValue() {
        map.put(null, null);
        map.put("key", null);
        map.put("anotherKey", 2);
        map.clear();
        assertEquals(0, map.size());
        assertFalse(map.containsKey(null));
        assertFalse(map.containsKey("key"));
        assertFalse(map.containsKey("anotherKey"));
    }

    @Test
    public void testMultipleKeysWithNullValues() {
        map.put("key1", null);
        map.put("key2", null);
        map.put("key3", null);
        assertTrue(map.containsKey("key1"));
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsKey("key3"));
        assertNull(map.get("key1"));
        assertNull(map.get("key2"));
        assertNull(map.get("key3"));
        assertEquals(3, map.size());
        assertTrue(map.containsValue(null));
    }
}