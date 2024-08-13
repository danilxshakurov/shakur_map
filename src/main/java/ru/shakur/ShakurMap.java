package ru.shakur;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

public class ShakurMap<K, V> implements Map<K, V> {

    private static final int DEFAULT_CAPACITY = 16; // начальный размер таблицы
    private static final float LOAD_FACTOR = 0.75f; // коэффициент загрузки

    private volatile ShakurEntry<K, V>[] arrayBuckets; // массив бакетов
    private final AtomicInteger size = new AtomicInteger(0); // количество элементов в карте
    private volatile int threshold; // порог увеличения размера таблицы

    private volatile ShakurEntry<K, V> nullKeyEntry; // Entry для хранения null ключа

    @SuppressWarnings("unchecked")
    public ShakurMap() {
        arrayBuckets = new ShakurEntry[DEFAULT_CAPACITY];
        threshold = (int) (DEFAULT_CAPACITY * LOAD_FACTOR);
    }

    private static class ShakurEntry<K, V> implements Map.Entry<K, V> {
        K key;
        V value;
        ShakurEntry<K, V> next;

        ShakurEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShakurEntry<?, ?> that = (ShakurEntry<?, ?>) o;
            return Objects.equals(key, that.key) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }
    }

    @Override
    public synchronized int size() {
        return size.get();
    }

    @Override
    public synchronized boolean isEmpty() {
        return size.get() == 0;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        if (key == null) {
            return nullKeyEntry != null;
        }
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % arrayBuckets.length;
        for (ShakurEntry<K, V> entry = arrayBuckets[index]; entry != null; entry = entry.next) {
            if (entry.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        if (nullKeyEntry != null && Objects.equals(nullKeyEntry.value, value)) {
            return true;
        }
        if (value == null) {
            for (ShakurEntry<K, V> entry : arrayBuckets) {
                for (ShakurEntry<K, V> current = entry; current != null; current = current.next) {
                    if (current.value == null) {
                        return true;
                    }
                }
            }
        } else {
            for (ShakurEntry<K, V> entry : arrayBuckets) {
                for (ShakurEntry<K, V> current = entry; current != null; current = current.next) {
                    if (value.equals(current.value)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public synchronized V get(Object key) {
        if (key == null) {
            return nullKeyEntry == null ? null : nullKeyEntry.value;
        }
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % arrayBuckets.length;
        for (ShakurEntry<K, V> entry = arrayBuckets[index]; entry != null; entry = entry.next) {
            if (entry.key.equals(key)) {
                return entry.value;
            }
        }
        return null;
    }

    @Override
    public synchronized V put(K key, V value) {
        if (key == null) {
            if (nullKeyEntry != null) {
                V oldValue = nullKeyEntry.value;
                nullKeyEntry.value = value;
                return oldValue;
            } else {
                nullKeyEntry = new ShakurEntry<>(null, value);
                size.incrementAndGet();
                return null;
            }
        }

        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % arrayBuckets.length;

        for (ShakurEntry<K, V> entry = arrayBuckets[index]; entry != null; entry = entry.next) {
            if (entry.key.equals(key)) {
                V oldValue = entry.value;
                entry.value = value;
                return oldValue;
            }
        }

        ShakurEntry<K, V> newEntry = new ShakurEntry<>(key, value);
        newEntry.next = arrayBuckets[index];
        arrayBuckets[index] = newEntry;
        size.incrementAndGet();

        if (size.get() > threshold) {
            resize();
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private synchronized void resize() {
        int newCapacity = arrayBuckets.length * 2;
        ShakurEntry<K, V>[] newArrayBuckets = new ShakurEntry[newCapacity];
        threshold = (int) (newCapacity * LOAD_FACTOR);

        for (ShakurEntry<K, V> bucket : arrayBuckets) {
            ShakurEntry<K, V> entry = bucket;
            while (entry != null) {
                ShakurEntry<K, V> next = entry.next;
                int hash = entry.key.hashCode();
                int index = (hash & 0x7FFFFFFF) % newCapacity;
                entry.next = newArrayBuckets[index];
                newArrayBuckets[index] = entry;
                entry = next;
            }
        }

        arrayBuckets = newArrayBuckets;
    }

    @Override
    public synchronized V remove(Object key) {
        if (key == null) {
            if (nullKeyEntry != null) {
                V oldValue = nullKeyEntry.value;
                nullKeyEntry = null;
                size.decrementAndGet();
                return oldValue;
            }
            return null;
        }

        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % arrayBuckets.length;

        ShakurEntry<K, V> previous = null;
        ShakurEntry<K, V> current = arrayBuckets[index];

        while (current != null) {
            if (current.key.equals(key)) {
                if (previous == null) {
                    arrayBuckets[index] = current.next;
                } else {
                    previous.next = current.next;
                }
                size.decrementAndGet();
                return current.value;
            }
            previous = current;
            current = current.next;
        }

        return null;
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public synchronized void clear() {
        Arrays.fill(arrayBuckets, null);
        nullKeyEntry = null;
        size.set(0);
    }

    @Override
    public synchronized Set<K> keySet() {
        Set<K> keys = new CopyOnWriteArraySet<>();
        if (nullKeyEntry != null) {
            keys.add(null);
        }
        for (ShakurEntry<K, V> bucket : arrayBuckets) {
            ShakurEntry<K, V> entry = bucket;
            while (entry != null) {
                keys.add(entry.key);
                entry = entry.next;
            }
        }
        return keys;
    }

    @Override
    public synchronized Collection<V> values() {
        Collection<V> values = new ArrayList<>();
        if (nullKeyEntry != null) {
            values.add(nullKeyEntry.value);
        }
        for (ShakurEntry<K, V> bucket : arrayBuckets) {
            ShakurEntry<K, V> entry = bucket;
            while (entry != null) {
                values.add(entry.value);
                entry = entry.next;
            }
        }
        return values;
    }

    @Override
    public synchronized Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> entries = new CopyOnWriteArraySet<>();
        if (nullKeyEntry != null) {
            entries.add(nullKeyEntry);
        }
        for (ShakurEntry<K, V> bucket : arrayBuckets) {
            ShakurEntry<K, V> entry = bucket;
            while (entry != null) {
                entries.add(entry);
                entry = entry.next;
            }
        }
        return entries;
    }
}
