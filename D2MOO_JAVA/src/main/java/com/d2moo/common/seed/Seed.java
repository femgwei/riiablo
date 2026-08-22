package com.d2moo.common.seed;

import com.d2moo.common.drlg.D2Seed;

/**
 * 随机数生成工具类
 * 对应 C++ 文件：Seed.cpp
 */
public class Seed {
    
    /** D2Common.0x6FDAEAB0 (#10912). */
    public static void initSeed(D2Seed seed) {
        if (seed == null) {
            return;
        }
        seed.setNLowSeed(1);
        seed.setNHighSeed(666);
    }
    
    /**
     * D2Common.0x6FDAEAC0 (#10913)
     * 使用低32位种子初始化
     */
    public static void initLowSeed(D2Seed seed, int lowSeed) {
        if (seed == null) {
            return;
        }
        seed.setNLowSeed(lowSeed);
        seed.setNHighSeed(666);
    }
    
    /**
     * D2Common.0x6FDAEAD0 (#10914)
     * 获取低32位种子
     */
    public static int getLowSeed(D2Seed seed) {
        if (seed == null) {
            return 0;
        }
        return seed.getNLowSeed();
    }
    
    /**
     * D2Common.0x6FDAEB00 (#10915)
     * 获取高32位种子
     */
    public static int getHighSeed(D2Seed seed) {
        if (seed == null) {
            return 0;
        }
        return seed.getNHighSeed();
    }
    
    /**
     * D2Common.0x6FDAEAE0 (#10921)
     * 设置低32位和高32位种子
     */
    public static void setSeeds(D2Seed seed, int lowSeed, int highSeed) {
        if (seed == null) {
            return;
        }
        seed.setSeeds(lowSeed, highSeed);
    }
    
    /**
     * D2Common.0x6FDAEAF0 (#10922)
     * 获取低32位和高32位种子
     */
    public static void getSeeds(D2Seed seed, int[] lowSeed, int[] highSeed) {
        if (seed == null) {
            return;
        }
        if (lowSeed != null && lowSeed.length > 0) {
            lowSeed[0] = seed.getNLowSeed();
        }
        if (highSeed != null && highSeed.length > 0) {
            highSeed[0] = seed.getNHighSeed();
        }
    }
    
    /**
     * D2Common.0x6FD78E30 (内联函数)
     * 生成下一个随机数
     * 算法：lSeed = nHighSeed + 0x6AC690C5 * nLowSeed
     */
    public static long rollRandomNumber(D2Seed seed) {
        if (seed == null) {
            return 0;
        }
        
        // 使用 long 进行计算以避免溢出
        long multiplier = 0x6AC690C5L;
        long high = Integer.toUnsignedLong(seed.getNHighSeed());
        long low = Integer.toUnsignedLong(seed.getNLowSeed());
        long newSeed = high + multiplier * low;
        
        seed.setLSeed(newSeed);
        
        return newSeed;
    }
    
    /**
     * D2Common.0x6FD7D3E0
     * 生成限制范围内的随机数 [0, nMax)
     */
    public static int rollLimitedRandomNumber(D2Seed seed, int nMax) {
        if (seed == null || nMax <= 0) {
            return 0;
        }
        
        long randomValue = rollRandomNumber(seed);
        
        // 如果 nMax 是 2 的幂，使用位运算优化
        if ((nMax - 1 & nMax) == 0) {
            // nMax 是 2 的幂
            return (int) (randomValue & (nMax - 1));
        } else {
            // nMax 不是 2 的幂，使用模运算
            return (int) Long.remainderUnsigned(randomValue, nMax);
        }
    }
    
    /**
     * 生成百分比随机数 [0, 100)
     */
    public static int rollPercentage(D2Seed seed) {
        if (seed == null) {
            return 0;
        }
        return (int) Long.remainderUnsigned(rollRandomNumber(seed), 100);
    }
    
    /**
     * D2Common.0x6FDAEA80 (#10920)
     * 获取随机值 [0, nValue)
     */
    public static int getRandomValue(D2Seed seed, int nValue) {
        if (seed == null || nValue <= 0) {
            return 0;
        }
        return rollLimitedRandomNumber(seed, nValue);
    }
    
    /**
     * D2Common.0x6FDA5260 (#10916)
     * 返回（可能是重置种子状态，具体实现需要查看源码）
     */
    public static void seedReturn() {
        // TODO: 需要查看 C++ 源码确定具体实现
        // 可能是重置全局种子状态或其他操作
    }
}
