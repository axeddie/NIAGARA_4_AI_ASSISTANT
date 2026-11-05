/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.baja.collection.BITable;
import javax.baja.collection.Column;
import javax.baja.collection.ColumnList;
import javax.baja.collection.TableCursor;
import javax.baja.file.BIDirectory;
import javax.baja.file.BIFile;
import javax.baja.file.BFileSpace;
import javax.baja.file.BFileSystem;
import javax.baja.file.FilePath;
import javax.baja.naming.BOrd;
import javax.baja.sys.Action;
import javax.baja.sys.BComplex;
import javax.baja.sys.BComponent;
import javax.baja.sys.BFacets;
import javax.baja.sys.BIObject;
import javax.baja.sys.BLink;
import javax.baja.sys.BObject;
import javax.baja.sys.BValue;
import javax.baja.sys.Flags;
import javax.baja.sys.Property;
import javax.baja.sys.Sys;
import javax.baja.tag.Tag;
import javax.baja.util.BWsAnnotation;
import javax.baja.workbench.BWbShell;

/**
 * Executes read-only agentic inspection queries (<station-inspect>[...]</station-inspect>)
 * requested by the AI so it can inspect any station component/ORD, run BQL queries, list folders,
 * or read any Station/Workbench file before generating its final <station-actions> plan.
 */
public final class StationInspector
{
  private StationInspector()
  {
  }

  /**
   * Extracts a list of inspection request maps if the AI response contains <station-inspect>...</station-inspect>.
   */
  public static List<Map<String, String>> extractInspectRequests(String responseText)
  {
    List<Map<String, String>> empty = new ArrayList<Map<String, String>>();
    if (responseText == null || responseText.trim().isEmpty())
    {
      return empty;
    }

    int tagStart = responseText.indexOf("<station-inspect>");
    int tagEnd = responseText.indexOf("</station-inspect>");
    if (tagStart < 0 || tagEnd <= tagStart)
    {
      return empty;
    }

    String inside = responseText.substring(tagStart + "<station-inspect>".length(), tagEnd).trim();
    return StationActionExecutor.parseRawJsonActionList(inside);
  }

  /**
   * Strips <station-inspect>...</station-inspect> from display text.
   */
  public static String stripInspectTagsForDisplay(String responseText)
  {
    if (responseText == null) return "";
    int tagStart = responseText.indexOf("<station-inspect>");
    int tagEnd = responseText.indexOf("</station-inspect>");
    if (tagStart >= 0 && tagEnd > tagStart)
    {
      String before = responseText.substring(0, tagStart).trim();
      String after = responseText.substring(tagEnd + "</station-inspect>".length()).trim();
      return (before + "\n" + after).trim();
    }
    return responseText.trim();
  }

  /**
   * Executes all read-only inspection queries and returns a structured result block to feed back to the AI.
   */
  public static String executeInspections(BWbShell shell, List<Map<String, String>> requests)
  {
    if (requests == null || requests.isEmpty())
    {
      return "";
    }

    StationContextExtractor.StationTarget ctx = StationContextExtractor.extract(shell);
    BComponent baseComp = ctx.targetComponent;
    BIFile activeFile = ctx.pxFile;

    StringBuilder out = new StringBuilder();
    out.append("=== STATION & FILE INSPECTION RESULTS ===\n");

    for (int i = 0; i < requests.size(); i++)
    {
      Map<String, String> req = requests.get(i);
      String op = firstNonEmpty(req.get("inspect"), req.get("action"), req.get("type"), "inspectOrd");
      out.append("\n[Inspection ").append(i + 1).append(": ").append(op).append("]\n");

      try
      {
        if ("inspectOrd".equalsIgnoreCase(op) || "inspectComponent".equalsIgnoreCase(op))
        {
          String ordStr = firstNonEmpty(req.get("ord"), req.get("component"), req.get("target"), "activeTarget");
          inspectOrdInto(baseComp, ordStr, out);
        }
        else if ("runBql".equalsIgnoreCase(op) || "bql".equalsIgnoreCase(op))
        {
          String bqlStr = firstNonEmpty(req.get("query"), req.get("bql"), req.get("ord"));
          runBqlInto(baseComp, bqlStr, out);
        }
        else if ("readFile".equalsIgnoreCase(op))
        {
          String fileStr = firstNonEmpty(req.get("file"), req.get("ord"), req.get("path"), "activeFile");
          int startLine = parseIntSafe(req.get("startLine"), 1);
          int endLine = parseIntSafe(req.get("endLine"), 400);
          String search = req.get("search");
          readFileInto(baseComp, activeFile, fileStr, startLine, endLine, search, out);
        }
        else if ("listFiles".equalsIgnoreCase(op) || "listDir".equalsIgnoreCase(op))
        {
          String dirStr = firstNonEmpty(req.get("dir"), req.get("ord"), req.get("path"), "station:|file:^");
          listFilesInto(baseComp, activeFile, dirStr, out);
        }
        else
        {
          out.append("Unknown inspection type: ").append(op).append("\n");
        }
      }
      catch (Throwable t)
      {
        out.append("Inspection error: ").append(t.getMessage()).append("\n");
      }
    }

    out.append("=== END INSPECTION RESULTS ===\n");
    return out.toString();
  }

