/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.baja.collection.BITable;
import javax.baja.collection.TableCursor;
import javax.baja.file.BIFile;
import javax.baja.file.BFileSpace;
import javax.baja.file.BFileSystem;
import javax.baja.file.BSubSpaceFile;
import javax.baja.file.FilePath;
import javax.baja.io.ValueDocDecoder;
import javax.baja.naming.BOrd;
import javax.baja.status.BStatusBoolean;
import javax.baja.status.BStatusEnum;
import javax.baja.status.BStatusNumeric;
import javax.baja.status.BStatusString;
import javax.baja.sys.Action;
import javax.baja.sys.BComplex;
import javax.baja.sys.BComponent;
import javax.baja.sys.BDynamicEnum;
import javax.baja.sys.BEnum;
import javax.baja.sys.BEnumRange;
import javax.baja.sys.BFacets;
import javax.baja.sys.BIObject;
import javax.baja.sys.BLink;
import javax.baja.sys.BObject;
import javax.baja.sys.BSimple;
import javax.baja.sys.BStruct;
import javax.baja.sys.BValue;
import javax.baja.sys.Flags;
import javax.baja.sys.Property;
import javax.baja.sys.Slot;
import javax.baja.sys.SlotCursor;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.tag.Id;
import javax.baja.tag.Tag;
import javax.baja.ui.BWidget;
import javax.baja.util.BFolder;
import javax.baja.util.BTypeSpec;
import javax.baja.util.BWsAnnotation;
import javax.baja.workbench.BWbShell;
import javax.baja.workbench.view.BWbView;

/**
 * Universal Station & Workbench File Modification Engine for Eddie's Workbench AI Assistant.
 * Supports full station manipulation (components, deep properties, dynamic slots, extensions,
 * facets, flags, tags/relations, links, actions with parameters, BProgram code, BOG import,
 * BQL mass batch actions, and WireSheet layout) as well as Station/Local file creation,
 * editing, patching, copying, renaming, and deletion — all backed by:
 *   1. The 3-option Guardrail Dialog ("Cancel" | "Make Duplicate Only" | "Proceed")
 *   2. Multi-level full-snapshot Undo (StationUndoManager)
 */
public final class StationActionExecutor
{
  private StationActionExecutor()
  {
  }

  public static boolean hasUndo()
  {
    return StationUndoManager.hasUndo();
  }

  public static String getLatestUndoSummary()
  {
    return StationUndoManager.getLatestUndoSummary();
  }

  public static String undoLastBatch()
  {
    return StationUndoManager.undoLastBatch();
  }

  /**
   * Extracts a list of action maps from an AI response string.
   * Supports <station-actions>[...]</station-actions>, ```json [...] ```, or a raw JSON array.
   */
  public static List<Map<String, String>> extractActionPlan(String responseText)
  {
    List<Map<String, String>> empty = new ArrayList<Map<String, String>>();
    if (responseText == null || responseText.trim().isEmpty())
    {
      return empty;
    }

    String jsonCandidate = null;

    // 1. Check for <station-actions>...</station-actions> tag
    int tagStart = responseText.indexOf("<station-actions>");
    int tagEnd = responseText.indexOf("</station-actions>");
    if (tagStart >= 0 && tagEnd > tagStart)
    {
      String inside = responseText.substring(tagStart + "<station-actions>".length(), tagEnd).trim();
      jsonCandidate = extractJsonArray(inside);
    }

    // 2. Otherwise look for a JSON array containing "action" keys (ignoring <station-inspect>)
    if (jsonCandidate == null && !responseText.contains("<station-inspect>"))
    {
      String candidate = extractJsonArray(responseText);
      if (candidate != null && candidate.contains("\"action\""))
      {
        jsonCandidate = candidate;
      }
    }

    if (jsonCandidate == null || jsonCandidate.isEmpty())
    {
      return empty;
    }

    return parseRawJsonActionList(jsonCandidate);
  }

  public static List<Map<String, String>> parseRawJsonActionList(String rawJson)
  {
    List<Map<String, String>> empty = new ArrayList<Map<String, String>>();
    String arr = extractJsonArray(rawJson);
    if (arr == null || arr.isEmpty()) return empty;
    try
    {
      List<Map<String, String>> parsed = parseActionPlan(arr);
      List<Map<String, String>> valid = new ArrayList<Map<String, String>>();
      for (int i = 0; i < parsed.size(); i++)
      {
        Map<String, String> item = parsed.get(i);
        if ((item.get("action") != null && !item.get("action").trim().isEmpty())
          || (item.get("inspect") != null && !item.get("inspect").trim().isEmpty())
          || (item.get("learn") != null && !item.get("learn").trim().isEmpty()))
        {
          valid.add(item);
        }
      }
      return valid;
    }
    catch (Exception e)
    {
      return empty;
    }
  }

  public static List<Map<String, String>> parseActionPlanLenient(String rawJson)
  {
    List<Map<String, String>> empty = new ArrayList<Map<String, String>>();
    if (rawJson == null || rawJson.trim().isEmpty()) return empty;
    String arr = extractJsonArray(rawJson);
    if (arr == null || arr.isEmpty())
    {
      String trimmed = rawJson.trim();
      if (trimmed.startsWith("{") && trimmed.endsWith("}"))
      {
        arr = "[" + trimmed + "]";
      }
      else
      {
        return empty;
      }
    }
    try
    {
      return parseActionPlan(arr);
    }
    catch (Exception e)
    {
      return empty;
    }
  }

  /**
   * Strips the raw <station-actions>...</station-actions>, <station-inspect>...</station-inspect>,
   * and <ai-learn>...</ai-learn> blocks from the display text so the conversation stays clean and readable.
   */
  public static String stripActionTagsForDisplay(String responseText)
  {
    if (responseText == null) return "";
    String cleaned = StationInspector.stripInspectTagsForDisplay(responseText);
    cleaned = EddiesAiBrainManager.stripLearnTagsForDisplay(cleaned);
    int tagStart = cleaned.indexOf("<station-actions>");
    int tagEnd = cleaned.indexOf("</station-actions>");
    if (tagStart >= 0 && tagEnd > tagStart)
    {
      String before = cleaned.substring(0, tagStart).trim();
      String after = cleaned.substring(tagEnd + "</station-actions>".length()).trim();
      if (before.isEmpty() && after.isEmpty())
      {
        return "(Station/file modification plan ready)";
      }
      return (before + "\n" + after).trim();
    }
    return cleaned.trim();
  }

  /**
   * Builds a concise one-line headline for the Guardrail Dialog & Undo History.
   */
  public static String buildPlanHeadline(List<Map<String, String>> plan)
  {
    if (plan == null || plan.isEmpty())
    {
      return "No modifications";
    }
    if (plan.size() == 1)
    {
      Map<String, String> a = plan.get(0);
      String act = firstNonEmpty(a.get("action"), "Modify");
      String tgt = firstNonEmpty(a.get("name"), a.get("component"), a.get("target"), a.get("file"), "activeTarget");
      return act + " on " + tgt + " (1 operation)";
    }
    return "Apply " + plan.size() + " Station / File Modification Operation(s)";
  }

