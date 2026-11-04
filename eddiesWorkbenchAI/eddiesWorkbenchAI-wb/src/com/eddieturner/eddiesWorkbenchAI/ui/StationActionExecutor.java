/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.baja.file.BIFile;
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
import javax.baja.sys.BLink;
import javax.baja.sys.BObject;
import javax.baja.sys.BSimple;
import javax.baja.sys.BValue;
import javax.baja.sys.Property;
import javax.baja.sys.Slot;
import javax.baja.sys.SlotCursor;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.util.BTypeSpec;
import javax.baja.util.BWsAnnotation;
import javax.baja.workbench.BWbShell;

/**
 * Parses and executes AI-generated station modification plans against the active
 * Niagara Station view in Workbench, with Preview summary and 1-click Undo support.
 */
public final class StationActionExecutor
{
  private static final List<Runnable> LAST_UNDO_STACK = new ArrayList<Runnable>();

  private StationActionExecutor()
  {
  }

  public static boolean hasUndo()
  {
    synchronized (LAST_UNDO_STACK)
    {
      return !LAST_UNDO_STACK.isEmpty();
    }
  }

  public static String undoLastBatch()
  {
    List<Runnable> ops;
    synchronized (LAST_UNDO_STACK)
    {
      if (LAST_UNDO_STACK.isEmpty())
      {
        return "Nothing to undo.";
      }
      ops = new ArrayList<Runnable>(LAST_UNDO_STACK);
      LAST_UNDO_STACK.clear();
    }

    int count = 0;
    for (int i = ops.size() - 1; i >= 0; i--)
    {
      try
      {
        ops.get(i).run();
        count++;
      }
      catch (Throwable ignore)
      {
      }
    }
    return "Reverted " + count + " operation(s) from the last AI station modification.";
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

    // 2. Otherwise look for a JSON array containing "action" keys
    if (jsonCandidate == null)
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

    try
    {
      List<Map<String, String>> parsed = parseActionPlan(jsonCandidate);
      List<Map<String, String>> valid = new ArrayList<Map<String, String>>();
      for (int i = 0; i < parsed.size(); i++)
      {
        Map<String, String> item = parsed.get(i);
        if (item.get("action") != null && !item.get("action").trim().isEmpty())
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

  /**
   * Strips the raw <station-actions>...</station-actions> block from the display text if present
   * so the conversation stays clean and readable.
   */
  public static String stripActionTagsForDisplay(String responseText)
  {
    if (responseText == null) return "";
    int tagStart = responseText.indexOf("<station-actions>");
    int tagEnd = responseText.indexOf("</station-actions>");
    if (tagStart >= 0 && tagEnd > tagStart)
    {
      String before = responseText.substring(0, tagStart).trim();
      String after = responseText.substring(tagEnd + "</station-actions>".length()).trim();
      if (before.isEmpty() && after.isEmpty())
      {
        return "(Station modification plan ready)";
      }
      return (before + "\n" + after).trim();
    }
    return responseText.trim();
  }

  /**
   * Formats a human-readable preview summary of the proposed station modifications.
   */
  public static String summarizePlan(List<Map<String, String>> plan)
  {
    if (plan == null || plan.isEmpty()) return "No station actions.";
    StringBuilder sb = new StringBuilder();
    sb.append("Proposed Station Modifications (").append(plan.size()).append(" action(s)):\n");
    for (int i = 0; i < plan.size(); i++)
    {
      Map<String, String> a = plan.get(i);
      String act = a.get("action");
      sb.append("  ").append(i + 1).append(". [").append(act).append("] ");
      if ("createComponent".equalsIgnoreCase(act) || "addComponent".equalsIgnoreCase(act))
      {
        sb.append(firstNonEmpty(a.get("name"), "?"))
          .append(" (").append(firstNonEmpty(a.get("type"), "?")).append(")");
        if (a.get("x") != null && a.get("y") != null)
        {
          sb.append(" at (x=").append(a.get("x")).append(", y=").append(a.get("y")).append(")");
        }
        if (a.get("value") != null)
        {
          sb.append(" value=").append(a.get("value"));
        }
      }
      else if ("setSlotValue".equalsIgnoreCase(act) || "setProperty".equalsIgnoreCase(act))
      {
        sb.append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"))
          .append(".").append(firstNonEmpty(a.get("slot"), a.get("property"), "?"))
          .append(" = ").append(a.get("value"));
      }
      else if ("setFacets".equalsIgnoreCase(act))
      {
        sb.append(firstNonEmpty(a.get("component"), a.get("target"), "activeTarget"))
          .append(".").append(firstNonEmpty(a.get("slot"), "out"))
          .append(" facets=").append(a.get("facets"));
      }
      else if ("link".equalsIgnoreCase(act) || "addLink".equalsIgnoreCase(act))
      {
        sb.append(firstNonEmpty(a.get("source"), a.get("from"), "?"))
          .append(".").append(firstNonEmpty(a.get("sourceSlot"), a.get("fromSlot"), "out"))
          .append(" -> ")
          .append(firstNonEmpty(a.get("target"), a.get("to"), "?"))
          .append(".").append(firstNonEmpty(a.get("targetSlot"), a.get("toSlot"), "inA"));
      }
      else if ("unlink".equalsIgnoreCase(act) || "removeLink".equalsIgnoreCase(act))
      {
        sb.append("Unlink ").append(firstNonEmpty(a.get("source"), "*"))
          .append(" -> ").append(firstNonEmpty(a.get("target"), "activeTarget"))
          .append(".").append(firstNonEmpty(a.get("targetSlot"), "*"));
      }
      else if ("renameComponent".equalsIgnoreCase(act) || "renameSlot".equalsIgnoreCase(act))
      {
        sb.append(firstNonEmpty(a.get("component"), a.get("oldName"), "?"))
          .append(" -> ").append(firstNonEmpty(a.get("newName"), "?"));
      }
      else if ("deleteComponent".equalsIgnoreCase(act) || "deleteSlot".equalsIgnoreCase(act))
      {
        sb.append("Delete ").append(firstNonEmpty(a.get("component"), a.get("name"), a.get("slot"), "?"));
      }
      else if ("updateProgram".equalsIgnoreCase(act))
      {
        sb.append("Update Java source on BProgram ").append(firstNonEmpty(a.get("component"), "activeTarget"));
      }
      else if ("updatePxFile".equalsIgnoreCase(act))
      {
        sb.append("Update PX graphic file ").append(firstNonEmpty(a.get("file"), "activePxFile"));
      }
      else if ("importBog".equalsIgnoreCase(act))
      {
        sb.append("Import BOG XML component graph into ").append(firstNonEmpty(a.get("parent"), "activeTarget"));
      }
      else if ("arrangeWiresheet".equalsIgnoreCase(act) || "layoutWiresheet".equalsIgnoreCase(act))
      {
        sb.append("Arrange WireSheet layout on ").append(firstNonEmpty(a.get("parent"), a.get("component"), "activeTarget"));
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
      synchronized (LAST_UNDO_STACK)
      {
        LAST_UNDO_STACK.clear();
        LAST_UNDO_STACK.addAll(undoOps);
      }
    }
    WiresheetLayoutEngine.refreshActiveWiresheetView(shell);
    return msg;
  }

  /**
   * Executes the parsed action plan against the active station view in the given BWbShell.
   */
  public static String executePlan(BWbShell shell, List<Map<String, String>> plan)
  {
    if (plan == null || plan.isEmpty())
    {
      return "No station actions to execute.";
    }

    StationContextExtractor.StationTarget ctx = StationContextExtractor.extract(shell);
    BComponent baseComp = ctx.targetComponent;
    BIFile activePxFile = ctx.pxFile;

    final List<Runnable> undoOps = new ArrayList<Runnable>();
    final Map<BComponent, Set<String>> touchedNamesByContainer = new LinkedHashMap<BComponent, Set<String>>();
    final Map<BComponent, List<WiresheetLayoutEngine.ExplicitLink>> explicitLinksByContainer =
      new LinkedHashMap<BComponent, List<WiresheetLayoutEngine.ExplicitLink>>();
    StringBuilder logBuilder = new StringBuilder();
    logBuilder.append("Executing ").append(plan.size()).append(" station action(s)...\n");

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

          // Pre-populate facets and value on newComp BEFORE parent.add so initial Fox serialization includes them
          if (initFacets != null && !initFacets.trim().isEmpty())
          {
            try
            {
              applyFacetsToComponent(newComp, "facets", initFacets, null);
            }
            catch (Throwable ignore)
            {
            }
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
            catch (Throwable ignore)
            {
            }
          }

          if (parent.get(addedName) != null)
          {
            parent.remove(addedName);
            logBuilder.append("Replaced existing \"").append(addedName).append("\". ");
          }

          parent.add(addedName, newComp);
          undoOps.add(new Runnable()
          {
            @Override
            public void run()
            {
              try { parent.remove(addedName); } catch (Throwable ignore) {}
            }
          });

          // Re-fetch the mounted component from parent (critical when parent is a remote Fox station proxy)
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

          // Check for nested property path such as "proxyExt.pointId" or "code.source"
          if (slotName.indexOf('.') > 0 && !slotName.endsWith(".value") && !slotName.startsWith("in."))
          {
            int dot = slotName.indexOf('.');
            String parentSlotName = slotName.substring(0, dot);
            String childPropName = slotName.substring(dot + 1);
            Slot parentSlot = resolveSlot(comp, parentSlotName);
            if (parentSlot instanceof Property && comp.get((Property) parentSlot) instanceof BComplex)
            {
              BComplex nested = (BComplex) comp.get((Property) parentSlot);
              Slot childSlot = resolveComplexSlot(nested, childPropName);
              if (childSlot instanceof Property)
              {
                String desc = applyPropertyValue(nested, (Property) childSlot, valueStr, undoOps);
                logBuilder.append("Set ").append(comp.getName()).append(".").append(slotName)
                  .append(" -> ").append(desc).append(".\n");
                continue;
              }
            }
          }

          Slot slot = resolveSlot(comp, slotName);
          if (slot == null && "value".equalsIgnoreCase(slotName))
          {
            slot = resolveSlot(comp, "fallback");
            if (slot == null) slot = resolveSlot(comp, "out");
          }
          if (slot == null || !slot.isProperty())
          {
            throw new Exception("Property not found: " + slotName + " on " + comp.getName());
          }

          Property prop = (Property) slot;
          String desc = applyPropertyValue(comp, prop, valueStr, undoOps);
          logBuilder.append("Set \"").append(prop.getName()).append("\" on \"")
            .append(comp.getName()).append("\" to ").append(desc).append(".\n");
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
                catch (Throwable ignore)
                {
                }
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
        else if ("deleteComponent".equalsIgnoreCase(actionName) || "deleteSlot".equalsIgnoreCase(actionName))
        {
          String compStr = firstNonEmpty(action.get("component"), action.get("name"), action.get("slot"));
          BComponent comp = resolveComponent(baseComp, compStr);
          if (comp != null && comp.getParentComponent() != null)
          {
            BComponent parent = comp.getParentComponent();
            String removedName = comp.getName();
            parent.remove(removedName);
            logBuilder.append("Deleted component \"").append(removedName).append("\".\n");
          }
          else if (baseComp != null && baseComp.getProperty(compStr) != null)
          {
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
          BComponent comp = resolveComponent(baseComp, compStr);
          if (comp == null) throw new Exception("Target component not found: " + compStr);

          Action actSlot = comp.getAction(actSlotName);
          if (actSlot == null) throw new Exception("Action slot not found: " + actSlotName + " on " + comp.getName());

          comp.invoke(actSlot, null);
          logBuilder.append("Invoked action \"").append(actSlotName).append("\" on \"").append(comp.getName()).append("\".\n");
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
            BSimple proto = (BSimple) impProp.getType().getInstance();
            codeComplex.set(impProp, (BValue) proto.decodeFromString(imports), null);
          }
          logBuilder.append("Updated Java source code on BProgram \"").append(comp.getName()).append("\".\n");
        }
        else if ("updatePxFile".equalsIgnoreCase(actionName))
        {
          String xmlContent = firstNonEmpty(action.get("xml"), action.get("content"));
          String fileOrdStr = action.get("file");
          BIFile targetFile = activePxFile;
          if (targetFile == null && fileOrdStr != null && !fileOrdStr.isEmpty())
          {
            BObject resolved = BOrd.make(fileOrdStr).resolve(baseComp).get();
            if (resolved instanceof BIFile)
            {
              targetFile = (BIFile) resolved;
            }
          }
          if (targetFile == null)
          {
            throw new Exception("No active PX file found to update.");
          }
          final BIFile finalFile = targetFile;
          final byte[] oldBytes = finalFile.read();
          finalFile.write(xmlContent.getBytes(StandardCharsets.UTF_8));
          undoOps.add(new Runnable()
          {
            @Override
            public void run()
            {
              try { finalFile.write(oldBytes); } catch (Throwable ignore) {}
            }
          });
          logBuilder.append("Updated PX file \"").append(finalFile.getFilePath()).append("\".\n");
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
                if (parent.get(slotName) != null)
                {
                  parent.remove(slotName);
                }
                parent.add(slotName, childCopy, rootDecoded.getFlags(dp), rootDecoded.getSlotFacets(dp), null);
                if (childCopy instanceof BComponent)
                {
                  recordTouchedComponentName(touchedNamesByContainer, parent, slotName);
                }
                undoOps.add(new Runnable()
                {
                  @Override
                  public void run()
                  {
                    try { parent.remove(slotName); } catch (Throwable ignore) {}
                  }
                });
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

    if (!undoOps.isEmpty())
    {
      synchronized (LAST_UNDO_STACK)
      {
        LAST_UNDO_STACK.clear();
        LAST_UNDO_STACK.addAll(undoOps);
      }
    }

    WiresheetLayoutEngine.refreshActiveWiresheetView(shell);
    return logBuilder.toString();
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
      catch (Throwable ignore)
      {
      }
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

  // ============================================================================
  // PROPERTY, FACETS, AND RESOLUTION HELPERS
  // ============================================================================

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

  private static BComponent resolveContainerComponent(BComponent baseComp)
  {
    if (baseComp == null) return null;
    if ("program:Program".equals(baseComp.getType().toString()) && baseComp.getParentComponent() != null)
    {
      return baseComp.getParentComponent();
    }
    return baseComp;
  }

  private static BComponent resolveComponent(BComponent baseComp, String s) throws Exception
  {
    if (s == null || s.trim().isEmpty() || "activeTarget".equalsIgnoreCase(s.trim()) || "this".equalsIgnoreCase(s.trim()) || ".".equals(s.trim()))
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
      BObject obj = BOrd.make(clean).resolve(baseComp).get();
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

      // 4. Relative slot path (e.g. "SubFolder/Point1")
      if (clean.indexOf('/') >= 0)
      {
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
