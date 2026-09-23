package com.riiablo.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

public class WinRegistry {
  public static final int HKEY_CURRENT_USER = 0x80000001;

  public static final int HKEY_LOCAL_MACHINE = 0x80000002;

  public static final long REG_SUCCESS = 0;

  private static final int KEY_ALL_ACCESS = 0xf003f;

  private static final int KEY_READ = 0x20019;

  // Do not initialize java.util.prefs.WindowsPreferences here. Java 17 no
  // longer permits the reflective native calls used by this legacy helper,
  // and Preferences.userRoot() itself prints an alarming stack trace before
  // the launcher has a chance to fall back to configured paths.
  private static Preferences userRoot;

  private static Preferences systemRoot;

  private static Class<? extends Preferences> userClass;

  private static Method regOpenKey = null;

  private static Method regCloseKey = null;

  private static Method regQueryValueEx = null;

  private static Method regEnumValue = null;

  private static Method regQueryInfoKey = null;

  private static Method regEnumKeyEx = null;

  private static Method regCreateKeyEx = null;

  private static Method regSetValueEx = null;

  private static Method regDeleteKey = null;

  private static Method regDeleteValue = null;

  private static Pattern REGISTRY_REFERENCE_REGEX = Pattern.compile("\\$\\(Registry:([A-Z_]+)\\\\(.*)@(.*)\\)");

  static {
    // Legacy reflection is intentionally disabled on Java 9+. Reads use the
    // supported reg.exe command below; the old fields remain for source/API
    // compatibility with callers that do not use registry writes.
  }

  public WinRegistry() {
  }

