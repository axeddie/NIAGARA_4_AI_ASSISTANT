/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import javax.baja.file.BIFile;
import javax.baja.io.ValueDocDecoder;
import javax.baja.io.ValueDocEncoder;
import javax.baja.sys.BComponent;
import javax.baja.sys.BFacets;
import javax.baja.sys.BLink;
import javax.baja.sys.BValue;
import javax.baja.sys.Property;

/**
 * Multi-level full-snapshot Undo manager for Eddie's Workbench AI Assistant.
 * Captures pre-modification BOG XML component snapshots, link snapshots, property values,
 * and file byte arrays so any station or file modification can be reverted cleanly.
 */
public final class StationUndoManager
{
  private static final int MAX_UNDO_BATCHES = 20;
  private static final Deque<UndoBatch> UNDO_HISTORY = new ArrayDeque<UndoBatch>();

  public static final class UndoBatch
  {
    public final String description;
    public final String timestamp;
    public final List<Runnable> operations;

    public UndoBatch(String description, List<Runnable> operations)
    {
      this.description = (description != null && !description.trim().isEmpty())
        ? description.trim()
        : "Station/File Modification";
      this.timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
      this.operations = new ArrayList<Runnable>(operations);
    }
  }

  private StationUndoManager()
  {
  }

  public static void pushBatch(String description, List<Runnable> undoOps)
  {
    if (undoOps == null || undoOps.isEmpty())
    {
      return;
    }
    synchronized (UNDO_HISTORY)
    {
      while (UNDO_HISTORY.size() >= MAX_UNDO_BATCHES)
      {
        UNDO_HISTORY.removeLast();
      }
      UNDO_HISTORY.addFirst(new UndoBatch(description, undoOps));
    }
  }

  public static boolean hasUndo()
  {
    synchronized (UNDO_HISTORY)
    {
      return !UNDO_HISTORY.isEmpty();
    }
  }

  public static int getUndoCount()
  {
    synchronized (UNDO_HISTORY)
    {
      return UNDO_HISTORY.size();
    }
  }

  public static String getLatestUndoSummary()
  {
    synchronized (UNDO_HISTORY)
    {
      if (UNDO_HISTORY.isEmpty())
      {
        return "Nothing to undo";
      }
      UndoBatch top = UNDO_HISTORY.peekFirst();
      return "[" + top.timestamp + "] " + top.description + " (" + UNDO_HISTORY.size() + " in history)";
    }
  }

