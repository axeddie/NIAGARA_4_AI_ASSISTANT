/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.baja.file.BIFile;
import javax.baja.naming.BOrd;
import javax.baja.naming.OrdTarget;
import javax.baja.space.Mark;
import javax.baja.status.BStatusValue;
import javax.baja.sys.Action;
import javax.baja.sys.BComplex;
import javax.baja.sys.BComponent;
import javax.baja.sys.BFacets;
import javax.baja.sys.BLink;
import javax.baja.sys.BObject;
import javax.baja.sys.BSimple;
import javax.baja.sys.BValue;
import javax.baja.sys.Property;
import javax.baja.sys.Slot;
import javax.baja.ui.transfer.BTransferWidget;
import javax.baja.ui.transfer.TransferEnvelope;
import javax.baja.ui.transfer.TransferFormat;
import javax.baja.util.BWsAnnotation;
import javax.baja.workbench.BWbShell;
import javax.baja.workbench.px.BWbPxView;
import javax.baja.workbench.view.BWbView;

/**
 * Extracts rich, structured context from the active Niagara Station view in Workbench
 * so the AI can understand the exact WireSheet, component hierarchy, selected points,
 * BProgram code, or PX graphic currently showing on screen.
 */
public final class StationContextExtractor
{
  public static final class StationTarget
  {
    public final BOrd activeOrd;
    public final BWbView activeView;
    public final BComponent targetComponent;
    public final BIFile pxFile;
    public final List<BComponent> selectedComponents;
    public final String summaryLabel;
    public final String fullContextPromptBlock;

    StationTarget(
      BOrd activeOrd,
      BWbView activeView,
      BComponent targetComponent,
      BIFile pxFile,
      List<BComponent> selectedComponents,
      String summaryLabel,
      String fullContextPromptBlock)
    {
      this.activeOrd = activeOrd;
      this.activeView = activeView;
      this.targetComponent = targetComponent;
      this.pxFile = pxFile;
      this.selectedComponents = selectedComponents;
      this.summaryLabel = summaryLabel;
      this.fullContextPromptBlock = fullContextPromptBlock;
    }
  }

  private StationContextExtractor()
  {
  }

  /**
   * Inspects the active Workbench shell and builds a complete StationTarget snapshot.
   */
  public static StationTarget extract(BWbShell shell)
  {
    if (shell == null)
    {
      return new StationTarget(
        null, null, null, null, new ArrayList<BComponent>(),
        "None", "Active Workbench Target: None");
    }

    BOrd activeOrd = null;
    BWbView activeView = null;
    BComponent targetComp = null;
    BIFile pxFile = null;
    List<BComponent> selectedComps = new ArrayList<BComponent>();

    try
    {
      activeOrd = shell.getActiveOrd();
      activeView = shell.getActiveView();

      if (activeView instanceof BWbPxView)
      {
        try
        {
          pxFile = ((BWbPxView) activeView).getPxFile();
        }
        catch (Throwable ignore) {}
      }

      BObject curVal = (activeView != null) ? activeView.getCurrentValue() : null;
      if (curVal instanceof BIFile && pxFile == null)
      {
        BIFile f = (BIFile) curVal;
        if ("px".equalsIgnoreCase(f.getExtension()))
        {
          pxFile = f;
        }
      }

      if (curVal instanceof BComponent)
      {
        targetComp = (BComponent) curVal;
      }
      else if (curVal instanceof BComplex)
      {
        targetComp = ((BComplex) curVal).getParentComponent();
      }

      if (targetComp == null && activeOrd != null)
      {
        try
        {
          OrdTarget resolved = shell.getActiveOrdTarget();
          if (resolved != null)
          {
            BObject obj = resolved.get();
            if (obj instanceof BComponent)
            {
              targetComp = (BComponent) obj;
            }
            else if (resolved.getComponent() != null)
            {
              targetComp = resolved.getComponent();
            }
          }
        }
        catch (Throwable ignore) {}
      }

      selectedComps = extractSelectedComponents(activeView);

      // Opportunistically check if a Program component with Model Farm credentials is visible
      // and EddiesAiConfig does not yet have a key configured
      if (targetComp != null && EddiesAiConfig.get().getModelFarmApiKey().isEmpty())
      {
        tryAutoImportCredentials(targetComp);
      }
    }
    catch (Throwable ignore)
    {
    }

    String ordStr = (activeOrd != null) ? activeOrd.toString() : "None";
    String shortOrd = ordStr;
    if (shortOrd.length() > 38)
    {
      shortOrd = "..." + shortOrd.substring(shortOrd.length() - 35);
    }

    String summaryLabel = shortOrd;
    if (targetComp != null)
    {
      String compName = targetComp.getName();
      if (compName == null || compName.isEmpty()) compName = targetComp.getType().getTypeName();
      summaryLabel = compName + " [" + targetComp.getType().toString() + "]";
      if (!selectedComps.isEmpty())
      {
        summaryLabel += " (+" + selectedComps.size() + " sel)";
      }
    }
    else if (pxFile != null)
    {
      summaryLabel = pxFile.getFileName() + " [PX Graphic]";
    }

    String promptBlock = buildPromptBlock(activeOrd, activeView, targetComp, pxFile, selectedComps);
    return new StationTarget(activeOrd, activeView, targetComp, pxFile, selectedComps, summaryLabel, promptBlock);
  }

