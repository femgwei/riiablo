package com.riiablo.map;

import com.riiablo.codec.excel.MonStats;

/** Entity creation boundary consumed by the map population assembly adapter. */
public interface MapPopulationPort {
  default boolean hasClientPresentation() { return false; }

  int createObject(Map.Preset preset, DS1.Object object, float x, float y);
  int createObject(int act, int type, int id, float x, float y);
  int createStaticObjectByClassId(int objectId, float x, float y);
  int createMonster(MonStats.Entry monstats, float x, float y);
  int createMonster(int monsterId, float x, float y);
  int createWarp(Map.Zone sourceZone, int index, float x, float y);
  int createWarp(int index, float x, float y);
}