  /**
   * Read a value from key and value name
   *
   * @param hkey      HKEY_CURRENT_USER/HKEY_LOCAL_MACHINE
   * @param key
   * @param valueName
   * @return the value
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws java.lang.reflect.InvocationTargetException
   *
   */
  public static String readString(int hkey, String key, String valueName) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    String value = readStringViaRegExe(hkey, key, valueName);
    if (value != null) return value;
    if (hkey != HKEY_LOCAL_MACHINE && hkey != HKEY_CURRENT_USER) {
      throw new IllegalArgumentException("hkey=" + hkey);
    }
    throw new IllegalStateException("Windows registry query unavailable for " + key);
  }

  /** Reads a registry value without opening java.prefs internals. */
  static String readStringViaRegExe(int hkey, String key, String valueName) {
    if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
    String root;
    if (hkey == HKEY_CURRENT_USER) root = "HKCU";
    else if (hkey == HKEY_LOCAL_MACHINE) root = "HKLM";
    else return null;
    Process process = null;
    try {
      process = new ProcessBuilder("reg", "query", root + "\\" + key, "/v", valueName)
          .redirectErrorStream(true).start();
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return null;
      }
      if (process.exitValue() != 0) return null;
      String output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
      Pattern valuePattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(valueName)
          + "\\s+REG_[^\\s]+\\s+(.*)$");
      java.util.regex.Matcher matcher = valuePattern.matcher(output);
      return matcher.find() ? matcher.group(1).trim() : null;
    } catch (IOException | InterruptedException | RuntimeException ignored) {
      if (process != null) process.destroyForcibly();
      if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
      return null;
    }
  }

  /**
   * Read value(s) and value name(s) form given key
   *
   * @param hkey HKEY_CURRENT_USER/HKEY_LOCAL_MACHINE
   * @param key
   * @return the value name(s) plus the value(s)
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws java.lang.reflect.InvocationTargetException
   *
   */
  public static Map<String, String> readStringValues(int hkey, String key) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    if (hkey == HKEY_LOCAL_MACHINE) {
      return readStringValues(systemRoot, hkey, key);
    } else if (hkey == HKEY_CURRENT_USER) {
      return readStringValues(userRoot, hkey, key);
    } else {
      throw new IllegalArgumentException("hkey=" + hkey);
    }
  }

  /**
   * Read the value name(s) from a given key
   *
   * @param hkey HKEY_CURRENT_USER/HKEY_LOCAL_MACHINE
   * @param key
   * @return the value name(s)
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws java.lang.reflect.InvocationTargetException
   *
   */
  public static List<String> readStringSubKeys(int hkey, String key) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    if (hkey == HKEY_LOCAL_MACHINE) {
      return readStringSubKeys(systemRoot, hkey, key);
    } else if (hkey == HKEY_CURRENT_USER) {
      return readStringSubKeys(userRoot, hkey, key);
    } else {
      throw new IllegalArgumentException("hkey=" + hkey);
    }
  }

  /**
   * Create a key
   *
   * @param hkey HKEY_CURRENT_USER/HKEY_LOCAL_MACHINE
   * @param key
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws java.lang.reflect.InvocationTargetException
   *
   */
  public static void createKey(int hkey, String key) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    int[] ret;
    if (hkey == HKEY_LOCAL_MACHINE) {
      ret = createKey(systemRoot, hkey, key);
      regCloseKey.invoke(systemRoot, new Object[]{new Integer(ret[0])});
    } else if (hkey == HKEY_CURRENT_USER) {
      ret = createKey(userRoot, hkey, key);
      regCloseKey.invoke(userRoot, new Object[]{new Integer(ret[0])});
    } else {
      throw new IllegalArgumentException("hkey=" + hkey);
    }
    if (ret[1] != REG_SUCCESS) {
      throw new IllegalArgumentException("rc=" + ret[1] + "  key=" + key);
    }
  }

  /**
   * Write a value in a given key/value name
   *
   * @param hkey
   * @param key
   * @param valueName
   * @param value
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws java.lang.reflect.InvocationTargetException
   *
   */
  public static void writeStringValue(int hkey, String key, String valueName, String value) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    if (hkey == HKEY_LOCAL_MACHINE) {
      writeStringValue(systemRoot, hkey, key, valueName, value);
    } else if (hkey == HKEY_CURRENT_USER) {
      writeStringValue(userRoot, hkey, key, valueName, value);
    } else {
      throw new IllegalArgumentException("hkey=" + hkey);
    }
  }

  /**
   * Delete a given key
   *
   * @param hkey
   * @param key
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws java.lang.reflect.InvocationTargetException
   *
   */
  public static void deleteKey(int hkey, String key) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    int rc = -1;
    if (hkey == HKEY_LOCAL_MACHINE) {
      rc = deleteKey(systemRoot, hkey, key);
    } else if (hkey == HKEY_CURRENT_USER) {
      rc = deleteKey(userRoot, hkey, key);
    }
    if (rc != REG_SUCCESS) {
      throw new IllegalArgumentException("rc=" + rc + "  key=" + key);
    }
  }

  /**
   * delete a value from a given key/value name
   *
   * @param hkey
   * @param key
   * @param value
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws java.lang.reflect.InvocationTargetException
   *
   */
  public static void deleteValue(int hkey, String key, String value) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    int rc = -1;
    if (hkey == HKEY_LOCAL_MACHINE) {
      rc = deleteValue(systemRoot, hkey, key, value);
    } else if (hkey == HKEY_CURRENT_USER) {
      rc = deleteValue(userRoot, hkey, key, value);
    }
    if (rc != REG_SUCCESS) {
      throw new IllegalArgumentException("rc=" + rc + "  key=" + key + "  value=" + value);
    }
  }

  // =====================

  private static int deleteValue(Preferences root, int hkey, String key, String value) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    int[] handles = (int[]) regOpenKey.invoke(
        root, new Object[]{
            new Integer(hkey), toCstr(key), new Integer(KEY_ALL_ACCESS)
        }
    );
    if (handles[1] != REG_SUCCESS) {
      return handles[1];  // can be REG_NOTFOUND, REG_ACCESSDENIED
    }
    int rc = (
        (Integer) regDeleteValue.invoke(
            root, new Object[]{
                new Integer(handles[0]), toCstr(value)
            }
        )
    ).intValue();
    regCloseKey.invoke(root, new Object[]{new Integer(handles[0])});
    return rc;
  }

  private static int deleteKey(Preferences root, int hkey, String key) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    int rc = (
        (Integer) regDeleteKey.invoke(
            root, new Object[]{new Integer(hkey), toCstr(key)}
        )
    ).intValue();
    return rc;  // can REG_NOTFOUND, REG_ACCESSDENIED, REG_SUCCESS
  }

  private static String readString(Preferences root, int hkey, String key, String value) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    long[] handles = (long[]) regOpenKey.invoke(
        root, new Object[]{
            new Long(hkey), toCstr(key), new Integer(KEY_READ)
        }
    );
    if (handles[1] != REG_SUCCESS) {
      return null;
    }
    byte[] valb = (byte[]) regQueryValueEx.invoke(
        root, new Object[]{new Long(handles[0]), toCstr(value)}
    );
    regCloseKey.invoke(root, new Object[]{new Long(handles[0])});
    return (valb != null ? new String(valb).trim() : null);
  }

  private static Map<String, String> readStringValues(Preferences root, int hkey, String key) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    HashMap<String, String> results = new HashMap<String, String>();
    long[] handles = (long[]) regOpenKey.invoke(
        root, new Object[]{
            new Long(hkey), toCstr(key), new Integer(KEY_READ)
        }
    );
    if (handles[1] != REG_SUCCESS) {
      return null;
    }
    int[] info = (int[]) regQueryInfoKey.invoke(root, new Object[]{new Long(handles[0])});

    int count = info[2]; // count
    int maxlen = info[3]; // value length max
    for (int index = 0; index < count; index++) {
      byte[] name = (byte[]) regEnumValue.invoke(
          root, new Object[]{
              new Long(handles[0]), new Integer(index), new Integer(maxlen + 1)
          }
      );
      String value = readString(hkey, key, new String(name));
      results.put(new String(name).trim(), value);
    }
    regCloseKey.invoke(root, new Object[]{new Long(handles[0])});
    return results;
  }

  private static List<String> readStringSubKeys(Preferences root, int hkey, String key) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    List<String> results = new ArrayList<String>();
    long[] handles = (long[]) regOpenKey.invoke(
        root, new Object[]{
            new Long(hkey), toCstr(key), new Integer(KEY_READ)
        }
    );
    if (handles[1] != REG_SUCCESS) {
      return null;
    }
    int[] info = (int[]) regQueryInfoKey.invoke(root, new Object[]{new Long(handles[0])});

    int count = info[0]; // count
    int maxlen = info[3]; // value length max
    for (int index = 0; index < count; index++) {
      byte[] name = (byte[]) regEnumKeyEx.invoke(
          root, new Object[]{
              new Long(handles[0]), new Integer(index), new Integer(maxlen + 1)
          }
      );
      results.add(new String(name).trim());
    }
    regCloseKey.invoke(root, new Object[]{new Long(handles[0])});
    return results;
  }

  private static int[] createKey(Preferences root, int hkey, String key) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    return (int[]) regCreateKeyEx.invoke(root, new Object[]{new Long(hkey), toCstr(key)});
  }

  private static void writeStringValue(
      Preferences root, int hkey, String key, String valueName, String value) throws
      IllegalArgumentException,
      IllegalAccessException,
      InvocationTargetException {
    long[] handles = (long[]) regOpenKey.invoke(
        root, new Object[]{
            new Long(hkey), toCstr(key), new Integer(KEY_ALL_ACCESS)
        }
    );

    regSetValueEx.invoke(root, new Object[]{new Long(handles[0]), toCstr(valueName), toCstr(value)});
    regCloseKey.invoke(root, new Object[]{new Long(handles[0])});
  }

  // utility
  private static byte[] toCstr(String str) {
    byte[] result = new byte[str.length() + 1];

    for (int i = 0; i < str.length(); i++) {
      result[i] = (byte) str.charAt(i);
    }
    result[str.length()] = 0;
    return result;
  }
}
