package com.d2moo.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存池管理系统
 * 对应 C++ 的 FOG_AllocPool 和 FOG_FreePool
 * 用于替代 C++ 的 D2_CALLOC_POOL 和 D2_FREE_POOL
 * 
 * 重要说明：
 * - 在 Java 中，由于没有指针，无法实现真正的内存池（预分配大块内存后直接返回）
 * - 当前实现每次分配都会创建新对象（new byte[size]），这是正常的 Java 行为
 * - 内存池主要用于：
 *   1. 追踪内存分配和释放
 *   2. 统计内存使用情况
 *   3. 调试内存泄漏
 * - 实际的内存管理仍由 Java GC 负责
 * 
 * 如果需要减少 GC 压力，可以考虑：
 * 1. 使用对象池（Object Pool）重用对象
 * 2. 使用 DirectByteBuffer 分配直接内存（需要手动管理）
 * 3. 优化分配策略，减少小对象分配
 */
public class D2MemoryPool {
    // 存储分配的对象，key 是实际数据对象的 identityHashCode
    private Map<Integer, PooledObject> allocatedObjects;
    
    // 预分配的内存池（可选）
    private List<MemoryChunk> memoryChunks;
    private int chunkSize;  // 每个内存块的大小
    private int currentChunkIndex;  // 当前使用的内存块索引
    private int currentOffset;  // 当前块中的偏移量
    
    // 统计信息
    private long totalAllocated;
    private long totalFreed;
    private int currentAllocations;
    
    // 是否启用预分配池（默认 false，使用直接分配）
    private boolean usePreAllocatedPool;
    
    /**
     * 构造函数 - 使用直接分配模式（每次 new）
     */
    public D2MemoryPool() {
        this(false, 0, 0);
    }
    
    /**
     * 构造函数 - 使用预分配内存池模式
     * @param usePool 是否使用预分配池
     * @param chunkSize 每个内存块的大小（字节）
     * @param initialChunks 初始内存块数量
     */
    public D2MemoryPool(boolean usePool, int chunkSize, int initialChunks) {
        this.allocatedObjects = new HashMap<>();
        this.memoryChunks = new ArrayList<>();
        this.chunkSize = chunkSize > 0 ? chunkSize : 64 * 1024; // 默认 64KB
        this.currentChunkIndex = 0;
        this.currentOffset = 0;
        this.usePreAllocatedPool = usePool;
        this.totalAllocated = 0;
        this.totalFreed = 0;
        this.currentAllocations = 0;
        
        if (usePool && initialChunks > 0) {
            for (int i = 0; i < initialChunks; i++) {
                allocateNewChunk();
            }
        }
    }
    
    /**
     * 分配新的内存块
     */
    private void allocateNewChunk() {
        byte[] chunk = new byte[chunkSize];
        memoryChunks.add(new MemoryChunk(chunk, 0));
    }
    
    /**
     * 分配内存并初始化为零（对应 D2_CALLOC_POOL）
     * @param size 分配大小（字节）
     * @return 分配的字节数组
     */
    public byte[] allocPool(int size) {
        if (size <= 0) {
            return null;
        }
        
        byte[] data;
        
        if (usePreAllocatedPool && size <= chunkSize) {
            // 从预分配池中分配
            data = allocFromPool(size);
        } else {
            // 直接分配（对于大块内存或未启用池模式）
            data = new byte[size];
        }
        
        // Java 数组默认初始化为 0，所以不需要 memset
        
        // 使用数据对象本身的 identityHashCode 作为 key
        int id = System.identityHashCode(data);
        allocatedObjects.put(id, new PooledObject(data, size));
        
        totalAllocated += size;
        currentAllocations++;
        
        return data;
    }
    