  /**
   * Formats an efficient, human-readable description of the proposed station/file modifications
   * for the Guardrail Confirmation Dialog.
   */
  public static String summarizePlan(List<Map<String, String>> plan)
  {
    if (plan == null || plan.isEmpty()) return "No station or file actions.";
    StringBuilder sb = new StringBuilder();
    sb.append("Proposed Operations (").append(plan.size()).append(" total):\n\n");
    for (int i = 0; i < plan.size(); i++)
    {
      Map<String, String> a = plan.get(i);
      String act = firstNonEmpty(a.get("action"), "?");
      sb.append("  ").append(i + 1).append(". [").append(act).append("] ");
      if ("createComponent".equalsIgnoreCase(act) || "addComponent".equalsIgnoreCase(act))
      {
        sb.append("Create ").append(firstNonEmpty(a.get("name"), "?"))
          .append(" (").append(firstNonEmpty(a.get("type"), "?")).append(")");
        if (a.get("parent") != null) sb.append(" in ").append(a.get("parent"));
        if (a.get("value") != null) sb.append(" = ").append(a.get("value"));
        if (a.get("facets") != null) sb.append(" [facets: ").append(a.get("facets")).append("]");
      }
      else if ("setSlotValue".equalsIgnoreCase(act) || "setProperty".equalsIgnoreCase(act))
      {
        sb.append("Set ").append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"))
          .append(".").append(firstNonEmpty(a.get("slot"), a.get("property"), "?"))
          .append(" = ").append(a.get("value"));
      }
      else if ("addDynamicSlot".equalsIgnoreCase(act) || "addProperty".equalsIgnoreCase(act))
      {
        sb.append("Add dynamic slot ").append(firstNonEmpty(a.get("slot"), a.get("name"), "?"))
          .append(" (").append(firstNonEmpty(a.get("type"), "baja:String")).append(") on ")
          .append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"));
        if (a.get("value") != null) sb.append(" = ").append(a.get("value"));
      }
      else if ("addExtension".equalsIgnoreCase(act))
      {
        sb.append("Attach extension ").append(firstNonEmpty(a.get("type"), "?"))
          .append(" as \"").append(firstNonEmpty(a.get("name"), a.get("slot"), "ext")).append("\" on ")
          .append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"));
      }
      else if ("setFacets".equalsIgnoreCase(act))
      {
        sb.append("Set facets on ").append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"))
          .append(".").append(firstNonEmpty(a.get("slot"), "facets"))
          .append(" -> ").append(a.get("facets"));
      }
      else if ("setSlotFlags".equalsIgnoreCase(act))
      {
        sb.append("Set flags on ").append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"))
          .append(".").append(firstNonEmpty(a.get("slot"), "?"))
          .append(" -> ").append(firstNonEmpty(a.get("flags"), "?"));
      }
      else if ("addTag".equalsIgnoreCase(act) || "setTag".equalsIgnoreCase(act))
      {
        sb.append("Set tag ").append(firstNonEmpty(a.get("tag"), a.get("id"), "?"))
          .append(a.get("value") != null ? ("=" + a.get("value")) : "")
          .append(" on ").append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"));
      }
      else if ("removeTag".equalsIgnoreCase(act))
      {
        sb.append("Remove tag ").append(firstNonEmpty(a.get("tag"), a.get("id"), "?"))
          .append(" from ").append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"));
      }
      else if ("addRelation".equalsIgnoreCase(act))
      {
        sb.append("Add relation ").append(firstNonEmpty(a.get("relation"), a.get("id"), "?"))
          .append(" from ").append(firstNonEmpty(a.get("source"), a.get("component"), "activeTarget"))
          .append(" -> ").append(firstNonEmpty(a.get("target"), "?"));
      }
      else if ("link".equalsIgnoreCase(act) || "addLink".equalsIgnoreCase(act))
      {
        sb.append("Wire link ").append(firstNonEmpty(a.get("source"), a.get("from"), "?"))
          .append(".").append(firstNonEmpty(a.get("sourceSlot"), a.get("fromSlot"), "out"))
          .append(" -> ")
          .append(firstNonEmpty(a.get("target"), a.get("to"), "?"))
          .append(".").append(firstNonEmpty(a.get("targetSlot"), a.get("toSlot"), "inA"));
      }
      else if ("unlink".equalsIgnoreCase(act) || "removeLink".equalsIgnoreCase(act))
      {
        sb.append("Remove link ").append(firstNonEmpty(a.get("source"), "*"))
          .append(" -> ").append(firstNonEmpty(a.get("target"), "activeTarget"))
          .append(".").append(firstNonEmpty(a.get("targetSlot"), "*"));
      }
      else if ("renameComponent".equalsIgnoreCase(act) || "renameSlot".equalsIgnoreCase(act))
      {
        sb.append("Rename ").append(firstNonEmpty(a.get("component"), a.get("oldName"), "?"))
          .append(" -> ").append(firstNonEmpty(a.get("newName"), "?"));
      }
      else if ("copyComponent".equalsIgnoreCase(act) || "duplicateComponent".equalsIgnoreCase(act))
      {
        sb.append("Duplicate ").append(firstNonEmpty(a.get("component"), a.get("source"), "?"))
          .append(" as ").append(firstNonEmpty(a.get("newName"), a.get("name"), "?"));
      }
      else if ("moveComponent".equalsIgnoreCase(act))
      {
        sb.append("Move ").append(firstNonEmpty(a.get("component"), "?"))
          .append(" into ").append(firstNonEmpty(a.get("targetParent"), a.get("parent"), "?"));
      }
      else if ("deleteComponent".equalsIgnoreCase(act) || "deleteSlot".equalsIgnoreCase(act))
      {
        sb.append("Delete ").append(firstNonEmpty(a.get("component"), a.get("name"), a.get("slot"), "?"));
      }
      else if ("invokeAction".equalsIgnoreCase(act))
      {
        sb.append("Invoke action ").append(firstNonEmpty(a.get("actionName"), a.get("slot"), "?"))
          .append(" on ").append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"));
        if (a.get("arg") != null || a.get("value") != null)
        {
          sb.append(" (arg=").append(firstNonEmpty(a.get("arg"), a.get("value"))).append(")");
        }
      }
      else if ("batchBql".equalsIgnoreCase(act))
      {
        sb.append("Batch BQL [").append(firstNonEmpty(a.get("subAction"), "?")).append("] on query: ")
          .append(firstNonEmpty(a.get("query"), a.get("bql"), "?"));
      }
      else if ("updateProgram".equalsIgnoreCase(act))
      {
        sb.append("Update & compile Java source on BProgram ")
          .append(firstNonEmpty(a.get("component"), "activeTarget"));
      }
      else if ("updatePxFile".equalsIgnoreCase(act)
        || "writeFile".equalsIgnoreCase(act)
        || "createFile".equalsIgnoreCase(act))
      {
        sb.append("Write file ").append(firstNonEmpty(a.get("file"), a.get("ord"), a.get("path"), "activeFile"));
      }
      else if ("patchFile".equalsIgnoreCase(act) || "editFile".equalsIgnoreCase(act))
      {
        sb.append("Patch/edit file ").append(firstNonEmpty(a.get("file"), a.get("ord"), a.get("path"), "activeFile"));
      }
      else if ("copyFile".equalsIgnoreCase(act))
      {
        sb.append("Copy file ").append(firstNonEmpty(a.get("source"), a.get("file"), "?"))
          .append(" -> ").append(firstNonEmpty(a.get("target"), a.get("to"), "?"));
      }
      else if ("renameFile".equalsIgnoreCase(act))
      {
        sb.append("Rename file ").append(firstNonEmpty(a.get("file"), a.get("source"), "?"))
          .append(" -> ").append(firstNonEmpty(a.get("newName"), a.get("target"), "?"));
      }
      else if ("deleteFile".equalsIgnoreCase(act))
      {
        sb.append("Delete file ").append(firstNonEmpty(a.get("file"), a.get("ord"), a.get("path"), "?"));
      }
      else if ("makeDir".equalsIgnoreCase(act))
      {
        sb.append("Create directory ").append(firstNonEmpty(a.get("dir"), a.get("path"), a.get("ord"), "?"));
      }
      else if ("importBog".equalsIgnoreCase(act))
      {
        sb.append("Import BOG XML component graph into ").append(firstNonEmpty(a.get("parent"), "activeTarget"));
      }
      else if ("arrangeWiresheet".equalsIgnoreCase(act) || "layoutWiresheet".equalsIgnoreCase(act))
      {
        sb.append("Arrange WireSheet layout (signal-flow + alphabetical grid) on ")
          .append(firstNonEmpty(a.get("parent"), a.get("component"), "activeTarget"));
      }
      else
      {
        sb.append(a.toString());
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Executes a 1-click full WireSheet arrangement on the active target container in Workbench,
   * pushing all position changes onto the Undo stack so the user can revert with 1 click.
   */
  public static String arrangeActiveWiresheet(BWbShell shell)
  {
    StationContextExtractor.StationTarget ctx = StationContextExtractor.extract(shell);
    BComponent container = resolveContainerComponent(ctx.targetComponent);
    if (container == null)
    {
      return "No active WireSheet container component is selected in Workbench.";
    }

    List<Runnable> undoOps = new ArrayList<Runnable>();
    String msg = WiresheetLayoutEngine.arrangeWiresheet(container, undoOps);
    if (!undoOps.isEmpty())
    {
      StationUndoManager.pushBatch("Arrange WireSheet on " + container.getName(), undoOps);
    }
    WiresheetLayoutEngine.refreshActiveWiresheetView(shell);
    return msg;
  }

  /**
   * Executes the plan in "Make Duplicate Only" mode:
   * - Never modifies the live station or original file.
   * - If the plan modifies a file (PX, BOG, XML, etc.), applies the edits to an in-memory copy of the file's
   *   bytes and opens EddiesDuplicateChooserDialog so the user can save the modified duplicate anywhere in
   *   the Station's Files space or on her local machine (creating a new folder if desired).
   * - If the plan modifies station components/WireSheets, clones the target container BComponent in memory,
   *   runs the entire action plan against the in-memory clone, encodes the modified clone to a .bog file,
   *   and opens EddiesDuplicateChooserDialog to save it.
   */
  public static String executePlanAsDuplicate(
    BWbShell shell,
    BWidget dialogOwner,
    List<Map<String, String>> plan)
  {
    if (plan == null || plan.isEmpty())
    {
      return "No actions to duplicate.";
    }

    StationContextExtractor.StationTarget ctx = StationContextExtractor.extract(shell);
    BComponent liveBaseComp = ctx.targetComponent;
    BIFile activeFile = ctx.pxFile;

    try
    {
      // Check if the plan is primarily a file modification (updatePxFile / writeFile / patchFile / createFile)
      Map<String, String> fileAction = findPrimaryFileAction(plan);
      if (fileAction != null && isPureFilePlan(plan))
      {
        String actName = firstNonEmpty(fileAction.get("action"), "");
        String fileSpec = firstNonEmpty(fileAction.get("file"), fileAction.get("ord"), fileAction.get("path"), "activeFile");
        byte[] origBytes = null;
        try
        {
          origBytes = readAnyFileBytes(liveBaseComp, activeFile, fileSpec);
        }
        catch (Throwable ignore)
        {
        }

        byte[] modifiedBytes;
        if ("patchFile".equalsIgnoreCase(actName) || "editFile".equalsIgnoreCase(actName))
        {
          String currentText = origBytes != null ? new String(origBytes, StandardCharsets.UTF_8) : "";
          for (int i = 0; i < plan.size(); i++)
          {
            Map<String, String> a = plan.get(i);
            String targetStr = a.get("target");
            String replacementStr = a.get("replacement") != null ? a.get("replacement") : "";
            if (targetStr != null && !targetStr.isEmpty() && currentText.contains(targetStr))
            {
              currentText = currentText.replace(targetStr, replacementStr);
            }
          }
          modifiedBytes = currentText.getBytes(StandardCharsets.UTF_8);
        }
        else
        {
          String newContent = firstNonEmpty(fileAction.get("xml"), fileAction.get("content"), fileAction.get("text"));
          modifiedBytes = newContent.getBytes(StandardCharsets.UTF_8);
        }

        String origFileName = "ModifiedFile.px";
        String ext = "px";
        if (activeFile != null)
        {
          origFileName = activeFile.getFileName();
          if (activeFile.getExtension() != null && !activeFile.getExtension().isEmpty())
          {
            ext = activeFile.getExtension();
          }
        }
        else if (!"activeFile".equalsIgnoreCase(fileSpec))
        {
          int slash = Math.max(fileSpec.lastIndexOf('/'), fileSpec.lastIndexOf('\\'));
          origFileName = slash >= 0 ? fileSpec.substring(slash + 1) : fileSpec;
          int dot = origFileName.lastIndexOf('.');
          if (dot > 0 && dot < origFileName.length() - 1)
          {
            ext = origFileName.substring(dot + 1);
          }
        }

        String suggestedDupName = buildDuplicateFileName(origFileName, ext);
        EddiesDuplicateChooserDialog.DuplicateSaveResult saveRes =
          EddiesDuplicateChooserDialog.promptAndSaveDuplicate(
            dialogOwner,
            liveBaseComp,
            activeFile,
            suggestedDupName,
            ext,
            modifiedBytes);

        if (!saveRes.saved)
        {
          return "Make Duplicate Only cancelled by user (original file was not modified).";
        }
        return "Saved modified file duplicate to: " + saveRes.savedLocationSummary
          + "\n(Original file was left completely untouched.)";
      }

      // Otherwise: Station Component / WireSheet modification -> Clone in memory & export .bog duplicate!
      BComponent sourceContainer = resolveContainerComponent(liveBaseComp);
      BComponent inMemoryRoot;
      String baseNameForBog = "StationDuplicate";

      if (sourceContainer != null)
      {
        baseNameForBog = (sourceContainer.getName() != null && !sourceContainer.getName().isEmpty())
          ? sourceContainer.getName()
          : sourceContainer.getType().getTypeName();
        try
        {
          inMemoryRoot = (BComponent) sourceContainer.newCopy();
        }
        catch (Throwable t)
        {
          // If the container is a special root (like BStation or BDeviceNetwork) that cannot be directly cloned,
          // create a BFolder containing clones of its child components
          inMemoryRoot = new BFolder();
          BComponent[] kids = sourceContainer.getChildComponents();
          if (kids != null)
          {
            for (int i = 0; i < kids.length; i++)
            {
              try
              {
                inMemoryRoot.add(kids[i].getName(), kids[i].newCopy());
              }
              catch (Throwable ignore) {}
            }
          }
        }
      }
      else
      {
        inMemoryRoot = new BFolder();
      }

      // Run the station modification plan against inMemoryRoot (offline, no Fox calls!)
      StringBuilder offlineLog = new StringBuilder();
      executePlanAgainstTarget(null, inMemoryRoot, null, plan, new ArrayList<Runnable>(), offlineLog, true);

      String bogXml = StationUndoManager.encodeComponentToBogXml(inMemoryRoot);
      byte[] bogBytes = bogXml.getBytes(StandardCharsets.UTF_8);
      String suggestedBogName = buildDuplicateFileName(baseNameForBog, "bog");

      EddiesDuplicateChooserDialog.DuplicateSaveResult saveRes =
        EddiesDuplicateChooserDialog.promptAndSaveDuplicate(
          dialogOwner,
          liveBaseComp,
          activeFile,
          suggestedBogName,
          "bog",
          bogBytes);

      if (!saveRes.saved)
      {
        return "Make Duplicate Only cancelled by user (live station was not modified).";
      }
      return "Created modified duplicate BOG file at: " + saveRes.savedLocationSummary
        + "\n(Live station was left completely untouched.)\n\nDuplicate Execution Details:\n"
        + offlineLog.toString();
    }
    catch (Throwable t)
    {
      return "Make Duplicate Only error: " + t.getMessage();
    }
  }

  /**
   * Executes the parsed action plan against the active station view or file in the given BWbShell,
   * recording a full pre-change Undo batch in StationUndoManager.
   */
  public static String executePlan(BWbShell shell, List<Map<String, String>> plan)
  {
    if (plan == null || plan.isEmpty())
    {
      return "No station actions to execute.";
    }

    StationContextExtractor.StationTarget ctx = StationContextExtractor.extract(shell);
    BComponent baseComp = ctx.targetComponent;
    BIFile activeFile = ctx.pxFile;

    final List<Runnable> undoOps = new ArrayList<Runnable>();
    StringBuilder logBuilder = new StringBuilder();
    logBuilder.append("Executing ").append(plan.size()).append(" operation(s)...\n");

    executePlanAgainstTarget(shell, baseComp, activeFile, plan, undoOps, logBuilder, false);

    final BSubSpaceFile backingBog = StationContextExtractor.getBackingSubSpaceFile(baseComp);
    if (backingBog != null && !undoOps.isEmpty())
    {
      try
      {
        backingBog.save();
        logBuilder.append("Saved offline BOG database: ").append(backingBog.getFileName()).append("\n");
        // Insert at index 0 so that when StationUndoManager runs undoOps in reverse order,
        // backingBog.save() executes LAST after all component restorations complete.
        undoOps.add(0, new Runnable()
        {
          @Override
          public void run()
          {
            try { backingBog.save(); } catch (Throwable ignore) {}
          }
        });
      }
      catch (Throwable saveErr)
      {
        logBuilder.append("Note: Offline BOG modified in memory (use File -> Save Bog to persist: ")
          .append(saveErr.getMessage()).append(")\n");
      }
    }

    if (!undoOps.isEmpty())
    {
      StationUndoManager.pushBatch(buildPlanHeadline(plan), undoOps);
    }

    WiresheetLayoutEngine.refreshActiveWiresheetView(shell);
    return logBuilder.toString();
  }

  private static void executePlanAgainstTarget(
    BWbShell shell,
    BComponent baseComp,
    BIFile activeFile,
    List<Map<String, String>> plan,
    final List<Runnable> undoOps,
    StringBuilder logBuilder,
    boolean isOfflineDuplicate)
  {
    final Map<BComponent, Set<String>> touchedNamesByContainer = new LinkedHashMap<BComponent, Set<String>>();
    final Map<BComponent, List<WiresheetLayoutEngine.ExplicitLink>> explicitLinksByContainer =
      new LinkedHashMap<BComponent, List<WiresheetLayoutEngine.ExplicitLink>>();

    for (int index = 0; index < plan.size(); index++)
    {
      Map<String, String> action = plan.get(index);
      String actionName = firstNonEmpty(action.get("action"), "");
      logBuilder.append("[").append(index + 1).append("] ");

      try
      {
        if ("createComponent".equalsIgnoreCase(actionName) || "addComponent".equalsIgnoreCase(actionName))
        {
          String typeStr = action.get("type");
          String name = action.get("name");
          String parentStr = action.get("parent");
          String xStr = action.get("x");
          String yStr = action.get("y");
          String initValue = action.get("value");
          String initFacets = action.get("facets");
          String initFlags = action.get("flags");

          final BComponent parent = (parentStr != null && !parentStr.trim().isEmpty())
            ? resolveComponent(baseComp, parentStr)
            : resolveContainerComponent(baseComp);

          if (parent == null)
          {
            throw new Exception("No target station container component is active in Workbench.");
          }

          Type type = resolveType(typeStr);
          if (type == null)
          {
            throw new Exception("Type not found in Niagara: " + typeStr);
          }

          BComponent newComp = (BComponent) ((BValue) type.getInstance()).newCopy();

          final String addedName = name;
          int rawX = (xStr != null && !xStr.isEmpty()) ? Integer.parseInt(xStr) : (3 + (index % 4) * 14);
          int rawY = (yStr != null && !yStr.isEmpty()) ? Integer.parseInt(yStr) : (3 + (index / 4) * 7);

          int p = (rawX > 120) ? Math.round(rawX / 12.0f) : Math.max(2, rawX);
          int q = (rawY > 180) ? Math.round(rawY / 12.0f) : Math.max(2, rawY);

          int reqWidth = WiresheetLayoutEngine.computeRequiredWixelWidth(newComp, addedName);
          BWsAnnotation annotation = BWsAnnotation.make(p, q, reqWidth);
          if (newComp.get("wsAnnotation") != null)
          {
            newComp.set("wsAnnotation", annotation, null);
          }
          else
          {
            newComp.add("wsAnnotation", annotation);
          }

          if (initFacets != null && !initFacets.trim().isEmpty())
          {
            try
            {
              applyFacetsToComponent(newComp, "facets", initFacets, null);
            }
            catch (Throwable ignore) {}
          }
          if (initValue != null && !initValue.trim().isEmpty())
          {
            try
            {
              Slot targetValSlot = resolveSlot(newComp, "fallback");
              if (targetValSlot == null) targetValSlot = resolveSlot(newComp, "out");
              if (targetValSlot == null) targetValSlot = resolveSlot(newComp, "in10");
              if (targetValSlot instanceof Property)
              {
                applyPropertyValue(newComp, (Property) targetValSlot, initValue, null);
              }
            }
            catch (Throwable ignore) {}
          }

          BValue existingVal = parent.get(addedName);
          if (existingVal instanceof BComponent)
          {
            Runnable restoreOld = StationUndoManager.createChildComponentRestoreUndo(
              parent, (BComponent) existingVal);
            if (restoreOld != null) undoOps.add(restoreOld);
            parent.remove(addedName);
            logBuilder.append("Replaced existing \"").append(addedName).append("\". ");
          }
          else if (existingVal != null)
          {
            parent.remove(addedName);
          }

          int slotFlags = (initFlags != null && !initFlags.trim().isEmpty())
            ? parseFlagsString(initFlags, 0)
            : 0;
          parent.add(addedName, newComp, slotFlags);
          if (!(existingVal instanceof BComponent))
          {
            undoOps.add(new Runnable()
            {
              @Override
              public void run()
              {
                try { parent.remove(addedName); } catch (Throwable ignore) {}
              }
            });
          }

          BComponent mountedComp = resolveMountedChild(parent, addedName, newComp);
          if (mountedComp != newComp)
          {
            if (mountedComp.get("wsAnnotation") != null)
            {
              mountedComp.set("wsAnnotation", annotation, null);
            }
            else
            {
              mountedComp.add("wsAnnotation", annotation);
            }
            if (initFacets != null && !initFacets.trim().isEmpty())
            {
              applyFacetsToComponent(mountedComp, "facets", initFacets, undoOps);
            }
            if (initValue != null && !initValue.trim().isEmpty())
            {
              Slot targetValSlot = resolveSlot(mountedComp, "fallback");
              if (targetValSlot == null) targetValSlot = resolveSlot(mountedComp, "out");
              if (targetValSlot == null) targetValSlot = resolveSlot(mountedComp, "in10");
              if (targetValSlot instanceof Property)
              {
                applyPropertyValue(mountedComp, (Property) targetValSlot, initValue, undoOps);
              }
            }
          }

          recordTouchedComponentName(touchedNamesByContainer, parent, addedName);
          logBuilder.append("Created \"").append(addedName).append("\" (").append(type.toString())
            .append(") width=").append(reqWidth).append(" wixels.\n");
        }
        else if ("setSlotValue".equalsIgnoreCase(actionName) || "setProperty".equalsIgnoreCase(actionName))
        {
          String componentStr = firstNonEmpty(action.get("component"), action.get("target"));
          String slotName = firstNonEmpty(action.get("slot"), action.get("property"));
          String valueStr = action.get("value");

          BComponent comp = resolveComponent(baseComp, componentStr);
          if (comp == null)
          {
            throw new Exception("Target component not found: " + componentStr);
          }

          String desc = setDeepPropertyOnComponent(comp, slotName, valueStr, undoOps);
          logBuilder.append("Set ").append(comp.getName()).append(".").append(slotName)
            .append(" = ").append(desc).append(".\n");
        }
        else if ("addDynamicSlot".equalsIgnoreCase(actionName) || "addProperty".equalsIgnoreCase(actionName))
        {
          String componentStr = firstNonEmpty(action.get("component"), action.get("target"));
          final String slotName = firstNonEmpty(action.get("slot"), action.get("name"), action.get("property"));
          String typeStr = firstNonEmpty(action.get("type"), "baja:String");
          String valueStr = action.get("value");
          String flagsStr = action.get("flags");
          String facetsStr = action.get("facets");

          final BComponent comp = resolveComponent(baseComp, componentStr);
          if (comp == null) throw new Exception("Target component not found: " + componentStr);

          Type slotType = resolveType(typeStr);
          if (slotType == null) throw new Exception("Slot type not found: " + typeStr);

          BValue instance = (BValue) slotType.getInstance();
          if (instance instanceof BSimple && valueStr != null && !valueStr.isEmpty())
          {
            instance = (BValue) ((BSimple) instance).decodeFromString(valueStr);
          }
          else
          {
            instance = instance.newCopy();
          }

          int flags = parseFlagsString(flagsStr, 0);
          BFacets facets = (facetsStr != null && !facetsStr.trim().isEmpty())
            ? BFacets.tryMake(facetsStr.trim())
            : BFacets.NULL;

          if (comp.get(slotName) != null)
          {
            Property existingProp = comp.getProperty(slotName);
            if (existingProp != null && valueStr != null)
            {
              applyPropertyValue(comp, existingProp, valueStr, undoOps);
            }
          }
          else
          {
            comp.add(slotName, instance, flags, facets != null ? facets : BFacets.NULL, null);
            undoOps.add(new Runnable()
            {
              @Override
              public void run()
              {
                try { comp.remove(slotName); } catch (Throwable ignore) {}
              }
            });
          }
          logBuilder.append("Added dynamic slot \"").append(slotName).append("\" (").append(slotType)
            .append(") on \"").append(comp.getName()).append("\".\n");
        }
        else if ("addExtension".equalsIgnoreCase(actionName))
        {
          String componentStr = firstNonEmpty(action.get("component"), action.get("target"));
          String extTypeStr = firstNonEmpty(action.get("type"), action.get("extensionType"));
          final String extSlotName = firstNonEmpty(action.get("name"), action.get("slot"), "HistoryExt");
          String propsConfig = firstNonEmpty(action.get("properties"), action.get("config"));

          final BComponent comp = resolveComponent(baseComp, componentStr);
          if (comp == null) throw new Exception("Target point/component not found: " + componentStr);

          Type extType = resolveType(extTypeStr);
          if (extType == null) throw new Exception("Extension type not found: " + extTypeStr);

          BValue extInstance = ((BValue) extType.getInstance()).newCopy();
          if (comp.get(extSlotName) != null)
          {
            comp.remove(extSlotName);
          }
          comp.add(extSlotName, extInstance);
          undoOps.add(new Runnable()
          {
            @Override
            public void run()
            {
              try { comp.remove(extSlotName); } catch (Throwable ignore) {}
            }
          });

          BValue mountedExt = comp.get(extSlotName);
          if (mountedExt instanceof BComponent && propsConfig != null && !propsConfig.isEmpty())
          {
            BComponent extComp = (BComponent) mountedExt;
            String[] pairs = propsConfig.split("[;|]");
            for (int pIdx = 0; pIdx < pairs.length; pIdx++)
            {
              int eq = pairs[pIdx].indexOf('=');
              if (eq > 0)
              {
                String k = pairs[pIdx].substring(0, eq).trim();
                String v = pairs[pIdx].substring(eq + 1).trim();
                try
                {
                  setDeepPropertyOnComponent(extComp, k, v, null);
                }
                catch (Throwable ignore) {}
              }
            }
          }
          logBuilder.append("Attached extension \"").append(extSlotName).append("\" (").append(extType)
            .append(") to \"").append(comp.getName()).append("\".\n");
        }
        else if ("setFacets".equalsIgnoreCase(actionName))
        {
          String componentStr = firstNonEmpty(action.get("component"), action.get("target"));
          String slotName = firstNonEmpty(action.get("slot"), "facets");
          String facetsStr = action.get("facets");

          BComponent comp = resolveComponent(baseComp, componentStr);
          if (comp == null)
          {
            throw new Exception("Target component not found: " + componentStr);
          }
          applyFacetsToComponent(comp, slotName, facetsStr, undoOps);
          logBuilder.append("Updated facets on \"").append(comp.getName()).append("\": ").append(facetsStr).append(".\n");
        }
        else if ("setSlotFlags".equalsIgnoreCase(actionName))
        {
          String componentStr = firstNonEmpty(action.get("component"), action.get("target"));
          String slotName = firstNonEmpty(action.get("slot"), action.get("property"), "out");
          String flagsStr = firstNonEmpty(action.get("flags"), "summary");

          final BComponent comp = resolveComponent(baseComp, componentStr);
          if (comp == null) throw new Exception("Target component not found: " + componentStr);

          final Slot slot = resolveSlot(comp, slotName);
          if (slot == null) throw new Exception("Slot not found: " + slotName + " on " + comp.getName());

          final int oldFlags = comp.getFlags(slot);
          int newFlags = parseFlagsString(flagsStr, oldFlags);
          comp.setFlags(slot, newFlags, null);
          undoOps.add(new Runnable()
          {
            @Override
            public void run()
            {
              try { comp.setFlags(slot, oldFlags, null); } catch (Throwable ignore) {}
            }
          });
          logBuilder.append("Set flags on \"").append(comp.getName()).append(".").append(slot.getName())
            .append("\" to [").append(Flags.encodeToString(newFlags)).append("].\n");
        }
        else if ("addTag".equalsIgnoreCase(actionName) || "setTag".equalsIgnoreCase(actionName))
        {
          String componentStr = firstNonEmpty(action.get("component"), action.get("target"));
          String tagQName = firstNonEmpty(action.get("tag"), action.get("id"), action.get("name"));
          String tagVal = action.get("value");

          final BComponent comp = resolveComponent(baseComp, componentStr);
          if (comp == null) throw new Exception("Target component not found: " + componentStr);
          if (tagQName.indexOf(':') < 0)
          {
            tagQName = "n4:" + tagQName;
          }

          final Id tagId = Id.newId(tagQName);
          final boolean hadTagBefore = comp.tags().contains(tagId);
          Tag createdTag = (tagVal != null && !tagVal.isEmpty())
            ? Tag.newTag(tagQName, tagVal)
            : Tag.newTag(tagQName);
          comp.tags().set(createdTag);

          if (!hadTagBefore)
          {
            undoOps.add(new Runnable()
            {
              @Override
              public void run()
              {
                try { comp.tags().removeAll(tagId); } catch (Throwable ignore) {}
              }
            });
          }
          logBuilder.append("Set tag \"").append(tagQName).append("\" on \"").append(comp.getName()).append("\".\n");
        }
        else if ("removeTag".equalsIgnoreCase(actionName))
        {
          String componentStr = firstNonEmpty(action.get("component"), action.get("target"));
          String tagQName = firstNonEmpty(action.get("tag"), action.get("id"), action.get("name"));
          if (tagQName.indexOf(':') < 0) tagQName = "n4:" + tagQName;

          BComponent comp = resolveComponent(baseComp, componentStr);
          if (comp == null) throw new Exception("Target component not found: " + componentStr);
          comp.tags().removeAll(Id.newId(tagQName));
          logBuilder.append("Removed tag \"").append(tagQName).append("\" from \"").append(comp.getName()).append("\".\n");
        }
        else if ("addRelation".equalsIgnoreCase(actionName))
        {
          String srcStr = firstNonEmpty(action.get("source"), action.get("component"), "activeTarget");
          String tgtStr = firstNonEmpty(action.get("target"), action.get("to"));
          String relQName = firstNonEmpty(action.get("relation"), action.get("id"), "n4:EquipRef");
          if (relQName.indexOf(':') < 0) relQName = "n4:" + relQName;

          final BComponent srcComp = resolveComponent(baseComp, srcStr);
          final BComponent tgtComp = resolveComponent(baseComp, tgtStr);
          if (srcComp == null) throw new Exception("Source component not found: " + srcStr);
          if (tgtComp == null) throw new Exception("Target component not found: " + tgtStr);

          final Id relId = Id.newId(relQName);
          srcComp.relations().add(relId, tgtComp);
          undoOps.add(new Runnable()
          {
            @Override
            public void run()
            {
              try { srcComp.relations().remove(relId, tgtComp); } catch (Throwable ignore) {}
            }
          });
          logBuilder.append("Added relation \"").append(relQName).append("\" from \"")
            .append(srcComp.getName()).append("\" -> \"").append(tgtComp.getName()).append("\".\n");
        }
        else if ("link".equalsIgnoreCase(actionName) || "addLink".equalsIgnoreCase(actionName))
        {
          String sourceStr = firstNonEmpty(action.get("source"), action.get("from"), action.get("sourceComponent"));
          String sourceSlotStr = firstNonEmpty(action.get("sourceSlot"), action.get("fromSlot"), action.get("outSlot"), action.get("slot"));
          String targetStr = firstNonEmpty(action.get("target"), action.get("to"), action.get("targetComponent"));
          String targetSlotStr = firstNonEmpty(action.get("targetSlot"), action.get("toSlot"), action.get("inSlot"));

          BComponent source = resolveComponent(baseComp, sourceStr);
          final BComponent target = resolveComponent(baseComp, targetStr);

          if (source == null) throw new Exception("Source component not found: " + sourceStr);
          if (target == null) throw new Exception("Target component not found: " + targetStr);

          Slot sourceSlot = resolveSlot(source, !sourceSlotStr.isEmpty() ? sourceSlotStr : "out");
          Slot targetSlot = null;

          if (!targetSlotStr.isEmpty())
          {
            targetSlot = resolveSlot(target, targetSlotStr);
          }
          else
          {
            targetSlot = resolveSlot(target, "inA");
            if (targetSlot == null) targetSlot = resolveSlot(target, "fallback");
            if (targetSlot == null) targetSlot = resolveSlot(target, "controlledVariable");
            if (targetSlot == null) targetSlot = resolveSlot(target, "in");
            if (targetSlot == null) targetSlot = resolveSlot(target, "in1");
          }

          if (sourceSlot == null)
          {
            throw new Exception("Source slot not found: " + (sourceSlotStr.isEmpty() ? "out" : sourceSlotStr) + " on " + source.getName());
          }
          if (targetSlot == null)
          {
            throw new Exception("Target slot not found: " + (targetSlotStr.isEmpty() ? "inA/fallback" : targetSlotStr) + " on " + target.getName());
          }

          BLink link = null;
          try
          {
            link = target.makeLink(source, sourceSlot, targetSlot, null);
          }
          catch (Throwable ignore) {}

          BOrd sourceOrd = source.getHandleOrd();
          if (sourceOrd == null || sourceOrd.isNull())
          {
            sourceOrd = source.getSlotPathOrd();
          }
          if (sourceOrd == null || sourceOrd.isNull())
          {
            sourceOrd = BOrd.make("slot:" + source.getName());
          }

          if (link == null)
          {
            link = new BLink(sourceOrd, sourceSlot.getName(), targetSlot.getName(), true);
          }
          else if (link.getSourceOrd() == null || link.getSourceOrd().isNull())
          {
            link.setSourceOrd(sourceOrd);
          }

          final Property linkProp = target.add(null, link);
          undoOps.add(new Runnable()
          {
            @Override
            public void run()
            {
              try { target.remove(linkProp); } catch (Throwable ignore) {}
            }
          });

          BComponent srcParent = source.getParentComponent();
          BComponent tgtParent = target.getParentComponent();
          if (srcParent != null)
          {
            recordTouchedComponentName(touchedNamesByContainer, srcParent, source.getName());
          }
          if (tgtParent != null)
          {
            recordTouchedComponentName(touchedNamesByContainer, tgtParent, target.getName());
          }
          if (srcParent != null && srcParent == tgtParent)
          {
            recordExplicitLink(
              explicitLinksByContainer,
              srcParent,
              new WiresheetLayoutEngine.ExplicitLink(
                source.getName(), sourceSlot.getName(), target.getName(), targetSlot.getName()));
          }

          logBuilder.append("Linked \"").append(source.getName()).append(".").append(sourceSlot.getName())
            .append("\" -> \"").append(target.getName()).append(".").append(targetSlot.getName()).append("\".\n");
        }
        else if ("unlink".equalsIgnoreCase(actionName) || "removeLink".equalsIgnoreCase(actionName))
        {
          String targetStr = firstNonEmpty(action.get("target"), action.get("component"));
          String targetSlotStr = firstNonEmpty(action.get("targetSlot"), action.get("slot"));
          String sourceStr = firstNonEmpty(action.get("source"), "");

          BComponent target = resolveComponent(baseComp, targetStr);
          if (target == null) throw new Exception("Target component not found: " + targetStr);

          BLink[] links = target.getLinks();
          int removed = 0;
          if (links != null)
          {
            for (int k = 0; k < links.length; k++)
            {
              BLink lnk = links[k];
              boolean slotMatch = targetSlotStr.isEmpty() || targetSlotStr.equalsIgnoreCase(lnk.getTargetSlotName());
              boolean srcMatch = sourceStr.isEmpty();
              if (!srcMatch)
              {
                try
                {
                  BComponent sc = lnk.getSourceComponent();
                  if (sc != null && sourceStr.equalsIgnoreCase(sc.getName()))
                  {
                    srcMatch = true;
                  }
                }
                catch (Throwable ignore) {}
                if (!srcMatch && lnk.getSourceOrd() != null)
                {
                  String ordStr = lnk.getSourceOrd().toString().toLowerCase();
                  if (ordStr.endsWith("/" + sourceStr.toLowerCase()) || ordStr.endsWith(":" + sourceStr.toLowerCase()))
                  {
                    srcMatch = true;
                  }
                }
              }
              if (slotMatch && srcMatch)
              {
                Runnable restoreLnk = StationUndoManager.createLinkRestoreUndo(target, lnk);
                if (restoreLnk != null) undoOps.add(restoreLnk);
                target.remove(lnk);
                removed++;
              }
            }
          }
          logBuilder.append("Removed ").append(removed).append(" link(s) on \"").append(target.getName()).append("\".\n");
        }
        else if ("renameComponent".equalsIgnoreCase(actionName) || "renameSlot".equalsIgnoreCase(actionName))
        {
          String oldName = firstNonEmpty(action.get("component"), action.get("oldName"), action.get("slot"));
          final String newName = firstNonEmpty(action.get("newName"), action.get("name"));
          BComponent comp = resolveComponent(baseComp, oldName);
          if (comp != null && comp.getParentComponent() != null)
          {
            final BComponent parent = comp.getParentComponent();
            final Property propInParent = comp.getPropertyInParentComponent();
            final String prevName = propInParent.getName();
            parent.rename(propInParent, newName);
            undoOps.add(new Runnable()
            {
              @Override
              public void run()
              {
                try { parent.rename(parent.getProperty(newName), prevName); } catch (Throwable ignore) {}
              }
            });
            logBuilder.append("Renamed \"").append(prevName).append("\" -> \"").append(newName).append("\".\n");
          }
          else
          {
            throw new Exception("Could not resolve component to rename: " + oldName);
          }
        }
        else if ("copyComponent".equalsIgnoreCase(actionName) || "duplicateComponent".equalsIgnoreCase(actionName))
        {
          String srcStr = firstNonEmpty(action.get("component"), action.get("source"));
          final String newName = firstNonEmpty(action.get("newName"), action.get("name"));
          String parentStr = action.get("parent");

          BComponent srcComp = resolveComponent(baseComp, srcStr);
          if (srcComp == null) throw new Exception("Source component not found: " + srcStr);

          final BComponent targetParent = (parentStr != null && !parentStr.isEmpty())
            ? resolveComponent(baseComp, parentStr)
            : (srcComp.getParentComponent() != null ? srcComp.getParentComponent() : resolveContainerComponent(baseComp));

          if (targetParent == null) throw new Exception("Target parent container not found for copyComponent.");

          BComponent copyComp = (BComponent) srcComp.newCopy();
          targetParent.add(newName, copyComp);
          undoOps.add(new Runnable()
          {
            @Override
            public void run()
            {
              try { targetParent.remove(newName); } catch (Throwable ignore) {}
            }
          });
          recordTouchedComponentName(touchedNamesByContainer, targetParent, newName);
          logBuilder.append("Duplicated \"").append(srcComp.getName()).append("\" as \"").append(newName).append("\".\n");
        }
        else if ("moveComponent".equalsIgnoreCase(actionName))
        {
          String srcStr = firstNonEmpty(action.get("component"), action.get("source"));
          String targetParentStr = firstNonEmpty(action.get("targetParent"), action.get("parent"), action.get("to"));
          String newName = action.get("newName");

          BComponent comp = resolveComponent(baseComp, srcStr);
          if (comp == null || comp.getParentComponent() == null)
          {
            throw new Exception("Component to move not found: " + srcStr);
          }
          final BComponent oldParent = comp.getParentComponent();
          final BComponent newParent = resolveComponent(baseComp, targetParentStr);
          if (newParent == null) throw new Exception("Destination container not found: " + targetParentStr);

          final String finalName = (newName != null && !newName.isEmpty()) ? newName : comp.getName();
          Runnable restoreOld = StationUndoManager.createChildComponentRestoreUndo(oldParent, comp);
          if (restoreOld != null) undoOps.add(restoreOld);

          BComponent copy = (BComponent) comp.newCopy();
          oldParent.remove(comp.getName());
          newParent.add(finalName, copy);
          undoOps.add(new Runnable()
          {
            @Override
            public void run()
            {
              try { newParent.remove(finalName); } catch (Throwable ignore) {}
            }
          });
          logBuilder.append("Moved \"").append(finalName).append("\" to \"").append(newParent.getName()).append("\".\n");
        }
        else if ("deleteComponent".equalsIgnoreCase(actionName) || "deleteSlot".equalsIgnoreCase(actionName))
        {
          String compStr = firstNonEmpty(action.get("component"), action.get("name"), action.get("slot"));
          BComponent comp = null;
          try
          {
            comp = resolveComponent(baseComp, compStr);
          }
          catch (Throwable ignore) {}

          if (comp != null && comp.getParentComponent() != null)
          {
            BComponent parent = comp.getParentComponent();
            String removedName = comp.getName();
            Runnable restoreSubtree = StationUndoManager.createChildComponentRestoreUndo(parent, comp);
            if (restoreSubtree != null)
            {
              undoOps.add(restoreSubtree);
            }
            parent.remove(removedName);
            logBuilder.append("Deleted component \"").append(removedName).append("\" (backed up for Undo).\n");
          }
          else if (baseComp != null && baseComp.getProperty(compStr) != null)
          {
            final BComponent parent = baseComp;
            final Property prop = baseComp.getProperty(compStr);
            final String slotName = prop.getName();
            final BValue oldVal = baseComp.get(prop) != null ? baseComp.get(prop).newCopy() : null;
            final int oldFlags = baseComp.getFlags(prop);
            final BFacets oldFacets = baseComp.getSlotFacets(prop);
            if (oldVal != null)
            {
              undoOps.add(new Runnable()
              {
                @Override
                public void run()
                {
                  try { parent.add(slotName, oldVal.newCopy(), oldFlags, oldFacets, null); } catch (Throwable ignore) {}
                }
              });
            }
            baseComp.remove(compStr);
            logBuilder.append("Deleted slot \"").append(compStr).append("\" from \"").append(baseComp.getName()).append("\".\n");
          }
          else
          {
            throw new Exception("Component/slot not found to delete: " + compStr);
          }
        }
        else if ("invokeAction".equalsIgnoreCase(actionName))
        {
          String compStr = firstNonEmpty(action.get("component"), action.get("target"));
          String actSlotName = firstNonEmpty(action.get("actionName"), action.get("slot"));
          String argStr = firstNonEmpty(action.get("arg"), action.get("parameter"), action.get("value"));

          BComponent comp = resolveComponent(baseComp, compStr);
          if (comp == null) throw new Exception("Target component not found: " + compStr);

          Action actSlot = comp.getAction(actSlotName);
          if (actSlot == null)
          {
            Slot s = resolveSlot(comp, actSlotName);
            if (s instanceof Action) actSlot = (Action) s;
          }
          if (actSlot == null) throw new Exception("Action slot not found: " + actSlotName + " on " + comp.getName());

          BValue argVal = decodeActionArgument(actSlot, argStr);
          comp.invoke(actSlot, argVal);
          logBuilder.append("Invoked action \"").append(actSlot.getName()).append("\" on \"").append(comp.getName()).append("\".\n");
        }
        else if ("batchBql".equalsIgnoreCase(actionName))
        {
          String queryStr = firstNonEmpty(action.get("query"), action.get("bql"));
          String subAction = firstNonEmpty(action.get("subAction"), "setSlotValue");
          List<BComponent> matched = resolveBqlComponents(baseComp, queryStr);
          if (matched.isEmpty())
          {
            logBuilder.append("Batch BQL matched 0 components for: ").append(queryStr).append("\n");
          }
          else
          {
            List<Map<String, String>> subPlan = new ArrayList<Map<String, String>>();
            for (int m = 0; m < matched.size(); m++)
            {
              BComponent mc = matched.get(m);
              BOrd ord = mc.getSlotPathOrd();
              String targetRef = ord != null ? ("station:|" + ord.toString()) : mc.getName();
              Map<String, String> item = new HashMap<String, String>(action);
              item.put("action", subAction);
              item.put("component", targetRef);
              item.put("target", targetRef);
              subPlan.add(item);
            }
            StringBuilder subLog = new StringBuilder();
            executePlanAgainstTarget(shell, baseComp, activeFile, subPlan, undoOps, subLog, isOfflineDuplicate);
            logBuilder.append("Batch BQL [").append(subAction).append("] applied to ")
              .append(matched.size()).append(" component(s).\n");
          }
        }
        else if ("updateProgram".equalsIgnoreCase(actionName))
        {
          String compStr = firstNonEmpty(action.get("component"), action.get("target"));
          String sourceCode = action.get("source");
          String imports = action.get("imports");

          BComponent comp = resolveComponent(baseComp, compStr);
          if (comp == null) throw new Exception("BProgram component not found: " + compStr);

          BValue codeVal = comp.get("code");
          if (!(codeVal instanceof BComplex))
          {
            throw new Exception("Component " + comp.getName() + " does not have a BProgramCode 'code' slot.");
          }
          final BComplex codeComplex = (BComplex) codeVal;
          final Property srcProp = codeComplex.getProperty("source");
          if (srcProp != null && sourceCode != null)
          {
            final BValue oldSrc = codeComplex.get(srcProp);
            BSimple proto = (BSimple) srcProp.getType().getInstance();
            codeComplex.set(srcProp, (BValue) proto.decodeFromString(sourceCode), null);
            undoOps.add(new Runnable()
            {
              @Override
              public void run()
              {
                try { codeComplex.set(srcProp, oldSrc, null); } catch (Throwable ignore) {}
              }
            });
          }
          Property impProp = codeComplex.getProperty("userDefinedImports");
          if (impProp != null && imports != null)
          {
            final Property finalImpProp = impProp;
            final BValue oldImp = codeComplex.get(impProp);
            BSimple proto = (BSimple) impProp.getType().getInstance();
            codeComplex.set(impProp, (BValue) proto.decodeFromString(imports), null);
            undoOps.add(new Runnable()
            {
              @Override
              public void run()
              {
                try { codeComplex.set(finalImpProp, oldImp, null); } catch (Throwable ignore) {}
              }
            });
          }

          // Attempt to invoke compile action if present on BProgram
          try
          {
            Action compileAct = comp.getAction("compile");
            if (compileAct != null)
            {
              comp.invoke(compileAct, null);
            }
          }
          catch (Throwable ignore) {}

          logBuilder.append("Updated Java source code on BProgram \"").append(comp.getName()).append("\".\n");
        }
        else if ("updatePxFile".equalsIgnoreCase(actionName)
          || "writeFile".equalsIgnoreCase(actionName)
          || "createFile".equalsIgnoreCase(actionName))
        {
          String contentStr = firstNonEmpty(action.get("xml"), action.get("content"), action.get("text"));
          String fileSpec = firstNonEmpty(action.get("file"), action.get("ord"), action.get("path"), "activeFile");
          byte[] newBytes = contentStr.getBytes(StandardCharsets.UTF_8);

          String writtenPath = writeAnyFileBytes(baseComp, activeFile, fileSpec, newBytes, undoOps);
          refreshFileViewIfNeeded(shell);
          logBuilder.append("Wrote file \"").append(writtenPath).append("\" (").append(newBytes.length).append(" bytes).\n");
        }
        else if ("patchFile".equalsIgnoreCase(actionName) || "editFile".equalsIgnoreCase(actionName))
        {
          String fileSpec = firstNonEmpty(action.get("file"), action.get("ord"), action.get("path"), "activeFile");
          String targetText = action.get("target");
          String replacementText = action.get("replacement") != null ? action.get("replacement") : "";

          if (targetText == null || targetText.isEmpty())
          {
            throw new Exception("patchFile requires a non-empty 'target' string to replace.");
          }

          byte[] oldBytes = readAnyFileBytes(baseComp, activeFile, fileSpec);
          if (oldBytes == null)
          {
            throw new Exception("Target file not found for patchFile: " + fileSpec);
          }

          String text = new String(oldBytes, StandardCharsets.UTF_8);
          if (!text.contains(targetText))
          {
            // Try normalized line endings (\r\n vs \n)
            String normText = text.replace("\r\n", "\n");
            String normTarget = targetText.replace("\r\n", "\n");
            if (!normText.contains(normTarget))
            {
              throw new Exception("Target snippet not found in file: " + fileSpec);
            }
            text = normText.replace(normTarget, replacementText);
          }
          else
          {
            text = text.replace(targetText, replacementText);
          }

          byte[] updatedBytes = text.getBytes(StandardCharsets.UTF_8);
          String patchedPath = writeAnyFileBytes(baseComp, activeFile, fileSpec, updatedBytes, undoOps);
          refreshFileViewIfNeeded(shell);
          logBuilder.append("Patched file \"").append(patchedPath).append("\".\n");
        }
        else if ("copyFile".equalsIgnoreCase(actionName))
        {
          String srcSpec = firstNonEmpty(action.get("source"), action.get("from"), action.get("file"));
          String dstSpec = firstNonEmpty(action.get("target"), action.get("to"));
          byte[] srcBytes = readAnyFileBytes(baseComp, activeFile, srcSpec);
          if (srcBytes == null) throw new Exception("Source file not found: " + srcSpec);
          String dstPath = writeAnyFileBytes(baseComp, activeFile, dstSpec, srcBytes, undoOps);
          logBuilder.append("Copied file \"").append(srcSpec).append("\" -> \"").append(dstPath).append("\".\n");
        }
        else if ("renameFile".equalsIgnoreCase(actionName))
        {
          String srcSpec = firstNonEmpty(action.get("file"), action.get("source"), action.get("oldName"));
          String dstSpec = firstNonEmpty(action.get("newName"), action.get("target"), action.get("to"));
          byte[] srcBytes = readAnyFileBytes(baseComp, activeFile, srcSpec);
          if (srcBytes == null) throw new Exception("Source file not found to rename: " + srcSpec);
          String dstPath = writeAnyFileBytes(baseComp, activeFile, dstSpec, srcBytes, undoOps);
          deleteAnyFile(baseComp, activeFile, srcSpec, undoOps);
          logBuilder.append("Renamed file \"").append(srcSpec).append("\" -> \"").append(dstPath).append("\".\n");
        }
        else if ("deleteFile".equalsIgnoreCase(actionName))
        {
          String fileSpec = firstNonEmpty(action.get("file"), action.get("ord"), action.get("path"));
          String delPath = deleteAnyFile(baseComp, activeFile, fileSpec, undoOps);
          logBuilder.append("Deleted file \"").append(delPath).append("\" (backed up for Undo).\n");
        }
        else if ("makeDir".equalsIgnoreCase(actionName))
        {
          String dirSpec = firstNonEmpty(action.get("dir"), action.get("path"), action.get("ord"));
          String createdDir = createAnyDirectory(baseComp, activeFile, dirSpec);
          logBuilder.append("Created directory \"").append(createdDir).append("\".\n");
        }
        else if ("importBog".equalsIgnoreCase(actionName))
        {
          String bogXml = firstNonEmpty(action.get("bog"), action.get("xml"));
          String parentStr = action.get("parent");
          final BComponent parent = (parentStr != null && !parentStr.trim().isEmpty())
            ? resolveComponent(baseComp, parentStr)
            : resolveContainerComponent(baseComp);

          if (parent == null)
          {
            throw new Exception("No target station container component for importBog.");
          }

          String sanitizedXml = enforceNiagaraProxyPointIdRule(bogXml);
          ValueDocDecoder decoder = new ValueDocDecoder(
            new ByteArrayInputStream(sanitizedXml.getBytes(StandardCharsets.UTF_8)));
          try
          {
            BValue decoded = decoder.decodeDocument();
            if (decoded instanceof BComponent)
            {
              BComponent rootDecoded = (BComponent) decoded;
              Property[] dynProps = rootDecoded.getDynamicPropertiesArray();
              int importedCount = 0;
              for (int pIdx = 0; pIdx < dynProps.length; pIdx++)
              {
                Property dp = dynProps[pIdx];
                final String slotName = dp.getName();
                BValue childCopy = rootDecoded.get(dp).newCopy();
                if (childCopy instanceof BComponent)
                {
                  BComponent childComp = (BComponent) childCopy;
                  int reqW = WiresheetLayoutEngine.computeRequiredWixelWidth(childComp, slotName);
                  BWsAnnotation initAnno = BWsAnnotation.make(3 + (pIdx % 4) * 14, 3 + (pIdx / 4) * 7, reqW);
                  if (childComp.get("wsAnnotation") == null)
                  {
                    childComp.add("wsAnnotation", initAnno);
                  }
                }
                BValue prevChild = parent.get(slotName);
                if (prevChild instanceof BComponent)
                {
                  Runnable restorePrev = StationUndoManager.createChildComponentRestoreUndo(
                    parent, (BComponent) prevChild);
                  if (restorePrev != null) undoOps.add(restorePrev);
                  parent.remove(slotName);
                }
                else if (prevChild != null)
                {
                  parent.remove(slotName);
                }

                parent.add(slotName, childCopy, rootDecoded.getFlags(dp), rootDecoded.getSlotFacets(dp), null);
                if (childCopy instanceof BComponent)
                {
                  recordTouchedComponentName(touchedNamesByContainer, parent, slotName);
                }
                if (!(prevChild instanceof BComponent))
                {
                  undoOps.add(new Runnable()
                  {
                    @Override
                    public void run()
                    {
                      try { parent.remove(slotName); } catch (Throwable ignore) {}
                    }
                  });
                }
                importedCount++;
              }
              logBuilder.append("Imported ").append(importedCount).append(" slot(s) from BOG XML into \"")
                .append(parent.getName()).append("\".\n");
            }
          }
          finally
          {
            try { decoder.close(); } catch (Throwable ignore) {}
          }
        }
        else if ("arrangeWiresheet".equalsIgnoreCase(actionName) || "layoutWiresheet".equalsIgnoreCase(actionName))
        {
          String parentStr = firstNonEmpty(action.get("parent"), action.get("component"), action.get("target"));
          BComponent container = (parentStr != null && !parentStr.trim().isEmpty())
            ? resolveComponent(baseComp, parentStr)
            : resolveContainerComponent(baseComp);
          if (container == null)
          {
            throw new Exception("No target WireSheet container component found for arrangeWiresheet.");
          }
          String layoutMsg = WiresheetLayoutEngine.arrangeWiresheet(container, undoOps);
          logBuilder.append(layoutMsg).append("\n");
          touchedNamesByContainer.remove(container);
        }
        else
        {
          throw new Exception("Unknown action type: " + actionName);
        }
      }
      catch (Exception e)
      {
        logBuilder.append("FAILED (").append(actionName).append("): ").append(e.getMessage()).append("\n");
      }
    }

    // Automatically run topological/grid WireSheet layout on any container where components/links were added
    for (Map.Entry<BComponent, Set<String>> entry : touchedNamesByContainer.entrySet())
    {
      try
      {
        BComponent container = entry.getKey();
        List<WiresheetLayoutEngine.ExplicitLink> expLinks = explicitLinksByContainer.get(container);
        String autoLayoutMsg = WiresheetLayoutEngine.arrangeComponentsInCleanSpace(
          container, entry.getValue(), expLinks, undoOps);
        if (autoLayoutMsg != null && !autoLayoutMsg.isEmpty())
        {
          logBuilder.append("[Layout] ").append(autoLayoutMsg).append("\n");
        }
      }
      catch (Throwable t)
      {
        logBuilder.append("[Layout Warning] ").append(t.getClass().getSimpleName())
          .append(": ").append(t.getMessage()).append("\n");
      }
    }
  }

  // ============================================================================
  // FILE READ / WRITE / PATCH / DELETE HELPERS (STATION & LOCAL MACHINE)
  // ============================================================================

  public static byte[] readAnyFileBytes(BComponent baseComp, BIFile activeFile, String fileSpec) throws Exception
  {
    String spec = fileSpec != null ? fileSpec.trim() : "activeFile";
    if (spec.isEmpty() || "activeFile".equalsIgnoreCase(spec) || "activePxFile".equalsIgnoreCase(spec))
    {
      if (activeFile != null)
      {
        return activeFile.read();
      }
      return null;
    }

    BIFile resolvedBajaFile = resolveBajaFileForRead(baseComp, activeFile, spec);
    if (resolvedBajaFile != null)
    {
      return resolvedBajaFile.read();
    }

    File localFile = resolveLocalOsFile(spec);
    if (localFile != null && localFile.isFile())
    {
      return StationInspector.readLocalFileBytes(localFile);
    }
    return null;
  }

  public static String writeAnyFileBytes(
    BComponent baseComp,
    BIFile activeFile,
    String fileSpec,
    byte[] newBytes,
    List<Runnable> undoOps) throws Exception
  {
    String spec = fileSpec != null ? fileSpec.trim() : "activeFile";
    if (spec.isEmpty() || "activeFile".equalsIgnoreCase(spec) || "activePxFile".equalsIgnoreCase(spec))
    {
      if (activeFile == null)
      {
        throw new Exception("No active file is currently open in Workbench.");
      }
      byte[] oldBytes = null;
      try { oldBytes = activeFile.read(); } catch (Throwable ignore) {}
      Runnable undo = StationUndoManager.createBajaFileRestoreUndo(activeFile, true, oldBytes);
      if (undo != null && undoOps != null) undoOps.add(undo);
      activeFile.write(newBytes);
      return String.valueOf(activeFile.getFilePath());
    }

    // 1. Station file (station:|file:^... or file:^... or ^...)
    if (spec.startsWith("station:|file:") || spec.startsWith("file:^") || spec.startsWith("^"))
    {
      BFileSpace stationSpace = EddiesDuplicateChooserDialog.resolveStationFileSpace(baseComp, activeFile);
      if (stationSpace == null)
      {
        throw new Exception("Connected station FileSpace not available to write: " + spec);
      }
      int caret = spec.indexOf('^');
      String relPath = caret >= 0 ? spec.substring(caret) : ("^" + spec);
      FilePath fp = new FilePath(relPath);
      BIFile existing = stationSpace.findFile(fp);
      boolean existed = (existing != null);
      byte[] prevBytes = null;
      if (existed)
      {
        try { prevBytes = existing.read(); } catch (Throwable ignore) {}
      }
      else
      {
        FilePath parentFp = fp.getParent();
        if (parentFp != null && stationSpace.findFile(parentFp) == null)
        {
          try { stationSpace.makeDir(parentFp); } catch (Throwable ignore) {}
        }
      }
      BIFile targetFile = existed ? existing : stationSpace.makeFile(fp);
      targetFile.write(newBytes);
      Runnable undo = StationUndoManager.createBajaFileRestoreUndo(targetFile, existed, prevBytes);
      if (undo != null && undoOps != null) undoOps.add(undo);
      return "station:|file:" + relPath;
    }

    // 2. Try resolving existing BIFile via BOrd
    BIFile existingBaja = resolveBajaFileForRead(baseComp, activeFile, spec);
    if (existingBaja != null)
    {
      byte[] oldBytes = null;
      try { oldBytes = existingBaja.read(); } catch (Throwable ignore) {}
      Runnable undo = StationUndoManager.createBajaFileRestoreUndo(existingBaja, true, oldBytes);
      if (undo != null && undoOps != null) undoOps.add(undo);
      existingBaja.write(newBytes);
      return String.valueOf(existingBaja.getFilePath());
    }

    // 3. Local file (file:! or local:|file: or Windows path)
    File localFile = resolveLocalOsFile(spec);
    if (localFile != null)
    {
      boolean existed = localFile.exists();
      byte[] prevBytes = existed ? StationInspector.readLocalFileBytes(localFile) : null;
      if (localFile.getParentFile() != null && !localFile.getParentFile().exists())
      {
        localFile.getParentFile().mkdirs();
      }
      FileOutputStream fos = new FileOutputStream(localFile);
      try
      {
        fos.write(newBytes);
        fos.flush();
      }
      finally
      {
        try { fos.close(); } catch (Throwable ignore) {}
      }
      Runnable undo = StationUndoManager.createLocalFileRestoreUndo(localFile, existed, prevBytes);
      if (undo != null && undoOps != null) undoOps.add(undo);
      return localFile.getAbsolutePath();
    }

    throw new Exception("Unable to resolve file destination: " + spec);
  }

  private static String deleteAnyFile(
    BComponent baseComp,
    BIFile activeFile,
    String fileSpec,
    List<Runnable> undoOps) throws Exception
  {
    BIFile bajaFile = resolveBajaFileForRead(baseComp, activeFile, fileSpec);
    if (bajaFile != null)
    {
      byte[] prevBytes = null;
      try { prevBytes = bajaFile.read(); } catch (Throwable ignore) {}
      final BFileSpace fs = bajaFile.getFileSpace();
      final FilePath fp = bajaFile.getFilePath();
      final byte[] backup = prevBytes;
      bajaFile.delete();
      if (fs != null && fp != null && backup != null && undoOps != null)
      {
        undoOps.add(new Runnable()
        {
          @Override
          public void run()
          {
            try
            {
              BIFile recreated = fs.makeFile(fp);
              recreated.write(backup);
            }
            catch (Throwable ignore) {}
          }
        });
      }
      return String.valueOf(fp);
    }

    File localFile = resolveLocalOsFile(fileSpec);
    if (localFile != null && localFile.exists())
    {
      byte[] prevBytes = localFile.isFile() ? StationInspector.readLocalFileBytes(localFile) : null;
      Runnable undo = StationUndoManager.createLocalFileRestoreUndo(localFile, true, prevBytes);
      if (undo != null && undoOps != null) undoOps.add(undo);
      localFile.delete();
      return localFile.getAbsolutePath();
    }

    throw new Exception("File not found to delete: " + fileSpec);
  }

  private static String createAnyDirectory(BComponent baseComp, BIFile activeFile, String dirSpec) throws Exception
  {
    String spec = dirSpec != null ? dirSpec.trim() : "";
    if (spec.startsWith("station:|file:") || spec.startsWith("file:^") || spec.startsWith("^"))
    {
      BFileSpace stationSpace = EddiesDuplicateChooserDialog.resolveStationFileSpace(baseComp, activeFile);
      if (stationSpace == null) throw new Exception("No connected station FileSpace.");
      int caret = spec.indexOf('^');
      String rel = caret >= 0 ? spec.substring(caret) : ("^" + spec);
      stationSpace.makeDir(new FilePath(rel));
      return "station:|file:" + rel;
    }

    File localDir = resolveLocalOsFile(spec);
    if (localDir != null)
    {
      localDir.mkdirs();
      return localDir.getAbsolutePath();
    }
    throw new Exception("Could not create directory: " + dirSpec);
  }

  private static BIFile resolveBajaFileForRead(BComponent baseComp, BIFile activeFile, String spec)
  {
    if (spec == null || spec.trim().isEmpty()) return activeFile;
    String s = spec.trim();
    if ("activeFile".equalsIgnoreCase(s) || "activePxFile".equalsIgnoreCase(s)) return activeFile;

    try
    {
      BObject baseObj = baseComp != null
        ? baseComp
        : (activeFile instanceof BObject
            ? (BObject) activeFile
            : (activeFile != null && activeFile.getFileSpace() != null
                ? activeFile.getFileSpace()
                : BFileSystem.INSTANCE));
      String ordStr = s.startsWith("^") ? ("station:|file:" + s) : s;
      if (ordStr.contains(":"))
      {
        BObject resolved = BOrd.make(ordStr).resolve(baseObj).get();
        if (resolved instanceof BIFile)
        {
          return (BIFile) resolved;
        }
      }
    }
    catch (Throwable ignore)
    {
    }

    if (s.startsWith("^") || s.startsWith("station:|file:") || s.startsWith("file:^"))
    {
      try
      {
        BFileSpace stationSpace = EddiesDuplicateChooserDialog.resolveStationFileSpace(baseComp, activeFile);
        if (stationSpace != null)
        {
          int caret = s.indexOf('^');
          String rel = caret >= 0 ? s.substring(caret) : ("^" + s);
          return stationSpace.findFile(new FilePath(rel));
        }
      }
      catch (Throwable ignore) {}
    }

    return null;
  }

  private static File resolveLocalOsFile(String spec)
  {
    if (spec == null || spec.trim().isEmpty()) return null;
    String s = spec.trim();
    if (s.startsWith("local:|file:"))
    {
      s = s.substring("local:|".length());
    }
    if (s.startsWith("file:!"))
    {
      String rel = s.substring("file:!".length());
      while (rel.startsWith("/") || rel.startsWith("\\")) rel = rel.substring(1);
      return new File(Sys.getNiagaraUserHome(), rel);
    }
    if (s.startsWith("file:"))
    {
      try
      {
        BObject obj = BOrd.make(s).resolve(BFileSystem.INSTANCE).get();
        if (obj instanceof BIFile)
        {
          File f = BFileSystem.INSTANCE.pathToLocalFile(((BIFile) obj).getFilePath());
          if (f != null) return f;
        }
      }
      catch (Throwable ignore) {}
    }
    if (s.indexOf(':') == 1 || s.startsWith("/") || s.startsWith("\\") || s.startsWith("."))
    {
      return new File(s);
    }
    return new File(Sys.getNiagaraUserHome(), s);
  }

  private static void refreshFileViewIfNeeded(BWbShell shell)
  {
    if (shell == null) return;
    try
    {
      BWbView view = shell.getActiveView();
      if (view != null)
      {
        view.relayout();
        view.repaint();
      }
    }
    catch (Throwable ignore)
    {
    }
  }

  private static Map<String, String> findPrimaryFileAction(List<Map<String, String>> plan)
  {
    if (plan == null) return null;
    for (int i = 0; i < plan.size(); i++)
    {
      Map<String, String> a = plan.get(i);
      String act = firstNonEmpty(a.get("action"), "");
      if ("updatePxFile".equalsIgnoreCase(act)
        || "writeFile".equalsIgnoreCase(act)
        || "createFile".equalsIgnoreCase(act)
        || "patchFile".equalsIgnoreCase(act)
        || "editFile".equalsIgnoreCase(act))
      {
        return a;
      }
    }
    return null;
  }

  private static boolean isPureFilePlan(List<Map<String, String>> plan)
  {
    if (plan == null || plan.isEmpty()) return false;
    for (int i = 0; i < plan.size(); i++)
    {
      String act = firstNonEmpty(plan.get(i).get("action"), "");
      if (!"updatePxFile".equalsIgnoreCase(act)
        && !"writeFile".equalsIgnoreCase(act)
        && !"createFile".equalsIgnoreCase(act)
        && !"patchFile".equalsIgnoreCase(act)
        && !"editFile".equalsIgnoreCase(act))
      {
        return false;
      }
    }
    return true;
  }

  private static String buildDuplicateFileName(String origName, String defaultExt)
  {
    String base = (origName != null && !origName.trim().isEmpty()) ? origName.trim() : "StationTarget";
    String ext = (defaultExt != null && !defaultExt.trim().isEmpty()) ? defaultExt.trim() : "bog";
    int dot = base.lastIndexOf('.');
    if (dot > 0)
    {
      ext = base.substring(dot + 1);
      base = base.substring(0, dot);
    }
    return base + "_Duplicate." + ext;
  }

  // ============================================================================
  // DEEP PROPERTY, FLAGS, ACTIONS, BQL, AND RESOLUTION HELPERS
  // ============================================================================

  private static String setDeepPropertyOnComponent(
    BComponent comp,
    String slotPath,
    String valueStr,
    List<Runnable> undoOps) throws Exception
  {
    if (slotPath == null || slotPath.trim().isEmpty())
    {
      throw new Exception("Missing slot/property name.");
    }
    String s = slotPath.trim();

    // Traverse arbitrary nested BComplex path (e.g. "alarmExt.offnormalAlgorithm.highLimit" or "proxyExt.pointId")
    if (s.indexOf('.') > 0 && !s.endsWith(".value") && !s.startsWith("in."))
    {
      String[] parts = s.split("\\.");
      BComplex currentComplex = comp;
      for (int i = 0; i < parts.length - 1; i++)
      {
        Slot nextSlot = resolveComplexSlot(currentComplex, parts[i]);
        if (nextSlot instanceof Property && currentComplex.get((Property) nextSlot) instanceof BComplex)
        {
          currentComplex = (BComplex) currentComplex.get((Property) nextSlot);
        }
        else
        {
          currentComplex = null;
          break;
        }
      }
      if (currentComplex != null)
      {
        String leafName = parts[parts.length - 1];
        Slot leafSlot = resolveComplexSlot(currentComplex, leafName);
        if (leafSlot instanceof Property)
        {
          return applyPropertyValue(currentComplex, (Property) leafSlot, valueStr, undoOps);
        }
      }
    }

    Slot slot = resolveSlot(comp, s);
    if (slot == null && ("value".equalsIgnoreCase(s) || "out.value".equalsIgnoreCase(s)))
    {
      slot = resolveSlot(comp, "fallback");
      if (slot == null) slot = resolveSlot(comp, "out");
    }
    if (slot == null || !slot.isProperty())
    {
      throw new Exception("Property not found: " + s + " on " + comp.getName());
    }
    return applyPropertyValue(comp, (Property) slot, valueStr, undoOps);
  }

  private static int parseFlagsString(String flagsStr, int baseFlags)
  {
    if (flagsStr == null || flagsStr.trim().isEmpty()) return baseFlags;
    String s = flagsStr.trim();
    int flags = s.startsWith("+") || s.startsWith("-") ? baseFlags : 0;

    String[] tokens = s.split("[,|\\s]+");
    for (int i = 0; i < tokens.length; i++)
    {
      String tok = tokens[i].trim();
      if (tok.isEmpty()) continue;
      boolean remove = tok.startsWith("-");
      if (tok.startsWith("+") || tok.startsWith("-"))
      {
        tok = tok.substring(1).trim();
      }
      int bit = flagNameToBit(tok);
      if (bit != 0)
      {
        flags = remove ? (flags & ~bit) : (flags | bit);
      }
      else
      {
        try
        {
          int decoded = Flags.decodeFromString(tok);
          flags = remove ? (flags & ~decoded) : (flags | decoded);
        }
        catch (Throwable ignore) {}
      }
    }
    return flags;
  }

  private static int flagNameToBit(String name)
  {
    if ("readonly".equalsIgnoreCase(name) || "r".equalsIgnoreCase(name)) return Flags.READONLY;
    if ("transient".equalsIgnoreCase(name) || "t".equalsIgnoreCase(name)) return Flags.TRANSIENT;
    if ("hidden".equalsIgnoreCase(name) || "h".equalsIgnoreCase(name)) return Flags.HIDDEN;
    if ("summary".equalsIgnoreCase(name) || "s".equalsIgnoreCase(name)) return Flags.SUMMARY;
    if ("async".equalsIgnoreCase(name) || "a".equalsIgnoreCase(name)) return Flags.ASYNC;
    if ("noRun".equalsIgnoreCase(name) || "n".equalsIgnoreCase(name)) return Flags.NO_RUN;
    if ("defaultOnClone".equalsIgnoreCase(name) || "d".equalsIgnoreCase(name)) return Flags.DEFAULT_ON_CLONE;
    if ("confirmRequired".equalsIgnoreCase(name) || "c".equalsIgnoreCase(name)) return Flags.CONFIRM_REQUIRED;
    if ("operator".equalsIgnoreCase(name) || "o".equalsIgnoreCase(name)) return Flags.OPERATOR;
    if ("executeOnChange".equalsIgnoreCase(name) || "x".equalsIgnoreCase(name)) return Flags.EXECUTE_ON_CHANGE;
    if ("fanIn".equalsIgnoreCase(name) || "f".equalsIgnoreCase(name)) return Flags.FAN_IN;
    if ("noAudit".equalsIgnoreCase(name)) return Flags.NO_AUDIT;
    if ("composite".equalsIgnoreCase(name)) return Flags.COMPOSITE;
    if ("metadata".equalsIgnoreCase(name) || "M".equals(name)) return Flags.METADATA;
    if ("linkTarget".equalsIgnoreCase(name) || "L".equals(name)) return Flags.LINK_TARGET;
    return 0;
  }

  private static BValue decodeActionArgument(Action actSlot, String argStr)
  {
    BValue defParam = actSlot.getParameterDefault();
    if (defParam == null)
    {
      return null;
    }
    if (argStr == null || argStr.trim().isEmpty())
    {
      return defParam.newCopy();
    }
    String s = argStr.trim();
    try
    {
      if (defParam instanceof BSimple)
      {
        return (BValue) ((BSimple) defParam).decodeFromString(s);
      }
      if (defParam instanceof BStruct)
      {
        BStruct structCopy = (BStruct) defParam.newCopy();
        Slot valSlot = resolveComplexSlot(structCopy, "value");
        if (valSlot instanceof Property)
        {
          applyPropertyValue(structCopy, (Property) valSlot, s, null);
        }
        return structCopy;
      }
    }
    catch (Throwable ignore)
    {
    }
    return defParam.newCopy();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static List<BComponent> resolveBqlComponents(BComponent baseComp, String bqlQuery)
  {
    List<BComponent> list = new ArrayList<BComponent>();
    if (bqlQuery == null || bqlQuery.trim().isEmpty()) return list;
    String q = bqlQuery.trim();
    if (!q.startsWith("station:|") && !q.startsWith("slot:") && !q.startsWith("bql:") && !q.startsWith("neql:"))
    {
      q = q.toLowerCase().startsWith("select ")
        ? ("station:|slot:/|bql:" + q)
        : ("station:|slot:/|bql:select * from " + q);
    }
    else if (q.startsWith("bql:") || q.startsWith("neql:"))
    {
      q = "station:|slot:/|" + q;
    }

    try
    {
      BObject baseObj = baseComp != null ? baseComp : Sys.getStation();
      BObject res = null;
      try
      {
        res = BOrd.make(q).resolve(baseObj).get();
      }
      catch (Throwable firstErr)
      {
        if (q.startsWith("station:|") && baseComp != null)
        {
          String bogRel = q.substring("station:|".length());
          res = BOrd.make(bogRel).resolve(baseComp).get();
        }
        else
        {
          throw firstErr;
        }
      }
      if (res instanceof BITable)
      {
        BITable table = (BITable) res;
        TableCursor cursor = table.cursor();
        try
        {
          while (cursor.next())
          {
            Object obj = cursor.get();
            if (obj instanceof BComponent)
            {
              list.add((BComponent) obj);
            }
          }
        }
        finally
        {
          try { cursor.close(); } catch (Throwable ignore) {}
        }
      }
      else if (res instanceof BComponent)
      {
        list.add((BComponent) res);
      }
    }
    catch (Throwable ignore)
    {
    }
    return list;
  }

  private static BComponent resolveMountedChild(BComponent parent, String childName, BComponent fallback)
  {
    if (parent != null && childName != null)
    {
      try
      {
        BValue v = parent.get(childName);
        if (v instanceof BComponent)
        {
          return (BComponent) v;
        }
        BComponent[] children = parent.getChildComponents();
        if (children != null)
        {
          for (int i = 0; i < children.length; i++)
          {
            if (children[i] != null && childName.equalsIgnoreCase(children[i].getName()))
            {
              return children[i];
            }
          }
        }
      }
      catch (Throwable ignore) {}
    }
    return fallback;
  }

  private static void recordTouchedComponentName(
    Map<BComponent, Set<String>> map,
    BComponent container,
    String compName)
  {
    if (container == null || compName == null || compName.trim().isEmpty()) return;
    Set<String> set = map.get(container);
    if (set == null)
    {
      set = new LinkedHashSet<String>();
      map.put(container, set);
    }
    set.add(compName.trim());
  }

  private static void recordExplicitLink(
    Map<BComponent, List<WiresheetLayoutEngine.ExplicitLink>> map,
    BComponent container,
    WiresheetLayoutEngine.ExplicitLink link)
  {
    if (container == null || link == null) return;
    List<WiresheetLayoutEngine.ExplicitLink> list = map.get(container);
    if (list == null)
    {
      list = new ArrayList<WiresheetLayoutEngine.ExplicitLink>();
      map.put(container, list);
    }
    list.add(link);
  }

  private static String applyPropertyValue(
    final BComplex target,
    final Property prop,
    String rawValueStr,
    List<Runnable> undoOps) throws Exception
  {
    String valueStr = (rawValueStr != null) ? rawValueStr.trim() : "";

    // Enforce Eddie's mandatory NiagaraProxyExt rule: pointId MUST include "station:|" before "slot:/..."
    if ("pointId".equals(prop.getName()) && target.getType().toString().contains("NiagaraProxyExt"))
    {
      if (valueStr.startsWith("slot:/"))
      {
        valueStr = "station:|" + valueStr;
      }
    }

    final BValue previousValue = target.get(prop) != null ? target.get(prop).newCopy() : null;
    if (previousValue != null && undoOps != null)
    {
      undoOps.add(new Runnable()
      {
        @Override
        public void run()
        {
          try { target.set(prop, previousValue, null); } catch (Throwable ignore) {}
        }
      });
    }

    BValue currentValue = target.get(prop);
    if (currentValue instanceof BStatusNumeric)
    {
      double num = Double.parseDouble(valueStr);
      target.set(prop, new BStatusNumeric(num), null);
      return Double.toString(num);
    }
    if (currentValue instanceof BStatusBoolean)
    {
      boolean bool = Boolean.parseBoolean(valueStr)
        || "1".equals(valueStr)
        || "active".equalsIgnoreCase(valueStr)
        || "on".equalsIgnoreCase(valueStr)
        || "true".equalsIgnoreCase(valueStr);
      target.set(prop, new BStatusBoolean(bool), null);
      return Boolean.toString(bool);
    }
    if (currentValue instanceof BStatusString)
    {
      target.set(prop, new BStatusString(valueStr), null);
      return valueStr;
    }
    if (currentValue instanceof BStatusEnum)
    {
      BStatusEnum curEnum = (BStatusEnum) currentValue;
      BEnum existingEnum = curEnum.getValue();
      if (existingEnum != null && existingEnum.getRange() != null)
      {
        BEnumRange range = existingEnum.getRange();
        BEnum resolvedEnum = null;
        if (range.isTag(valueStr))
        {
          resolvedEnum = range.get(valueStr);
        }
        else
        {
          try
          {
            resolvedEnum = range.get(Integer.parseInt(valueStr));
          }
          catch (NumberFormatException ignore) {}
        }
        if (resolvedEnum != null)
        {
          target.set(prop, new BStatusEnum(resolvedEnum), null);
          return resolvedEnum.getTag();
        }
      }
      int ordinal = Integer.parseInt(valueStr);
      target.set(prop, new BStatusEnum(BDynamicEnum.make(ordinal)), null);
      return Integer.toString(ordinal);
    }
    if (currentValue instanceof BSimple)
    {
      BSimple prototype = (BSimple) currentValue.getType().getInstance();
      BSimple newValue = (BSimple) prototype.decodeFromString(valueStr);
      target.set(prop, newValue, null);
      return valueStr;
    }

    throw new Exception("Unsupported editable property type on " + prop.getName());
  }

  private static void applyFacetsToComponent(
    final BComponent comp,
    String slotName,
    String facetsStr,
    List<Runnable> undoOps) throws Exception
  {
    if (facetsStr == null || facetsStr.trim().isEmpty()) return;
    BFacets decoded = BFacets.tryMake(facetsStr.trim());
    if (decoded == null || decoded.equals(BFacets.NULL))
    {
      throw new Exception("Invalid BFacets syntax: " + facetsStr);
    }

    Property facetsProp = comp.getProperty("facets");
    if (facetsProp != null && ("facets".equalsIgnoreCase(slotName) || "out".equalsIgnoreCase(slotName)))
    {
      final BValue oldVal = comp.get(facetsProp);
      comp.set(facetsProp, decoded, null);
      if (undoOps != null && oldVal != null)
      {
        final Property fp = facetsProp;
        undoOps.add(new Runnable()
        {
          @Override
          public void run()
          {
            try { comp.set(fp, oldVal, null); } catch (Throwable ignore) {}
          }
        });
      }
      return;
    }

    Slot slot = resolveSlot(comp, slotName);
    if (slot != null)
    {
      final Slot targetSlot = slot;
      final BFacets oldFacets = comp.getSlotFacets(targetSlot);
      comp.setFacets(targetSlot, decoded, null);
      if (undoOps != null)
      {
        undoOps.add(new Runnable()
        {
          @Override
          public void run()
          {
            try { comp.setFacets(targetSlot, oldFacets, null); } catch (Throwable ignore) {}
          }
        });
      }
    }
  }

  private static String enforceNiagaraProxyPointIdRule(String bogXml)
  {
    if (bogXml == null) return "";
    return bogXml.replace("n=\"pointId\" v=\"slot:/", "n=\"pointId\" v=\"station:|slot:/");
  }

  public static BComponent resolveContainerComponent(BComponent baseComp)
  {
    if (baseComp == null) return null;
    if ("program:Program".equals(baseComp.getType().toString()) && baseComp.getParentComponent() != null)
    {
      return baseComp.getParentComponent();
    }
    return baseComp;
  }

  public static BComponent resolveComponentAnywhere(BComponent baseComp, String s) throws Exception
  {
    return resolveComponent(baseComp, s);
  }

  private static BComponent resolveComponent(BComponent baseComp, String s) throws Exception
  {
    if (s == null || s.trim().isEmpty()
      || "activeTarget".equalsIgnoreCase(s.trim())
      || "this".equalsIgnoreCase(s.trim())
      || ".".equals(s.trim()))
    {
      return baseComp;
    }

    String clean = s.trim();
    if (baseComp != null && clean.equalsIgnoreCase(baseComp.getName()))
    {
      return baseComp;
    }

    if (clean.indexOf(':') >= 0)
    {
      BObject baseObj = baseComp != null ? baseComp : Sys.getStation();
      BObject obj = null;
      try
      {
        obj = BOrd.make(clean).resolve(baseObj).get();
      }
      catch (Throwable firstErr)
      {
        if (clean.startsWith("station:|") && baseComp != null)
        {
          String bogRel = clean.substring("station:|".length());
          obj = BOrd.make(bogRel).resolve(baseComp).get();
        }
        else
        {
          throw (firstErr instanceof Exception) ? (Exception) firstErr : new Exception(firstErr);
        }
      }
      if (obj instanceof BComponent)
      {
        return (BComponent) obj;
      }
    }

    if (baseComp != null)
    {
      // 1. Check direct child of baseComp
      BValue child = baseComp.get(clean);
      if (child instanceof BComponent)
      {
        return (BComponent) child;
      }

      // 2. Case-insensitive child lookup on baseComp
      BComponent[] children = baseComp.getChildComponents();
      if (children != null)
      {
        for (int i = 0; i < children.length; i++)
        {
          if (children[i].getName().equalsIgnoreCase(clean))
          {
            return children[i];
          }
        }
      }

      // 3. Check sibling on parent component
      BComponent parent = baseComp.getParentComponent();
      if (parent != null)
      {
        BValue sib = parent.get(clean);
        if (sib instanceof BComponent)
        {
          return (BComponent) sib;
        }
        BComponent[] siblings = parent.getChildComponents();
        if (siblings != null)
        {
          for (int i = 0; i < siblings.length; i++)
          {
            if (siblings[i].getName().equalsIgnoreCase(clean))
            {
              return siblings[i];
            }
          }
        }
      }

      // 4. Relative or absolute slot path (e.g. "SubFolder/Point1" or "/Drivers/...")
      if (clean.indexOf('/') >= 0)
      {
        try
        {
          String ordCandidate = clean.startsWith("/") ? ("station:|slot:" + clean) : ("slot:" + clean);
          BObject rel = BOrd.make(ordCandidate).resolve(baseComp).get();
          if (rel instanceof BComponent) return (BComponent) rel;
        }
        catch (Throwable ignore) {}
        try
        {
          BObject rel = BOrd.make("slot:" + clean).resolve(baseComp).get();
          if (rel instanceof BComponent) return (BComponent) rel;
        }
        catch (Throwable ignore) {}
      }
    }

    throw new Exception("Component not found by name or ORD: " + clean);
  }

  private static Slot resolveComplexSlot(BComplex comp, String slotName)
  {
    if (comp == null || slotName == null || slotName.trim().isEmpty()) return null;
    String s = slotName.trim();
    Slot slot = comp.getSlot(s);
    if (slot != null) return slot;

    SlotCursor<Slot> cursor = comp.getSlots();
    while (cursor.next())
    {
      Slot sl = cursor.slot();
      if (sl != null && sl.getName().equalsIgnoreCase(s))
      {
        return sl;
      }
    }
    return null;
  }

  private static Slot resolveSlot(BComponent comp, String slotName)
  {
    if (comp == null || slotName == null || slotName.trim().isEmpty())
      return null;

    String s = slotName.trim();

    Slot slot = comp.getSlot(s);
    if (slot != null) return slot;

    if (s.endsWith(".value"))
    {
      String base = s.substring(0, s.length() - 6);
      slot = comp.getSlot(base);
      if (slot != null) return slot;
    }

    if (s.startsWith("in.") && s.length() > 3)
    {
      slot = comp.getSlot(s.substring(3));
      if (slot != null) return slot;
    }

    SlotCursor<Slot> cursor = comp.getSlots();
    while (cursor.next())
    {
      Slot sl = cursor.slot();
      if (sl != null && sl.getName().equalsIgnoreCase(s))
      {
        return sl;
      }
    }

    return null;
  }

  private static Type resolveType(String typeStr)
  {
    if (typeStr == null || typeStr.trim().isEmpty())
      return null;

    String t = typeStr.trim();

    try
    {
      Type type = Sys.getType(t);
      if (type != null) return type;
    }
    catch (Throwable ignore) {}

    if (t.toLowerCase().startsWith("control:"))
    {
      String bare = t.substring(8);
      try
      {
        Type type = Sys.getType("kitControl:" + bare);
        if (type != null) return type;
      }
      catch (Throwable ignore) {}
    }

    if (!t.contains(":"))
    {
      String[] prefixes = {
        "kitControl:", "control:", "baja:", "driver:", "bacnet:",
        "nd:", "lon:", "modbus:", "schedule:", "alarm:", "history:", "program:"
      };
      for (int i = 0; i < prefixes.length; i++)
      {
        try
        {
          Type type = Sys.getType(prefixes[i] + t);
          if (type != null) return type;
        }
        catch (Throwable ignore) {}
      }
    }

    try
    {
      BTypeSpec spec = BTypeSpec.make(t);
      if (spec != null)
      {
        Type resolved = spec.getResolvedType();
        if (resolved != null) return resolved;
      }
    }
    catch (Throwable ignore) {}

    return null;
  }

  // ============================================================================
  // JSON ACTION PLAN PARSER
  // ============================================================================

  private static String extractJsonArray(String text)
  {
    if (text == null) return null;
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');
    if (start >= 0 && end > start)
    {
      return text.substring(start, end + 1).trim();
    }
    return null;
  }

  private static List<Map<String, String>> parseActionPlan(String json) throws Exception
  {
    List<Map<String, String>> actions = new ArrayList<Map<String, String>>();
    int index = 0;
    while (true)
    {
      int startBrace = json.indexOf('{', index);
      if (startBrace < 0) break;

      int endBrace = findMatchingBrace(json, startBrace);
      if (endBrace < 0)
      {
        throw new Exception("Malformed JSON array: unclosed object brace.");
      }

      String objectStr = json.substring(startBrace + 1, endBrace);
      actions.add(parseJsonObject(objectStr));
      index = endBrace + 1;
    }
    return actions;
  }

  private static Map<String, String> parseJsonObject(String str) throws Exception
  {
    Map<String, String> map = new HashMap<String, String>();
    int i = 0;
    while (i < str.length())
    {
      int keyStartQuote = str.indexOf('"', i);
      if (keyStartQuote < 0) break;

      int keyEndQuote = str.indexOf('"', keyStartQuote + 1);
      if (keyEndQuote < 0) throw new Exception("Malformed JSON object key.");

      String key = str.substring(keyStartQuote + 1, keyEndQuote);

      int colon = str.indexOf(':', keyEndQuote + 1);
      if (colon < 0) throw new Exception("Malformed JSON: missing colon after key: " + key);

      int valueStart = findNonWhitespace(str, colon + 1);
      if (valueStart < 0) throw new Exception("Malformed JSON: missing value for key: " + key);

      String value;
      int nextIndex;
      if (str.charAt(valueStart) == '"')
      {
        int valueEndQuote = findJsonStringEnd(str, valueStart + 1);
        if (valueEndQuote < 0) throw new Exception("Malformed JSON: unclosed string value for key: " + key);
        value = unescapeJson(str.substring(valueStart + 1, valueEndQuote));
        nextIndex = valueEndQuote + 1;
      }
      else
      {
        int comma = str.indexOf(',', valueStart);
        int end = (comma >= 0) ? comma : str.length();
        value = str.substring(valueStart, end).trim();
        nextIndex = end;
      }

      map.put(key, value);
      i = nextIndex;
    }
    return map;
  }

  private static int findMatchingBrace(String str, int start)
  {
    int depth = 0;
    boolean inString = false;
    boolean escaping = false;
    for (int i = start; i < str.length(); ++i)
    {
      char c = str.charAt(i);
      if (inString)
      {
        if (escaping) escaping = false;
        else if (c == '\\') escaping = true;
        else if (c == '"') inString = false;
      }
      else
      {
        if (c == '"') inString = true;
        else if (c == '{') depth++;
        else if (c == '}')
        {
          depth--;
          if (depth == 0) return i;
        }
      }
    }
    return -1;
  }

  private static int findNonWhitespace(String str, int start)
  {
    for (int i = start; i < str.length(); ++i)
    {
      char c = str.charAt(i);
      if (c != ' ' && c != '\t' && c != '\r' && c != '\n')
        return i;
    }
    return -1;
  }

  static int findJsonStringEnd(String json, int start)
  {
    boolean escaping = false;
    for (int i = start; i < json.length(); ++i)
    {
      char c = json.charAt(i);
      if (escaping)
      {
        escaping = false;
      }
      else if (c == '\\')
      {
        escaping = true;
      }
      else if (c == '"')
      {
        return i;
      }
    }
    return -1;
  }

  static String unescapeJson(String value)
  {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); ++i)
    {
      char c = value.charAt(i);
      if (c != '\\')
      {
        out.append(c);
        continue;
      }
      if (i + 1 >= value.length()) break;
      char next = value.charAt(++i);
      switch (next)
      {
        case '"': out.append('"'); break;
        case '\\': out.append('\\'); break;
        case '/': out.append('/'); break;
        case 'b': out.append('\b'); break;
        case 'f': out.append('\f'); break;
        case 'n': out.append('\n'); break;
        case 'r': out.append('\r'); break;
        case 't': out.append('\t'); break;
        case 'u':
          if (i + 4 < value.length())
          {
            try
            {
              out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
              i += 4;
            }
            catch (NumberFormatException ignore) {}
          }
          break;
        default:
          out.append(next);
          break;
      }
    }
    return out.toString();
  }

  private static String firstNonEmpty(String... values)
  {
    if (values == null) return "";
    for (int i = 0; i < values.length; i++)
    {
      if (values[i] != null && !values[i].trim().isEmpty())
        return values[i].trim();
    }
    return "";
  }
}