  /**
   * Scans the active target component (and its immediate children) for StationBuilder / ModelFarm
   * Program Object slots (ApiKey, Domain, SubscriptionId) and imports them into EddiesAiConfig.
   */
  public static boolean tryImportCredentialsFromShell(BWbShell shell)
  {
    if (shell == null) return false;
    StationTarget st = extract(shell);
    if (st.targetComponent != null)
    {
      return tryAutoImportCredentials(st.targetComponent);
    }
    return false;
  }

  private static boolean tryAutoImportCredentials(BComponent comp)
  {
    if (comp == null) return false;
    if (importFromComponentSlots(comp)) return true;

    BComponent[] children = comp.getChildComponents();
    if (children != null)
    {
      for (int i = 0; i < children.length; i++)
      {
        if (importFromComponentSlots(children[i])) return true;
      }
    }
    return false;
  }

  private static boolean importFromComponentSlots(BComponent c)
  {
    if (c == null) return false;
    try
    {
      Property apiKeyProp = findPropertyIgnoreCase(c, "ApiKey");
      if (apiKeyProp == null) return false;

      BValue val = c.get(apiKeyProp);
      if (val == null) return false;
      String key = EddiesAiConfig.sanitizeKey(val.toString());
      if (key.isEmpty()) return false;

      EddiesAiConfig cfg = EddiesAiConfig.get();
      cfg.setModelFarmApiKey(key);

      Property domainProp = findPropertyIgnoreCase(c, "Domain");
      if (domainProp != null && c.get(domainProp) != null)
      {
        String dom = c.get(domainProp).toString().trim();
        if (!dom.isEmpty()) cfg.setModelFarmDomain(dom);
      }

      Property subIdProp = findPropertyIgnoreCase(c, "SubscriptionId");
      if (subIdProp != null && c.get(subIdProp) != null)
      {
        String subId = c.get(subIdProp).toString().trim();
        if (!subId.isEmpty()) cfg.setModelFarmSubscriptionId(subId);
      }

      cfg.save();
      return true;
    }
    catch (Throwable ignore)
    {
      return false;
    }
  }

  private static Property findPropertyIgnoreCase(BComplex c, String name)
  {
    Property exact = c.getProperty(name);
    if (exact != null) return exact;
    Property[] props = c.getPropertiesArray();
    for (int i = 0; i < props.length; i++)
    {
      if (props[i].getName().equalsIgnoreCase(name))
      {
        return props[i];
      }
    }
    return null;
  }

  private static List<BComponent> extractSelectedComponents(BWbView activeView)
  {
    List<BComponent> list = new ArrayList<BComponent>();
    if (activeView == null) return list;

    try
    {
      Mark mark = null;
      BTransferWidget tw = activeView.getTransferWidget();
      if (tw != null)
      {
        TransferEnvelope env = tw.getTransferData();
        if (env != null && env.supports(TransferFormat.mark))
        {
          Object data = env.getData(TransferFormat.mark);
          if (data instanceof Mark)
          {
            mark = (Mark) data;
          }
        }
      }

      if (mark != null && mark.size() > 0)
      {
        BObject[] objs = mark.getValues();
        if (objs != null)
        {
          for (int i = 0; i < objs.length; i++)
          {
            if (objs[i] instanceof BComponent)
            {
              list.add((BComponent) objs[i]);
            }
          }
        }
      }
    }
    catch (Throwable ignore)
    {
    }
    return list;
  }

