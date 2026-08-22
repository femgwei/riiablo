package com.d2moo.common.util;

/**
 * 内存池工具类
 * 提供静态方法用于内存池分配和释放
 * 对应 C++ 的 D2_CALLOC_POOL, D2_CALLOC_STRC_POOL, D2_FREE_POOL
 */
public class D2Pool {
    
    /**
     * 分配内存并初始化为零（对应 D2_CALLOC_POOL）
     * @param memPool 内存池对象
     * @param size 分配大小（字节）
     * @return 分配的字节数组
     */
    public static byte[] callocPool(Object memPool, int size) {
        if (memPool instanceof D2MemoryPool) {
            return ((D2MemoryPool) memPool).allocPool(size);
        }
        
        // 如果没有提供内存池，使用 Java 数组
        D2Log.warning("Memory pool not provided, using Java array allocation");
        return new byte[size];
    }
    
    /**
     * 分配 int 数组（对应 D2_CALLOC_POOL 用于 int 数组）
     * @param memPool 内存池对象
     * @param length 数组长度
     * @return 分配的 int 数组
     */
    public static int[] callocIntArrayPool(Object memPool, int length) {
        if (memPool instanceof D2MemoryPool) {
            D2MemoryPool pool = (D2MemoryPool) memPool;
            int[] arr = new int[length];
            // 记录分配
            int id = System.identityHashCode(arr);
            pool.recordAllocation(id, arr, length * 4);
            return arr;
        }
        
        D2Log.warning("Memory pool not provided, using Java array allocation");
        return new int[length];
    }
    
    /**
     * 分配结构体内存并初始化为零（对应 D2_CALLOC_STRC_POOL）
     * @param memPool 内存池对象
     * @param clazz 要分配的类型
     * @return 分配的对象实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T callocStrcPool(Object memPool, Class<T> clazz) {
        if (memPool instanceof D2MemoryPool) {
            return ((D2MemoryPool) memPool).allocStrcPool(clazz);
        }
        
        // 如果没有提供内存池，直接创建实例
        D2Log.warning("Memory pool not provided, using direct instantiation");
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            D2Log.warning("Failed to allocate structure: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 释放内存（对应 D2_FREE_POOL）
     * @param memPool 内存池对象
     * @param obj 要释放的对象
     */
    public static void freePool(Object memPool, Object obj) {
        if (memPool instanceof D2MemoryPool) {
            ((D2MemoryPool) memPool).freePool(obj);
        }
        // Java 中对象会被垃圾回收器自动回收
    }
    
    /**
     * 分配数组（对应 D2_CALLOC_POOL 用于数组）
     * @param memPool 内存池对象
     * @param clazz 数组元素类型
     * @param length 数组长度
     * @return 分配的数组
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] callocArrayPool(Object memPool, Class<T> clazz, int length) {
        if (memPool instanceof D2MemoryPool) {
            D2MemoryPool pool = (D2MemoryPool) memPool;
            T[] arr = (T[]) java.lang.reflect.Array.newInstance(clazz, length);
            // 记录分配
            int id = System.identityHashCode(arr);
            int elementSize = getElementSize(clazz);
            int totalSize = elementSize * length;
            pool.recordAllocation(id, arr, totalSize);
            return arr;
        }
        
        // 如果没有提供内存池，使用 Java 数组
        D2Log.warning("Memory pool not provided, using Java array allocation");
        return (T[]) java.lang.reflect.Array.newInstance(clazz, length);
    }
    
    /**
     * 重新分配数组（对应 D2_REALLOC_POOL）
     * @param memPool 内存池对象
     * @param oldArray 旧数组
     * @param clazz 数组元素类型
     * @param newLength 新数组长度
     * @return 重新分配的数组
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] reallocArrayPool(Object memPool, T[] oldArray, Class<T> clazz, int newLength) {
        if (oldArray == null) {
            return callocArrayPool(memPool, clazz, newLength);
        }
        
        T[] newArray = callocArrayPool(memPool, clazz, newLength);
        if (newArray != null && oldArray.length > 0) {
            int copyLength = Math.min(oldArray.length, newLength);
            System.arraycopy(oldArray, 0, newArray, 0, copyLength);
        }
        
        // 释放旧数组
        if (memPool instanceof D2MemoryPool) {
            ((D2MemoryPool) memPool).freePool(oldArray);
        }
        
        return newArray;
    }
    
    /**
     * 获取基本类型的大小
     */
    private static int getElementSize(Class<?> clazz) {
        if (clazz == byte.class || clazz == Byte.class) return 1;
        if (clazz == short.class || clazz == Short.class) return 2;
        if (clazz == int.class || clazz == Integer.class) return 4;
        if (clazz == long.class || clazz == Long.class) return 8;
        if (clazz == float.class || clazz == Float.class) return 4;
        if (clazz == double.class || clazz == Double.class) return 8;
        if (clazz == boolean.class || clazz == Boolean.class) return 1;
        return 8; // 对象引用大小
    }
}
