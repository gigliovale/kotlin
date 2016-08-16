/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.cli.common.jarvfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public abstract class ArchiveHandler {
  public static final long DEFAULT_LENGTH = 0L;
  public static final long DEFAULT_TIMESTAMP = -1L;

  protected static class EntryInfo {
    public final EntryInfo parent;
    public final CharSequence shortName;
    public final boolean isDirectory;
    public final long length;

    /** @deprecated use {@link EntryInfo#EntryInfo(CharSequence, boolean, long, long, EntryInfo)} instead (to be removed in IDEA 16) */
    @SuppressWarnings("unused")
    public EntryInfo(EntryInfo parent, @NotNull String shortName, boolean isDirectory, long length, long timestamp) {
      this(shortName, isDirectory, length, timestamp, parent);
    }

    public EntryInfo(@NotNull CharSequence shortName, boolean isDirectory, long length, long timestamp, @Nullable EntryInfo parent) {
      this.parent = parent;
      this.shortName = shortName;
      this.isDirectory = isDirectory;
      this.length = length;
    }
  }

  private final File myPath;
  private final Object myLock = new Object();
  private volatile Reference<Map<String, EntryInfo>> myEntries = new SoftReference<Map<String, EntryInfo>>(null);
  private boolean myCorrupted;

  protected ArchiveHandler(@NotNull String path) {
    myPath = new File(path);
  }

  @NotNull
  public File getFile() {
    return myPath;
  }

  @NotNull
  public String[] list(@NotNull String relativePath) {
    EntryInfo entry = getEntryInfo(relativePath);
    if (entry == null || !entry.isDirectory) return ArrayUtil.EMPTY_STRING_ARRAY;

    Set<String> names = new THashSet<String>();
    for (EntryInfo info : getEntriesMap().values()) {
      if (info.parent == entry) {
        names.add(info.shortName.toString());
      }
    }
    return ArrayUtil.toStringArray(names);
  }

  public void dispose() {
    myEntries.clear();
  }

  @Nullable
  protected EntryInfo getEntryInfo(@NotNull String relativePath) {
    if (relativePath.isEmpty()) {
      return createRootEntry();
    }

    return getEntriesMap().get(relativePath);
  }

  @NotNull
  protected Map<String, EntryInfo> getEntriesMap() {
    Map<String, EntryInfo> map = SoftReference.dereference(myEntries);
    if (map == null) {
      synchronized (myLock) {
        map = SoftReference.dereference(myEntries);

        if (map == null) {
          if (myCorrupted) {
            map = Collections.emptyMap();
          }
          else {
            try {
              map = createEntriesMap();
            }
            catch (Exception e) {
              myCorrupted = true;
              Logger.getInstance(getClass()).warn(e.getMessage() + ": " + myPath, e);
              map = Collections.emptyMap();
            }
          }

          myEntries = new SoftReference<Map<String, EntryInfo>>(map);
        }
      }
    }
    return map;
  }

  @NotNull
  protected abstract Map<String, EntryInfo> createEntriesMap() throws IOException;

  @NotNull
  protected EntryInfo createRootEntry() {
    return new EntryInfo("", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, null);
  }

  @NotNull
  protected Pair<String, String> splitPath(@NotNull String entryName) {
    int p = entryName.lastIndexOf('/');
    String parentName = p > 0 ? entryName.substring(0, p) : "";
    String shortName = p > 0 ? entryName.substring(p + 1) : entryName;
    return Pair.create(parentName, shortName);
  }

  @NotNull
  public abstract byte[] contentsToByteArray(@NotNull String relativePath) throws IOException;
}