  private static String buildPromptBlock(
    BOrd activeOrd,
    BWbView activeView,
    BComponent targetComp,
    BIFile pxFile,
    List<BComponent> selectedComps)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ACTIVE NIAGARA WORKBENCH VIEW CONTEXT ===\n");
    sb.append("Active ORD: ").append(activeOrd != null ? activeOrd.toString() : "None").append("\n");
    if (activeView != null)
    {
      sb.append("Active View Type: ").append(activeView.getType().toString()).append("\n");
    }

    if (targetComp != null)
    {
      BOrd slotOrd = targetComp.getSlotPathOrd();
      sb.append("Target Component Name: ").append(safeName(targetComp)).append("\n");
      sb.append("Target Component Type: ").append(targetComp.getType().toString()).append("\n");
      sb.append("Target SlotPath ORD: ").append(slotOrd != null ? "station:|" + slotOrd.toString() : "unmounted").append("\n");

      BComponent parent = targetComp.getParentComponent();
      if (parent != null)
      {
        BOrd parentOrd = parent.getSlotPathOrd();
        sb.append("Parent Component: ").append(safeName(parent))
          .append(" (").append(parent.getType().toString()).append(") ")
          .append(parentOrd != null ? "station:|" + parentOrd.toString() : "").append("\n");
      }

      // Properties & Facets of Target Component
      sb.append("\n--- Target Component Properties & Slots ---\n");
      Property[] props = targetComp.getPropertiesArray();
      int propCount = 0;
      for (int i = 0; i < props.length && propCount < 60; i++)
      {
        Property p = props[i];
        BValue v = targetComp.get(p);
        if (v instanceof BComponent || v instanceof BLink) continue; // handled below

        String propName = p.getName();
        String valStr = formatValue(v);
        BFacets facets = targetComp.getSlotFacets(p);
        String facetStr = (facets != null && !facets.equals(BFacets.NULL)) ? " [facets: " + facets.toString() + "]" : "";
        sb.append("  - ").append(propName)
          .append(" (").append(v != null ? v.getType().toString() : "null").append(") = ")
          .append(valStr).append(facetStr).append("\n");
        propCount++;

        // If proxyExt, also inspect its sub-properties (such as pointId, deviceFacets, conversion)
        if ("proxyExt".equals(propName) && v instanceof BComplex)
        {
          BComplex proxy = (BComplex) v;
          Property[] proxyProps = proxy.getPropertiesArray();
          for (int j = 0; j < proxyProps.length; j++)
          {
            Property pp = proxyProps[j];
            BValue pv = proxy.get(pp);
            if (pv instanceof BSimple || pv instanceof BStatusValue)
            {
              sb.append("      proxyExt.").append(pp.getName()).append(" = ").append(formatValue(pv)).append("\n");
            }
          }
        }
      }

      // Actions on Target Component
      Action[] actions = targetComp.getActionsArray();
      if (actions != null && actions.length > 0)
      {
        sb.append("  Actions: ");
        for (int i = 0; i < actions.length; i++)
        {
          if (i > 0) sb.append(", ");
          sb.append(actions[i].getName());
        }
        sb.append("\n");
      }

      // If Target is a BProgram, include its Java source code
      if ("program:Program".equals(targetComp.getType().toString()) || targetComp.get("code") instanceof BComplex)
      {
        BValue codeVal = targetComp.get("code");
        if (codeVal instanceof BComplex)
        {
          BComplex codeObj = (BComplex) codeVal;
          BValue srcVal = codeObj.get("source");
          BValue impVal = codeObj.get("userDefinedImports");
          if (srcVal != null)
          {
            sb.append("\n--- BProgram Java Source Code ---\n");
            if (impVal != null && !impVal.toString().trim().isEmpty())
            {
              sb.append("// User Defined Imports: ").append(impVal.toString().trim()).append("\n");
            }
            sb.append(abbreviateText(srcVal.toString(), 6000)).append("\n");
          }
        }
      }

      // Child Components on Target (e.g. WireSheet blocks or Point Folder children)
      BComponent[] children = targetComp.getChildComponents();
      if (children != null && children.length > 0)
      {
        sb.append("\n--- Child Components on Target WireSheet / Container (").append(children.length).append(" total) ---\n");
        int maxChildren = Math.min(children.length, 80);
        for (int i = 0; i < maxChildren; i++)
        {
          BComponent ch = children[i];
          sb.append("  * ").append(ch.getName())
            .append(" [").append(ch.getType().toString()).append("]");

          BValue ws = ch.get("wsAnnotation");
          if (ws instanceof BWsAnnotation)
          {
            BWsAnnotation anno = (BWsAnnotation) ws;
            sb.append(" @(x=").append(anno.p).append(", y=").append(anno.q).append(")");
          }

          BValue outVal = ch.get("out");
          if (outVal != null)
          {
            sb.append(" out=").append(formatValue(outVal));
          }
          BValue fbVal = ch.get("fallback");
          if (fbVal != null)
          {
            sb.append(" fallback=").append(formatValue(fbVal));
          }
          sb.append("\n");

          // Links targeting this child component
          BLink[] childLinks = ch.getLinks();
          if (childLinks != null && childLinks.length > 0)
          {
            for (int k = 0; k < childLinks.length; k++)
            {
              BLink lnk = childLinks[k];
              BComponent srcComp = lnk.getSourceComponent();
              String srcName = (srcComp != null) ? srcComp.getName() : String.valueOf(lnk.getSourceOrd());
              sb.append("      link: ").append(srcName).append(".").append(lnk.getSourceSlotName())
                .append(" -> ").append(ch.getName()).append(".").append(lnk.getTargetSlotName()).append("\n");
            }
          }
        }
        if (children.length > maxChildren)
        {
          sb.append("  ... (").append(children.length - maxChildren).append(" more child components omitted)\n");
        }
      }

      // Direct Links on Target Component
      BLink[] links = targetComp.getLinks();
      if (links != null && links.length > 0)
      {
        sb.append("\n--- Incoming Links on Target Component ---\n");
        for (int i = 0; i < links.length; i++)
        {
          BLink lnk = links[i];
          BComponent srcComp = lnk.getSourceComponent();
          String srcName = (srcComp != null) ? srcComp.getName() : String.valueOf(lnk.getSourceOrd());
          sb.append("  link: ").append(srcName).append(".").append(lnk.getSourceSlotName())
            .append(" -> ").append(safeName(targetComp)).append(".").append(lnk.getTargetSlotName()).append("\n");
        }
      }
    }