    /**
     * 从预分配池中分配内存
     * 
     * 注意：由于 Java 没有指针，无法直接返回池中内存的"视图"。
     * 当前实现仍然会创建新的 byte[] 数组并复制数据，这实际上并没有减少内存分配。
     * 
     * 真正的内存池在 Java 中需要使用：
     * 1. ByteBuffer（可以创建 slice() 视图，但需要手动管理）
     * 2. DirectByteBuffer（直接内存，但需要手动释放）
     * 3. Unsafe API（不推荐，不安全）
     * 
     * 当前实现主要用于：
     * - 追踪内存分配和释放
     * - 统计内存使用情况
     * - 调试内存泄漏
     * 
     * 实际的内存管理仍由 Java GC 负责。
     */
    private byte[] allocFromPool(int size) {
        // 注意：即使从池中分配，仍然需要创建新数组
        // 因为 Java 无法直接返回池中内存的"切片"
        byte[] result = new byte[size];
        
        // 尝试从当前块复制数据（虽然这并不能减少分配）
        if (currentChunkIndex < memoryChunks.size()) {
            MemoryChunk currentChunk = memoryChunks.get(currentChunkIndex);
            if (currentChunk.offset + size <= chunkSize) {
                System.arraycopy(currentChunk.data, currentChunk.offset, result, 0, size);
                currentChunk.offset += size;
                currentOffset = currentChunk.offset;
                return result;
            }
        }
        
        // 当前块空间不足，尝试下一个块
        currentChunkIndex++;
        if (currentChunkIndex >= memoryChunks.size()) {
            allocateNewChunk();
        }
        
        // 从新块复制数据
        MemoryChunk chunk = memoryChunks.get(currentChunkIndex);
        System.arraycopy(chunk.data, 0, result, 0, size);
        chunk.offset = size;
        currentOffset = size;
        
        return result;
    }
    
