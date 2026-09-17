package com.d2moo.common.drlg;

/** Receives one native outdoor DirtPathGrid tile in level-local coordinates. */
@FunctionalInterface
public interface DrlgDirtPathExporter {
    void onDirtPath(int levelId, int tileX, int tileY);
}