  public static String undoLastBatch()
  {
    UndoBatch batch;
    int remaining;
    synchronized (UNDO_HISTORY)
    {
      if (UNDO_HISTORY.isEmpty())
      {
        return "Nothing to undo.";
      }
      batch = UNDO_HISTORY.removeFirst();
      remaining = UNDO_HISTORY.size();
    }

    int count = 0;
    int errors = 0;
    for (int i = batch.operations.size() - 1; i >= 0; i--)
    {
      try
      {
        batch.operations.get(i).run();
        count++;
      }
      catch (Throwable t)
      {
        errors++;
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Undid [").append(batch.timestamp).append("] ").append(batch.description)
      .append(" — reverted ").append(count).append(" step(s)");
    if (errors > 0)
    {
      sb.append(" (").append(errors).append(" warning(s))");
    }
    sb.append(". (").append(remaining).append(" undo state(s) remaining).");
    return sb.toString();
  }

  /**
   * Serializes a BComponent (and all its child components, properties, facets, flags, and links)
   * into an in-memory BOG XML string using ValueDocEncoder.
   */
  public static String encodeComponentToBogXml(BComponent comp) throws Exception
  {
    if (comp == null)
    {
      throw new IllegalArgumentException("Cannot encode null component to BOG XML.");
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
    ValueDocEncoder encoder = new ValueDocEncoder(baos);
    try
    {
      encoder.setEncodeTransients(false);
      encoder.encodeDocument(comp.newCopy());
      encoder.flush();
    }
    finally
    {
      try { encoder.close(); } catch (Throwable ignore) {}
    }
    String bogXml = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    return bogXml.replace("n=\"pointId\" v=\"slot:/", "n=\"pointId\" v=\"station:|slot:/");
  }

  /**
   * Decodes a BComponent from a BOG XML string created by encodeComponentToBogXml.
   */
  public static BComponent decodeComponentFromBogXml(String bogXml) throws Exception
  {
    if (bogXml == null || bogXml.trim().isEmpty())
    {
      return null;
    }
    ValueDocDecoder decoder = new ValueDocDecoder(
      new ByteArrayInputStream(bogXml.getBytes(StandardCharsets.UTF_8)));
    try
    {
      BValue val = decoder.decodeDocument();
      if (val instanceof BComponent)
      {
        return (BComponent) val;
      }
      return null;
    }
    finally
    {
      try { decoder.close(); } catch (Throwable ignore) {}
    }
  }

  /**
   * Creates an Undo Runnable that restores a deleted or replaced child component (including its
   * full BOG subtree, flags, facets, and incoming/outgoing links on sibling components).
   */
  public static Runnable createChildComponentRestoreUndo(final BComponent parent, final BComponent childToBackup)
  {
    if (parent == null || childToBackup == null)
    {
      return null;
    }
    try
    {
      final String childName = childToBackup.getName();
      Property propInParent = parent.getProperty(childName);
      final int savedFlags = (propInParent != null) ? parent.getFlags(propInParent) : 0;
      final BFacets savedFacets = (propInParent != null) ? parent.getSlotFacets(propInParent) : BFacets.NULL;
      final String bogSnapshot = encodeComponentToBogXml(childToBackup);

      return new Runnable()
      {
        @Override
        public void run()
        {
          try
          {
            BComponent restored = decodeComponentFromBogXml(bogSnapshot);
            if (restored == null)
            {
              restored = (BComponent) childToBackup.newCopy();
            }
            if (parent.get(childName) != null)
            {
              parent.remove(childName);
            }
            parent.add(childName, restored.newCopy(), savedFlags, savedFacets, null);
          }
          catch (Throwable ignore)
          {
          }
        }
      };
    }
    catch (Throwable t)
    {
      final String childName = childToBackup.getName();
      final BValue copyFallback = childToBackup.newCopy();
      return new Runnable()
      {
        @Override
        public void run()
        {
          try
          {
            if (parent.get(childName) != null)
            {
              parent.remove(childName);
            }
            parent.add(childName, copyFallback.newCopy());
          }
          catch (Throwable ignore)
          {
          }
        }
      };
    }
  }

  /**
   * Creates an Undo Runnable that restores a removed BLink on a target component.
   */
  public static Runnable createLinkRestoreUndo(final BComponent target, final BLink removedLink)
  {
    if (target == null || removedLink == null)
    {
      return null;
    }
    final BLink linkCopy = (BLink) removedLink.newCopy();
    return new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          target.add(null, (BLink) linkCopy.newCopy());
        }
        catch (Throwable ignore)
        {
        }
      }
    };
  }

  /**
   * Creates an Undo Runnable that restores a BIFile's previous bytes (or deletes it if newly created).
   */
  public static Runnable createBajaFileRestoreUndo(
    final BIFile file,
    final boolean existedBefore,
    final byte[] previousBytes)
  {
    if (file == null)
    {
      return null;
    }
    final byte[] snapshot = (previousBytes != null) ? previousBytes.clone() : null;
    return new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          if (!existedBefore)
          {
            file.delete();
          }
          else if (snapshot != null)
          {
            file.write(snapshot);
          }
        }
        catch (Throwable ignore)
        {
        }
      }
    };
  }

  /**
   * Creates an Undo Runnable that restores a local java.io.File's previous bytes (or deletes it if newly created).
   */
  public static Runnable createLocalFileRestoreUndo(
    final File localFile,
    final boolean existedBefore,
    final byte[] previousBytes)
  {
    if (localFile == null)
    {
      return null;
    }
    final byte[] snapshot = (previousBytes != null) ? previousBytes.clone() : null;
    return new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          if (!existedBefore)
          {
            localFile.delete();
          }
          else if (snapshot != null)
          {
            FileOutputStream fos = new FileOutputStream(localFile);
            try
            {
              fos.write(snapshot);
              fos.flush();
            }
            finally
            {
              try { fos.close(); } catch (Throwable ignore) {}
            }
          }
        }
        catch (Throwable ignore)
        {
        }
      }
    };
  }
}