    /**
     * 分配结构体内存并初始化为零（对应 D2_CALLOC_STRC_POOL）
     * @param clazz 要分配的类型
     * @return 分配的对象实例
     */
    @SuppressWarnings("unchecked")
    public <T> T allocStrcPool(Class<T> clazz) {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            
            // 记录分配
            int id = System.identityHashCode(instance);
            int size = estimateSize(instance);
            allocatedObjects.put(id, new PooledObject(instance, size));
            
            totalAllocated += size;
            currentAllocations++;
            
            return instance;
        } catch (Exception e) {
            D2Log.warning("Failed to allocate structure pool: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 释放内存（对应 D2_FREE_POOL）
     * @param obj 要释放的对象（可以是 byte[]、数组或任何对象）
     */
    public void freePool(Object obj) {
        if (obj == null) {
            return;
        }
        
        int id = System.identityHashCode(obj);
        PooledObject pooled = allocatedObjects.remove(id);
        
        if (pooled != null) {
            totalFreed += pooled.size;
            currentAllocations--;
        } else {
            // 如果没有找到，可能是数组元素或其他情况
            // 尝试查找数组元素
            if (obj instanceof int[]) {
                // 对于 int[]，尝试查找整个数组
                int[] arr = (int[]) obj;
                for (Map.Entry<Integer, PooledObject> entry : allocatedObjects.entrySet()) {
                    if (entry.getValue().getData() == obj) {
                        allocatedObjects.remove(entry.getKey());
                        totalFreed += entry.getValue().size;
                        currentAllocations--;
                        return;
                    }
                }
            }
        }
        
        // Java 中对象会被垃圾回收器自动回收，这里只是记录统计信息
    }
    
    /**
     * 估算对象大小（粗略估算）
     */
    private int estimateSize(Object obj) {
        if (obj == null) {
            return 0;
        }
        
        // 基本类型大小估算
        if (obj instanceof Byte) return 1;
        if (obj instanceof Short) return 2;
        if (obj instanceof Integer) return 4;
        if (obj instanceof Long) return 8;
        if (obj instanceof Float) return 4;
        if (obj instanceof Double) return 8;
        if (obj instanceof Boolean) return 1;
        
        // 数组大小估算
        if (obj instanceof byte[]) return ((byte[]) obj).length;
        if (obj instanceof int[]) return ((int[]) obj).length * 4;
        if (obj instanceof long[]) return ((long[]) obj).length * 8;
        if (obj instanceof Object[]) return ((Object[]) obj).length * 8; // 引用大小
        
        // 默认对象大小估算（包括对象头）
        return 16; // 最小对象大小
    }
    
    /**
     * 获取统计信息
     */
    public PoolStatistics getStatistics() {
        return new PoolStatistics(totalAllocated, totalFreed, currentAllocations);
    }
    
    /**
     * 清空所有分配记录（用于测试或重置）
     */
    public void clear() {
        allocatedObjects.clear();
        totalAllocated = 0;
        totalFreed = 0;
        currentAllocations = 0;
    }
    
    /**
     * 记录分配（包内访问，用于 D2Pool 类）
     */
    void recordAllocation(int id, Object obj, int size) {
        allocatedObjects.put(id, new PooledObject(obj, size));
        totalAllocated += size;
        currentAllocations++;
    }
    
    /**
     * 内存块内部类
     */
    private static class MemoryChunk {
        final byte[] data;
        int offset;
        
        MemoryChunk(byte[] data, int offset) {
            this.data = data;
            this.offset = offset;
        }
    }
    
    /**
     * 获取内存池使用情况
     */
    public PoolUsageInfo getPoolUsageInfo() {
        if (!usePreAllocatedPool) {
            return null;
        }
        
        int totalChunks = memoryChunks.size();
        int usedChunks = currentChunkIndex + 1;
        long totalPoolSize = (long) totalChunks * chunkSize;
        long usedPoolSize = (long) (usedChunks - 1) * chunkSize + currentOffset;
        
        return new PoolUsageInfo(totalChunks, usedChunks, totalPoolSize, usedPoolSize, chunkSize);
    }
    
    /**
     * 内存池使用信息
     */
    public static class PoolUsageInfo {
        private final int totalChunks;
        private final int usedChunks;
        private final long totalPoolSize;
        private final long usedPoolSize;
        private final int chunkSize;
        
        public PoolUsageInfo(int totalChunks, int usedChunks, long totalPoolSize, 
                            long usedPoolSize, int chunkSize) {
            this.totalChunks = totalChunks;
            this.usedChunks = usedChunks;
            this.totalPoolSize = totalPoolSize;
            this.usedPoolSize = usedPoolSize;
            this.chunkSize = chunkSize;
        }
        
        public int getTotalChunks() { return totalChunks; }
        public int getUsedChunks() { return usedChunks; }
        public long getTotalPoolSize() { return totalPoolSize; }
        public long getUsedPoolSize() { return usedPoolSize; }
        public int getChunkSize() { return chunkSize; }
        public double getUsageRatio() {
            return totalPoolSize > 0 ? (double) usedPoolSize / totalPoolSize : 0.0;
        }
    }
    
    /**
     * 池化对象包装类
     */
    public static class PooledObject {
        private final Object data;
        private final int size;
        
        public PooledObject(Object data, int size) {
            this.data = data;
            this.size = size;
        }
        
        public Object getData() {
            return data;
        }
        
        public int getSize() {
            return size;
        }
    }
    
    /**
     * 统计信息类
     */
    public static class PoolStatistics {
        private final long totalAllocated;
        private final long totalFreed;
        private final int currentAllocations;
        
        public PoolStatistics(long totalAllocated, long totalFreed, int currentAllocations) {
            this.totalAllocated = totalAllocated;
            this.totalFreed = totalFreed;
            this.currentAllocations = currentAllocations;
        }
        
        public long getTotalAllocated() {
            return totalAllocated;
        }
        
        public long getTotalFreed() {
            return totalFreed;
        }
        
        public int getCurrentAllocations() {
            return currentAllocations;
        }
        
        public long getNetAllocated() {
            return totalAllocated - totalFreed;
        }
    }
}