  private static void inspectOrdInto(BComponent baseComp, String ordOrName, StringBuilder sb) throws Exception
  {
    BComponent comp = StationActionExecutor.resolveComponentAnywhere(baseComp, ordOrName);
    if (comp == null)
    {
      sb.append("Could not resolve component: ").append(ordOrName).append("\n");
      return;
    }

    BOrd slotOrd = comp.getSlotPathOrd();
    sb.append("Name: ").append(comp.getName()).append("\n");
    sb.append("Type: ").append(comp.getType().toString()).append("\n");
    sb.append("SlotPath: ").append(slotOrd != null ? "station:|" + slotOrd.toString() : "unmounted").append("\n");

    // Tags
    try
    {
      if (!comp.tags().isEmpty())
      {
        sb.append("Tags: ");
        boolean first = true;
        for (Tag tag : comp.tags())
        {
          if (!first) sb.append(", ");
          sb.append(tag.getId().getQName());
          if (tag.getValue() != null) sb.append("=").append(tag.getValue().toString());
          first = false;
        }
        sb.append("\n");
      }
    }
    catch (Throwable ignore)
    {
    }

    // Properties
    sb.append("Properties:\n");
    Property[] props = comp.getPropertiesArray();
    for (int i = 0; i < props.length && i < 120; i++)
    {
      Property p = props[i];
      BValue v = comp.get(p);
      if (v instanceof BComponent || v instanceof BLink) continue;
      int flags = comp.getFlags(p);
      BFacets facets = comp.getSlotFacets(p);
      sb.append("  - ").append(p.getName())
        .append(" (").append(v != null ? v.getType().toString() : "null").append(")")
        .append(" = ").append(v != null ? v.toString() : "null");
      if (flags != 0)
      {
        sb.append(" [flags:").append(Flags.encodeToString(flags)).append("]");
      }
      if (facets != null && !facets.equals(BFacets.NULL))
      {
        sb.append(" [facets:").append(facets.toString()).append("]");
      }
      sb.append("\n");

      // Inspect sub-properties of BComplex slots (such as proxyExt, alarmExt, historyExt, code)
      if (v instanceof BComplex)
      {
        BComplex sub = (BComplex) v;
        Property[] subProps = sub.getPropertiesArray();
        for (int j = 0; j < subProps.length && j < 30; j++)
        {
          Property sp = subProps[j];
          BValue sv = sub.get(sp);
          if (sv != null)
          {
            String svStr = sv.toString();
            if (svStr.length() > 300) svStr = svStr.substring(0, 300) + "...";
            sb.append("      ").append(p.getName()).append(".").append(sp.getName())
              .append(" (").append(sv.getType().toString()).append(") = ").append(svStr).append("\n");
          }
        }
      }
    }

    // Actions
    Action[] actions = comp.getActionsArray();
    if (actions != null && actions.length > 0)
    {
      sb.append("Actions: ");
      for (int i = 0; i < actions.length; i++)
      {
        if (i > 0) sb.append(", ");
        sb.append(actions[i].getName());
        BValue defArg = actions[i].getParameterDefault();
        if (defArg != null)
        {
          sb.append("(").append(defArg.getType().toString()).append(")");
        }
      }
      sb.append("\n");
    }

    // Child Components
    BComponent[] children = comp.getChildComponents();
    if (children != null && children.length > 0)
    {
      sb.append("Child Components (").append(children.length).append("):\n");
      int limit = Math.min(children.length, 200);
      for (int i = 0; i < limit; i++)
      {
        BComponent ch = children[i];
        sb.append("  * ").append(ch.getName()).append(" [").append(ch.getType().toString()).append("]");
        BValue ws = ch.get("wsAnnotation");
        if (ws instanceof BWsAnnotation)
        {
          BWsAnnotation a = (BWsAnnotation) ws;
          sb.append(" @(x=").append(a.p).append(",y=").append(a.q).append(")");
        }
        BValue outVal = ch.get("out");
        if (outVal != null) sb.append(" out=").append(outVal.toString());
        sb.append("\n");

        BLink[] links = ch.getLinks();
        if (links != null)
        {
          for (int k = 0; k < links.length; k++)
          {
            BLink lnk = links[k];
            BComponent src = lnk.getSourceComponent();
            String srcName = src != null ? src.getName() : String.valueOf(lnk.getSourceOrd());
            sb.append("      link: ").append(srcName).append(".").append(lnk.getSourceSlotName())
              .append(" -> ").append(ch.getName()).append(".").append(lnk.getTargetSlotName()).append("\n");
          }
        }
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void runBqlInto(BComponent baseComp, String bqlQuery, StringBuilder sb) throws Exception
  {
    if (bqlQuery == null || bqlQuery.trim().isEmpty())
    {
      sb.append("Empty BQL query.\n");
      return;
    }
    String q = bqlQuery.trim();
    if (!q.startsWith("station:|") && !q.startsWith("slot:") && !q.startsWith("bql:") && !q.startsWith("neql:"))
    {
      if (q.toLowerCase().startsWith("select "))
      {
        q = "station:|slot:/|bql:" + q;
      }
      else
      {
        q = "station:|slot:/|bql:select name, type, slotPath, out from " + q;
      }
    }
    else if (q.startsWith("bql:") || q.startsWith("neql:"))
    {
      q = "station:|slot:/|" + q;
    }

    BObject baseObj = (baseComp != null) ? baseComp : Sys.getStation();
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
        throw (firstErr instanceof Exception) ? (Exception) firstErr : new Exception(firstErr);
      }
    }
    if (res instanceof BITable)
    {
      BITable table = (BITable) res;
      ColumnList cols = table.getColumns();
      Column[] colArr = cols != null ? cols.list() : new Column[0];

      sb.append("BQL Query: ").append(q).append("\n");
      if (colArr.length > 0)
      {
        sb.append("Columns: ");
        for (int c = 0; c < colArr.length; c++)
        {
          if (c > 0) sb.append(" | ");
          sb.append(colArr[c].getName());
        }
        sb.append("\n");
      }

      TableCursor cursor = table.cursor();
      try
      {
        int rowCount = 0;
        while (cursor.next() && rowCount < 150)
        {
          rowCount++;
          sb.append("  Row ").append(rowCount).append(": ");
          if (colArr.length > 0)
          {
            for (int c = 0; c < colArr.length; c++)
            {
              if (c > 0) sb.append(" | ");
              BIObject cellVal = cursor.cell(colArr[c]);
              sb.append(cellVal != null ? cellVal.toString() : "null");
            }
          }
          else
          {
            Object rowObj = cursor.get();
            if (rowObj instanceof BComponent)
            {
              BComponent rc = (BComponent) rowObj;
              sb.append(rc.getName()).append(" [").append(rc.getType()).append("] ")
                .append(rc.getSlotPathOrd());
            }
            else
            {
              sb.append(rowObj != null ? rowObj.toString() : "null");
            }
          }
          sb.append("\n");
        }
        sb.append("Total rows returned (up to 150 shown): ").append(rowCount).append("\n");
      }
      finally
      {
        try { cursor.close(); } catch (Throwable ignore) {}
      }
    }
    else if (res != null)
    {
      sb.append("Resolved object: ").append(res.toString()).append(" (").append(res.getType()).append(")\n");
    }
    else
    {
      sb.append("No results for BQL query: ").append(q).append("\n");
    }
  }

  private static void readFileInto(
    BComponent baseComp,
    BIFile activeFile,
    String fileSpec,
    int startLine,
    int endLine,
    String searchFilter,
    StringBuilder sb) throws Exception
  {
    byte[] bytes = StationActionExecutor.readAnyFileBytes(baseComp, activeFile, fileSpec);
    if (bytes == null)
    {
      sb.append("File not found: ").append(fileSpec).append("\n");
      return;
    }

    String content = new String(bytes, StandardCharsets.UTF_8);
    String[] lines = content.split("\n", -1);
    sb.append("File: ").append(fileSpec).append(" (").append(lines.length).append(" lines, ")
      .append(bytes.length).append(" bytes)\n");

    int s = Math.max(1, startLine);
    int e = Math.min(lines.length, Math.max(s, endLine));
    String filterLower = (searchFilter != null && !searchFilter.trim().isEmpty())
      ? searchFilter.trim().toLowerCase()
      : null;

    int shown = 0;
    for (int i = s; i <= e && shown < 400; i++)
    {
      String line = lines[i - 1];
      if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
      if (filterLower != null && !line.toLowerCase().contains(filterLower))
      {
        continue;
      }
      sb.append(i).append(": ").append(line).append("\n");
      shown++;
    }
  }

  private static void listFilesInto(
    BComponent baseComp,
    BIFile activeFile,
    String dirSpec,
    StringBuilder sb) throws Exception
  {
    BFileSpace stationSpace = EddiesDuplicateChooserDialog.resolveStationFileSpace(baseComp, activeFile);
    String clean = dirSpec != null ? dirSpec.trim() : "station:|file:^";

    if (clean.startsWith("station:|file:") || clean.startsWith("file:^") || clean.startsWith("^"))
    {
      if (stationSpace == null)
      {
        sb.append("No connected station FileSpace available.\n");
        return;
      }
      int caret = clean.indexOf('^');
      String rel = caret >= 0 ? clean.substring(caret) : "^";
      FilePath fp = new FilePath(rel);
      BIFile dir = stationSpace.findFile(fp);
      sb.append("Station Directory (station:|file:").append(rel).append("):\n");
      BIFile[] kids = null;
      if (dir instanceof BIDirectory)
      {
        kids = ((BIDirectory) dir).listFiles();
      }
      else if (dir != null)
      {
        kids = stationSpace.getChildren(dir);
      }
      if (kids != null)
      {
        for (int i = 0; i < kids.length; i++)
        {
          BIFile f = kids[i];
          boolean isDir = f.isDirectory() || (f instanceof BIDirectory);
          sb.append("  ").append(isDir ? "[DIR]  " : "[FILE] ")
            .append(f.getFileName()).append(" (").append(f.getFilePath()).append(")\n");
        }
      }
      return;
    }

    File localDir;
    if ("file:!".equalsIgnoreCase(clean) || clean.startsWith("file:!"))
    {
      File nuh = Sys.getNiagaraUserHome();
      String sub = clean.length() > 6 ? clean.substring(6) : "";
      localDir = sub.isEmpty() ? nuh : new File(nuh, sub);
    }
    else
    {
      localDir = new File(clean);
    }

    if (localDir != null && localDir.isDirectory())
    {
      sb.append("Local Directory (").append(localDir.getAbsolutePath()).append("):\n");
      File[] files = localDir.listFiles();
      if (files != null)
      {
        for (int i = 0; i < files.length && i < 200; i++)
        {
          File f = files[i];
          sb.append("  ").append(f.isDirectory() ? "[DIR]  " : "[FILE] ")
            .append(f.getName()).append("\n");
        }
      }
    }
    else
    {
      sb.append("Directory not found: ").append(clean).append("\n");
    }
  }

  public static byte[] readLocalFileBytes(File f) throws Exception
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream((int) Math.max(1024L, f.length()));
    FileInputStream fis = new FileInputStream(f);
    try
    {
      byte[] buf = new byte[4096];
      int n;
      while ((n = fis.read(buf)) >= 0)
      {
        baos.write(buf, 0, n);
      }
    }
    finally
    {
      try { fis.close(); } catch (Throwable ignore) {}
    }
    return baos.toByteArray();
  }

  private static int parseIntSafe(String s, int def)
  {
    if (s == null || s.trim().isEmpty()) return def;
    try
    {
      return Integer.parseInt(s.trim());
    }
    catch (NumberFormatException e)
    {
      return def;
    }
  }

  private static String firstNonEmpty(String... vals)
  {
    if (vals == null) return "";
    for (int i = 0; i < vals.length; i++)
    {
      if (vals[i] != null && !vals[i].trim().isEmpty())
      {
        return vals[i].trim();
      }
    }
    return "";
  }
}
