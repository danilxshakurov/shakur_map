package ru.shakur;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ShakurMap<K, V> implements Map<K, V> {

    private static final int DEFAULT_CAPACITY = 16; // начальный размер таблицы
    private static final float LOAD_FACTOR = 0.75f; // коэффициент загрузки
    private final AtomicInteger size = new AtomicInteger(0); // количество элементов в карте
    private volatile int threshold; // порог увеличения размера таблицы
    private volatile ShakurEntry<K, V> nullKeyEntry; // Entry для хранения null ключа
    private AtomicReference<ShakurEntry<K, V>>[] arrayBuckets; // массив бакетов
    private ReentrantLock[] locks; // массив блокировок
    private final ReentrantLock nullKeyLock = new ReentrantLock(); // блокировка для null ключа
    private final ReentrantLock globalLock = new ReentrantLock(); // глобальная блокировка
    private final ReentrantLock resizeLock = new ReentrantLock(); // блокировка ресайза
    private final Condition resizeCondition = resizeLock.newCondition(); // координация потоков
    private final AtomicBoolean isResizing = new AtomicBoolean(false); // проверка запущен ли ресайз

    @SuppressWarnings("unchecked")
    public ShakurMap() {
        arrayBuckets = new AtomicReference[DEFAULT_CAPACITY];
        locks = new ReentrantLock[DEFAULT_CAPACITY];
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            arrayBuckets[i] = new AtomicReference<>();
            locks[i] = new ReentrantLock();
        }
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
    public int size() {
        awaitResizeCompletion();

        return size.get();
    }

    @Override
    public boolean isEmpty() {
        awaitResizeCompletion();

        return size.get() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) {
            nullKeyLock.lock();
            try {
                return nullKeyEntry != null;
            } finally {
                nullKeyLock.unlock();
            }
        }

        awaitResizeCompletion();

        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % arrayBuckets.length;

        locks[index].lock();
        try {
            ShakurEntry<K, V> entry = arrayBuckets[index].get();
            while (entry != null) {
                if (entry.key.equals(key)) {
                    return true;
                }
                entry = entry.next;
            }
            return false;
        } finally {
            locks[index].unlock();
        }
    }

    @Override
    public boolean containsValue(Object value) {
        awaitResizeCompletion();

        globalLock.lock();
        try {
            if (nullKeyEntry != null && Objects.equals(nullKeyEntry.value, value)) {
                return true;
            }

            if (value == null) {
                for (AtomicReference<ShakurEntry<K, V>> arrayBucket : arrayBuckets) {
                    ShakurEntry<K, V> entry = arrayBucket.get();
                    while (entry != null) {
                        if (entry.value == null) {
                            return true;
                        }
                        entry = entry.next;
                    }
                }
            } else {
                for (AtomicReference<ShakurEntry<K, V>> arrayBucket : arrayBuckets) {
                    ShakurEntry<K, V> entry = arrayBucket.get();
                    while (entry != null) {
                        if (value.equals(entry.value)) {
                            return true;
                        }
                        entry = entry.next;
                    }
                }
            }
            return false;
        } finally {
            globalLock.unlock();
        }
    }

    @Override
    public V get(Object key) {
        if (key == null) {
            nullKeyLock.lock();
            try {
                return nullKeyEntry == null ? null : nullKeyEntry.value;
            } finally {
                nullKeyLock.unlock();
            }
        }

        awaitResizeCompletion();

        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % arrayBuckets.length;

        locks[index].lock();
        try {
            ShakurEntry<K, V> entry = arrayBuckets[index].get();
            while (entry != null) {
                if (entry.key.equals(key)) {
                    return entry.value;
                }
                entry = entry.next;
            }
        } finally {
            locks[index].unlock();
        }

        return null;
    }

    @Override
    public V put(K key, V value) {
        if (key == null) {
            nullKeyLock.lock();
            try {
                if (nullKeyEntry != null) {
                    V oldValue = nullKeyEntry.value;
                    nullKeyEntry.value = value;
                    return oldValue;
                } else {
                    nullKeyEntry = new ShakurEntry<>(null, value);
                    size.incrementAndGet();
                    return null;
                }
            } finally {
                nullKeyLock.unlock();
            }
        }

        awaitResizeCompletion();

        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % arrayBuckets.length;
        ShakurEntry<K, V> newEntry = new ShakurEntry<>(key, value);

        locks[index].lock();
        try {
            ShakurEntry<K, V> currentEntry = arrayBuckets[index].get();
            while (currentEntry != null) {
                if (currentEntry.key.equals(key)) {
                    V oldValue = currentEntry.value;
                    currentEntry.value = value;
                    return oldValue;
                }
                currentEntry = currentEntry.next;
            }

            newEntry.next = arrayBuckets[index].get();
            arrayBuckets[index].set(newEntry);
            size.incrementAndGet();
        } finally {
            locks[index].unlock();
        }

        if (size.get() > threshold) {
            startResize();
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        try {
            int newCapacity = arrayBuckets.length * 2;
            AtomicReference<ShakurEntry<K, V>>[] newArrayBuckets = new AtomicReference[newCapacity];
            ReentrantLock[] newLocks = new ReentrantLock[newCapacity];

            for (int i = 0; i < newCapacity; i++) {
                newArrayBuckets[i] = new AtomicReference<>();
                newLocks[i] = new ReentrantLock();
            }

            threshold = (int) (newCapacity * LOAD_FACTOR);

            for (AtomicReference<ShakurEntry<K, V>> arrayBucket : arrayBuckets) {
                ShakurEntry<K, V> entry = arrayBucket.get();
                while (entry != null) {
                    ShakurEntry<K, V> nextEntry = entry.next;

                    int hash = entry.key.hashCode();
                    int newIndex = (hash & 0x7FFFFFFF) % newCapacity;

                    ReentrantLock lock = newLocks[newIndex];
                    lock.lock();
                    try {
                        entry.next = newArrayBuckets[newIndex].get();
                        newArrayBuckets[newIndex].set(entry);
                    } finally {
                        lock.unlock();
                    }

                    entry = nextEntry;
                }
            }

            arrayBuckets = newArrayBuckets;
            locks = newLocks;
        } finally {
            resizeLock.lock();
            try {
                isResizing.set(false);
                resizeCondition.signalAll();
            } finally {
                resizeLock.unlock();
            }
        }
    }

    private void startResize() {
        resizeLock.lock();
        try {
            if (!isResizing.get()) {
                isResizing.set(true);
                new Thread(this::resize).start();
            }
        } finally {
            resizeLock.unlock();
        }
    }

    private void awaitResizeCompletion() {
        while (isResizing.get()) {
            try {
                resizeLock.lock();
                try {
                    resizeCondition.await();
                } finally {
                    resizeLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public V remove(Object key) {
        if (key == null) {
            nullKeyLock.lock();
            try {
                if (nullKeyEntry != null) {
                    V oldValue = nullKeyEntry.value;
                    nullKeyEntry = null;
                    size.decrementAndGet();
                    return oldValue;
                }
            } finally {
                nullKeyLock.unlock();
            }
            return null;
        }

        awaitResizeCompletion();

        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % arrayBuckets.length;

        locks[index].lock();
        try {
            AtomicReference<ShakurEntry<K, V>> bucketReference = arrayBuckets[index];
            ShakurEntry<K, V> previous = null;
            ShakurEntry<K, V> current = bucketReference.get();

            while (current != null) {
                if (current.key.equals(key)) {
                    if (previous == null) {
                        bucketReference.set(current.next);
                    } else {
                        previous.next = current.next;
                    }
                    size.decrementAndGet();
                    return current.value;
                }
                previous = current;
                current = current.next;
            }
        } finally {
            locks[index].unlock();
        }

        return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        awaitResizeCompletion();

        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        awaitResizeCompletion();

        globalLock.lock();
        try {
            for (int i = 0; i < arrayBuckets.length; i++) {
                arrayBuckets[i] = new AtomicReference<>();
            }
            nullKeyEntry = null;
            size.set(0);
        } finally {
            globalLock.unlock();
        }
    }

    @Override
    public Set<K> keySet() {
        Set<K> keys = new CopyOnWriteArraySet<>();

        nullKeyLock.lock();
        try {
            if (nullKeyEntry != null) {
                keys.add(null);
            }
        } finally {
            nullKeyLock.unlock();
        }

        awaitResizeCompletion();

        for (int i = 0; i < arrayBuckets.length; i++) {
            locks[i].lock();
            try {
                ShakurEntry<K, V> entry = arrayBuckets[i].get();
                while (entry != null) {
                    keys.add(entry.key);
                    entry = entry.next;
                }
            } finally {
                locks[i].unlock();
            }
        }

        return keys;
    }

    @Override
    public Collection<V> values() {
        Collection<V> values = new ArrayList<>();

        nullKeyLock.lock();
        try {
            if (nullKeyEntry != null) {
                values.add(nullKeyEntry.value);
            }
        } finally {
            nullKeyLock.unlock();
        }

        awaitResizeCompletion();

        for (int i = 0; i < arrayBuckets.length; i++) {
            locks[i].lock();
            try {
                ShakurEntry<K, V> entry = arrayBuckets[i].get();
                while (entry != null) {
                    values.add(entry.value);
                    entry = entry.next;
                }
            } finally {
                locks[i].unlock();
            }
        }

        return values;
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> entries = new CopyOnWriteArraySet<>();

        nullKeyLock.lock();
        try {
            if (nullKeyEntry != null) {
                entries.add(nullKeyEntry);
            }
        } finally {
            nullKeyLock.unlock();
        }

        awaitResizeCompletion();

        for (int i = 0; i < arrayBuckets.length; i++) {
            locks[i].lock();
            try {
                ShakurEntry<K, V> entry = arrayBuckets[i].get();
                while (entry != null) {
                    entries.add(entry);
                    entry = entry.next;
                }
            } finally {
                locks[i].unlock();
            }
        }

        return entries;
    }
}