    // Selected Components in the View (Multi-Selection)
    if (selectedComps != null && !selectedComps.isEmpty())
    {
      sb.append("\n--- Selected Components in Active View (").append(selectedComps.size()).append(" selected) ---\n");
      int maxSel = Math.min(selectedComps.size(), 50);
      for (int i = 0; i < maxSel; i++)
      {
        BComponent sc = selectedComps.get(i);
        BOrd scOrd = sc.getSlotPathOrd();
        sb.append("  [Selected ").append(i + 1).append("] ")
          .append(safeName(sc)).append(" (").append(sc.getType().toString()).append(") ")
          .append(scOrd != null ? "station:|" + scOrd.toString() : "");
        BValue out = sc.get("out");
        if (out != null)
        {
          sb.append(" out=").append(formatValue(out));
        }
        sb.append("\n");
      }
    }

    // PX Graphic File Content if viewing/editing a PX graphic
    if (pxFile != null)
    {
      try
      {
        sb.append("\n--- Active PX Graphic File (").append(pxFile.getFilePath()).append(") ---\n");
        byte[] bytes = pxFile.read();
        if (bytes != null && bytes.length > 0)
        {
          String xml = new String(bytes, StandardCharsets.UTF_8);
          sb.append(abbreviateText(xml, 8000)).append("\n");
        }
      }
      catch (Throwable ignore)
      {
      }
    }

    sb.append("=== END ACTIVE VIEW CONTEXT ===\n");
    return sb.toString();
  }

  private static String safeName(BComponent c)
  {
    if (c == null) return "null";
    String n = c.getName();
    return (n != null && !n.isEmpty()) ? n : c.getType().getTypeName();
  }

  private static String formatValue(BValue v)
  {
    if (v == null) return "null";
    try
    {
      String s = v.toString(null);
      return abbreviateText(s.replace('\r', ' ').replace('\n', ' '), 120);
    }
    catch (Throwable t)
    {
      return v.toString();
    }
  }

  private static String abbreviateText(String text, int maxChars)
  {
    if (text == null) return "";
    if (text.length() <= maxChars) return text;
    return text.substring(0, maxChars) + "\n... [truncated]";
  }
}
