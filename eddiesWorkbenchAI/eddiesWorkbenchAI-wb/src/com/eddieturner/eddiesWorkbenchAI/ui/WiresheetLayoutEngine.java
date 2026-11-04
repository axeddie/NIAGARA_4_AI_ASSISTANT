/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.baja.gx.BFont;
import javax.baja.naming.BOrd;
import javax.baja.naming.SlotPath;
import javax.baja.sync.Transaction;
import javax.baja.sys.BComplex;
import javax.baja.sys.BComponent;
import javax.baja.sys.BLink;
import javax.baja.sys.BObject;
import javax.baja.sys.BValue;
import javax.baja.sys.Context;
import javax.baja.sys.Flags;
import javax.baja.sys.Property;
import javax.baja.ui.BWidget;
import javax.baja.util.BWsAnnotation;
import javax.baja.workbench.BWbShell;

/**
 * Intelligent Niagara 4 WireSheet layout engine (WiresheetLayoutEngine).
 *
 * <p>Handles two primary layout patterns:
 * <ol>
 *   <li><b>Unlinked Blocks</b>: Arranged alphabetically (natural sort) in a clean horizontal
 *       multi-column grid targeting 5 columns across and automatically reducing to 4 or 3 columns
 *       only when block names are unusually wide.</li>
 *   <li><b>Linked Logic Chains</b>: Separated out of the unlinked grid and placed in a dedicated
 *       logic area (or starting at top-left if the entire sheet is linked logic), arranged
 *       topologically from left to right by signal flow with wide wire channels (so link lines
 *       draw cleanly without collapsing into link knobs) and vertical alignment to minimize
 *       wire crossings.</li>
 * </ol>
 *
 * <p>All blocks receive a minimum {@code wixelWidth} of 10 wixels, sized snugly via actual
 * 10pt bold font metrics (at 12 pixels per wixel, matching {@code com.tridium.wiresheet.TitleBarGlyph})
 * so every block shows its full name without excess blank space to the right.
 */
public final class WiresheetLayoutEngine
{
  /** Minimum width of any block on the wiresheet in wixels. */
  public static final int MIN_WIXEL_WIDTH = 10;

  /** Standard Niagara WireSheet pixels per wixel (WixelGrid.wixel == 12). */
  private static final double PIXELS_PER_WIXEL = 12.0;

  /** Left margin (in wixels) where layout begins on the wiresheet. */
  private static final int START_P = 3;

  /** Top margin (in wixels) where layout begins on the wiresheet. */
  private static final int START_Q = 3;

  /** Horizontal gap (in wixels) between unlinked columns in the alphabetical grid. */
  private static final int UNLINKED_COL_GAP = 3;

  /** Vertical gap (in wixels) between rows in the unlinked alphabetical grid. */
  private static final int UNLINKED_ROW_GAP = 2;

  /** Target maximum canvas width (in wixels) for the unlinked 5->4->3 column grid. */
  private static final int MAX_GRID_CANVAS_WIXELS = 86;

  /**
   * Horizontal wire channel gap (in wixels) between connected logic stages.
   * 6 wixels (72px) ensures Niagara draws full orthogonal link lines without stubby knobs.
   */
  private static final int LOGIC_WIRE_CHANNEL_GAP = 6;

  /** Vertical gap (in wixels) between blocks in the same logic stage column. */
  private static final int LOGIC_VERTICAL_GAP = 2;

  /** Vertical separation (in wixels) between distinct disconnected logic sub-circuits. */
  private static final int SUBGRAPH_VERTICAL_GAP = 4;

  /** Vertical separation (in wixels) between the unlinked grid area and the linked logic area. */
  private static final int SECTION_SEPARATION_GAP = 4;

  private static final FontMetrics AWT_TITLE_FONT_METRICS;
  static
  {
    FontMetrics fm = null;
    try
    {
      Canvas c = new Canvas();
      fm = c.getFontMetrics(new Font("Tahoma", Font.BOLD, 10));
    }
    catch (Throwable ignore)
    {
    }
    AWT_TITLE_FONT_METRICS = fm;
  }

  /**
   * Represents a link explicitly created during a station action plan so layout can
   * immediately connect the blocks even before asynchronous Fox link resolution completes.
   */
  public static final class ExplicitLink
  {
    public final String sourceName;
    public final String sourceSlot;
    public final String targetName;
    public final String targetSlot;

    public ExplicitLink(String sourceName, String sourceSlot, String targetName, String targetSlot)
    {
      this.sourceName = sourceName;
      this.sourceSlot = sourceSlot != null ? sourceSlot : "out";
      this.targetName = targetName;
      this.targetSlot = targetSlot != null ? targetSlot : "inA";
    }
  }

  private WiresheetLayoutEngine()
  {
  }

  /**
   * Arranges all child components on the given container WireSheet.
   * Records undo operations into {@code undoOps} if non-null.
   */
  public static String arrangeWiresheet(BComponent container, List<Runnable> undoOps)
  {
    return arrangeWiresheetInternal(container, null, undoOps);
  }

  private static String arrangeWiresheetInternal(
    BComponent container,
    List<ExplicitLink> explicitLinks,
    List<Runnable> undoOps)
  {
    if (container == null)
    {
      return "No target WireSheet container component found.";
    }

    BComponent[] rawChildren = container.getChildComponents();
    if (rawChildren == null || rawChildren.length == 0)
    {
      return "No child components on \"" + safeName(container) + "\" to arrange.";
    }

    List<BComponent> allBlocks = new ArrayList<BComponent>();
    Map<String, BComponent> byName = new HashMap<String, BComponent>();
    Map<String, BComponent> byHandle = new HashMap<String, BComponent>();
    indexContainerChildren(container, rawChildren, allBlocks, byName, byHandle);

    if (allBlocks.isEmpty())
    {
      return "No visible WireSheet blocks on \"" + safeName(container) + "\".";
    }

    // Build intra-sheet directed link graph: source -> targets, target -> sources
    Map<BComponent, List<LinkEdge>> outgoing = new LinkedHashMap<BComponent, List<LinkEdge>>();
    Map<BComponent, List<LinkEdge>> incoming = new LinkedHashMap<BComponent, List<LinkEdge>>();
    for (int i = 0; i < allBlocks.size(); i++)
    {
      BComponent b = allBlocks.get(i);
      outgoing.put(b, new ArrayList<LinkEdge>());
      incoming.put(b, new ArrayList<LinkEdge>());
    }

    int totalIntraLinks = populateIntraSheetLinks(
      container, allBlocks, byName, byHandle, explicitLinks, outgoing, incoming);

    // Partition into Unlinked Blocks vs. Linked Logic Blocks
    List<BComponent> unlinkedBlocks = new ArrayList<BComponent>();
    List<BComponent> linkedBlocks = new ArrayList<BComponent>();
    for (int i = 0; i < allBlocks.size(); i++)
    {
      BComponent b = allBlocks.get(i);
      boolean hasOut = !outgoing.get(b).isEmpty();
      boolean hasIn = !incoming.get(b).isEmpty();
      if (hasOut || hasIn)
      {
        linkedBlocks.add(b);
      }
      else
      {
        unlinkedBlocks.add(b);
      }
    }

    int currentTopQ = START_Q;
    int arrangedUnlinked = 0;
    int chosenGridCols = 0;
    int arrangedLinked = 0;
    int subCircuitsCount = 0;

    Context tx = null;
    try
    {
      tx = Transaction.start(container, null);
    }
    catch (Throwable ignore)
    {
    }

    try
    {
      // 1. Lay out unlinked blocks alphabetically in a 5 -> 4 -> 3 horizontal column grid
      if (!unlinkedBlocks.isEmpty())
      {
        GridLayoutResult gridRes = layoutUnlinkedAlphabeticalGrid(
          container, unlinkedBlocks, outgoing, incoming, START_P, currentTopQ, undoOps);
        arrangedUnlinked = unlinkedBlocks.size();
        chosenGridCols = gridRes.columnsUsed;
        currentTopQ = gridRes.bottomQ + SECTION_SEPARATION_GAP;
      }

      // 2. Lay out linked logic blocks in a dedicated space outside the unlinked columns
      if (!linkedBlocks.isEmpty())
      {
        List<List<BComponent>> connectedComponents = findWeaklyConnectedSubgraphs(
          linkedBlocks, outgoing, incoming);
        subCircuitsCount = connectedComponents.size();

        for (int cIdx = 0; cIdx < connectedComponents.size(); cIdx++)
        {
          List<BComponent> subgraph = connectedComponents.get(cIdx);
          int nextBottomQ = layoutConnectedLogicSubgraph(
            container, subgraph, outgoing, incoming, START_P, currentTopQ, undoOps);
          currentTopQ = nextBottomQ + SUBGRAPH_VERTICAL_GAP;
          arrangedLinked += subgraph.size();
        }
      }
    }
    finally
    {
      if (tx != null)
      {
        try
        {
          Transaction.end(container, tx);
        }
        catch (Throwable ignore)
        {
        }
      }
    }

    StringBuilder summary = new StringBuilder();
    summary.append("Arranged ").append(allBlocks.size()).append(" block(s) on \"")
      .append(safeName(container)).append("\": ");
    if (arrangedLinked > 0)
    {
      summary.append(arrangedLinked).append(" linked logic block(s) in ")
        .append(subCircuitsCount).append(" left-to-right signal flow group(s) (")
        .append(totalIntraLinks).append(" link(s))");
    }
    if (arrangedUnlinked > 0)
    {
      if (arrangedLinked > 0) summary.append(", plus ");
      summary.append(arrangedUnlinked).append(" unlinked block(s) alphabetically across ")
        .append(chosenGridCols).append(" column(s)");
    }
    summary.append(".");
    return summary.toString();
  }

  /**
   * Arranges a specific subset of newly created/linked components on a wiresheet in a clean
   * unoccupied space outside existing blocks (or arranges the whole wiresheet if all blocks
   * are part of the plan).
   */
  public static String arrangeComponentsInCleanSpace(
    BComponent container,
    Set<String> touchedComponentNames,
    List<ExplicitLink> explicitLinks,
    List<Runnable> undoOps)
  {
    if (container == null || touchedComponentNames == null || touchedComponentNames.isEmpty())
    {
      return "";
    }

    BComponent[] rawChildren = container.getChildComponents();
    if (rawChildren == null || rawChildren.length == 0)
    {
      return "";
    }

    List<BComponent> allBlocks = new ArrayList<BComponent>();
    Map<String, BComponent> byName = new HashMap<String, BComponent>();
    Map<String, BComponent> byHandle = new HashMap<String, BComponent>();
    indexContainerChildren(container, rawChildren, allBlocks, byName, byHandle);

    if (allBlocks.isEmpty())
    {
      return "";
    }

    // Build full sheet link graph so we can expand touched components to any directly connected blocks
    Map<BComponent, List<LinkEdge>> fullOutgoing = new LinkedHashMap<BComponent, List<LinkEdge>>();
    Map<BComponent, List<LinkEdge>> fullIncoming = new LinkedHashMap<BComponent, List<LinkEdge>>();
    for (int i = 0; i < allBlocks.size(); i++)
    {
      BComponent b = allBlocks.get(i);
      fullOutgoing.put(b, new ArrayList<LinkEdge>());
      fullIncoming.put(b, new ArrayList<LinkEdge>());
    }
    populateIntraSheetLinks(
      container, allBlocks, byName, byHandle, explicitLinks, fullOutgoing, fullIncoming);

    // Resolve touched names to actual mounted child components on container
    Set<BComponent> groupSet = new LinkedHashSet<BComponent>();
    for (String name : touchedComponentNames)
    {
      if (name == null) continue;
      BComponent resolved = byName.get(name.toLowerCase());
      if (resolved != null)
      {
        groupSet.add(resolved);
      }
    }

    if (groupSet.isEmpty())
    {
      return "";
    }

    // Also include any component on the same sheet that is linked to/from a touched component
    boolean expanded = true;
    while (expanded)
    {
      expanded = false;
      for (int i = 0; i < allBlocks.size(); i++)
      {
        BComponent ch = allBlocks.get(i);
        List<LinkEdge> ins = fullIncoming.get(ch);
        for (int k = 0; k < ins.size(); k++)
        {
          BComponent src = ins.get(k).source;
          if (src != null && src != ch)
          {
            if (groupSet.contains(ch) && !groupSet.contains(src))
            {
              groupSet.add(src);
              expanded = true;
            }
            else if (groupSet.contains(src) && !groupSet.contains(ch))
            {
              groupSet.add(ch);
              expanded = true;
            }
          }
        }
      }
    }

    // Check if there are other existing untouched blocks on this wiresheet
    int untouchedMaxBottomQ = 0;
    int untouchedCount = 0;
    for (int i = 0; i < allBlocks.size(); i++)
    {
      BComponent ch = allBlocks.get(i);
      if (groupSet.contains(ch))
      {
        continue;
      }
      untouchedCount++;
      BValue ws = ch.get("wsAnnotation");
      if (ws instanceof BWsAnnotation)
      {
        BWsAnnotation anno = (BWsAnnotation) ws;
        int estH = estimateWixelHeight(ch, fullOutgoing.get(ch), fullIncoming.get(ch));
        int bottom = anno.q + estH;
        if (bottom > untouchedMaxBottomQ)
        {
          untouchedMaxBottomQ = bottom;
        }
      }
    }

    // If the entire wiresheet was created/touched (no pre-existing untouched blocks), do a full sheet arrange
    if (untouchedCount == 0)
    {
      return arrangeWiresheetInternal(container, explicitLinks, undoOps);
    }

    // Otherwise place this new group in a clean space below existing untouched blocks
    int startQ = Math.max(START_Q, untouchedMaxBottomQ + SECTION_SEPARATION_GAP);

    List<BComponent> subList = new ArrayList<BComponent>(groupSet);
    List<BComponent> unlinked = new ArrayList<BComponent>();
    List<BComponent> linked = new ArrayList<BComponent>();
    for (int i = 0; i < subList.size(); i++)
    {
      BComponent b = subList.get(i);
      if (!fullOutgoing.get(b).isEmpty() || !fullIncoming.get(b).isEmpty())
      {
        linked.add(b);
      }
      else
      {
        unlinked.add(b);
      }
    }

    Context tx = null;
    try
    {
      tx = Transaction.start(container, null);
    }
    catch (Throwable ignore)
    {
    }

    try
    {
      int curQ = startQ;
      if (!linked.isEmpty())
      {
        List<List<BComponent>> subgraphs = findWeaklyConnectedSubgraphs(linked, fullOutgoing, fullIncoming);
        for (int i = 0; i < subgraphs.size(); i++)
        {
          curQ = layoutConnectedLogicSubgraph(
            container, subgraphs.get(i), fullOutgoing, fullIncoming, START_P, curQ, undoOps) + SUBGRAPH_VERTICAL_GAP;
        }
      }
      if (!unlinked.isEmpty())
      {
        GridLayoutResult gr = layoutUnlinkedAlphabeticalGrid(
          container, unlinked, fullOutgoing, fullIncoming, START_P, curQ, undoOps);
        curQ = gr.bottomQ;
      }
    }
    finally
    {
      if (tx != null)
      {
        try
        {
          Transaction.end(container, tx);
        }
        catch (Throwable ignore)
        {
        }
      }
    }

    return "Auto-arranged " + subList.size() + " block(s) in left-to-right signal flow.";
  }

  /**
   * Forces the active WireSheet view in the given Workbench shell (if open) to reload its
   * component and link glyphs so newly applied {@code wsAnnotation} coordinates and widths
   * appear immediately without requiring a manual view refresh.
   */
  public static void refreshActiveWiresheetView(final BWbShell shell)
  {
    if (shell == null) return;
    Runnable refresher = new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          BWidget wsPane = findWidgetByClassName(shell, "com.tridium.wiresheet.BWireSheetPane");
          if (wsPane != null)
          {
            Field ctrlField = wsPane.getClass().getField("controller");
            Object controller = ctrlField.get(wsPane);
            if (controller != null)
            {
              Field containerField = controller.getClass().getField("container");
              Object containerObj = containerField.get(controller);
              if (containerObj instanceof BComponent)
              {
                Method loadMethod = controller.getClass().getMethod("load", BComponent.class);
                loadMethod.invoke(controller, containerObj);
              }
              else
              {
                Method reloadMethod = controller.getClass().getMethod("reload");
                reloadMethod.invoke(controller);
              }
            }
            wsPane.relayout();
            wsPane.repaint();
          }
        }
        catch (Throwable ignore)
        {
        }
      }
    };

    try
    {
      BWidget.invokeLater(refresher);
    }
    catch (Throwable ignore)
    {
      try
      {
        refresher.run();
      }
      catch (Throwable ignore2)
      {
      }
    }
  }

  private static BWidget findWidgetByClassName(BWidget root, String className)
  {
    if (root == null) return null;
    if (className.equals(root.getClass().getName()))
    {
      return root;
    }
    BWidget[] children = root.getChildWidgets();
    if (children != null)
    {
      for (int i = 0; i < children.length; i++)
      {
        BWidget found = findWidgetByClassName(children[i], className);
        if (found != null) return found;
      }
    }
    return null;
  }

  // ============================================================================
  // 1. UNLINKED ALPHABETICAL HORIZONTAL GRID (5 -> 4 -> 3 COLUMNS)
  // ============================================================================

  private static final class GridLayoutResult
  {
    final int columnsUsed;
    final int bottomQ;

    GridLayoutResult(int columnsUsed, int bottomQ)
    {
      this.columnsUsed = columnsUsed;
      this.bottomQ = bottomQ;
    }
  }

  private static GridLayoutResult layoutUnlinkedAlphabeticalGrid(
    BComponent container,
    List<BComponent> blocks,
    Map<BComponent, List<LinkEdge>> outgoing,
    Map<BComponent, List<LinkEdge>> incoming,
    int startP,
    int startQ,
    List<Runnable> undoOps)
  {
    List<BComponent> sorted = new ArrayList<BComponent>(blocks);
    Collections.sort(sorted, NATURAL_NAME_COMPARATOR);

    int count = sorted.size();
    Map<BComponent, Integer> widths = new HashMap<BComponent, Integer>();
    Map<BComponent, Integer> heights = new HashMap<BComponent, Integer>();
    for (int i = 0; i < count; i++)
    {
      BComponent c = sorted.get(i);
      widths.put(c, Integer.valueOf(computeRequiredWixelWidth(c)));
      heights.put(c, Integer.valueOf(estimateWixelHeight(c, outgoing.get(c), incoming.get(c))));
    }

    // Target 5 columns across, auto-reducing to 4 or 3 only if block widths would exceed canvas width
    int chosenCols = Math.min(5, count);
    for (int candidateCols = Math.min(5, count); candidateCols >= 3; candidateCols--)
    {
      int totalWidth = computeGridWidthForColumns(sorted, widths, candidateCols);
      if (totalWidth <= MAX_GRID_CANVAS_WIXELS || candidateCols == 3)
      {
        chosenCols = candidateCols;
        break;
      }
    }

    int rows = (count + chosenCols - 1) / chosenCols;

    // Compute max width per column so columns align cleanly
    int[] colWidths = new int[chosenCols];
    for (int col = 0; col < chosenCols; col++)
    {
      int maxW = MIN_WIXEL_WIDTH;
      for (int row = 0; row < rows; row++)
      {
        int idx = row * chosenCols + col;
        if (idx < count)
        {
          int w = widths.get(sorted.get(idx)).intValue();
          if (w > maxW) maxW = w;
        }
      }
      colWidths[col] = maxW;
    }

    int[] colP = new int[chosenCols];
    int runningP = startP;
    for (int col = 0; col < chosenCols; col++)
    {
      colP[col] = runningP;
      runningP += colWidths[col] + UNLINKED_COL_GAP;
    }

    int curQ = startQ;
    for (int row = 0; row < rows; row++)
    {
      int maxRowHeight = 4;
      for (int col = 0; col < chosenCols; col++)
      {
        int idx = row * chosenCols + col;
        if (idx >= count) break;
        BComponent comp = sorted.get(idx);
        int w = widths.get(comp).intValue();
        int h = heights.get(comp).intValue();
        if (h > maxRowHeight) maxRowHeight = h;

        applyWsAnnotation(comp, colP[col], curQ, w, undoOps);
      }
      curQ += maxRowHeight + UNLINKED_ROW_GAP;
    }

    return new GridLayoutResult(chosenCols, curQ - UNLINKED_ROW_GAP);
  }

  private static int computeGridWidthForColumns(
    List<BComponent> sorted,
    Map<BComponent, Integer> widths,
    int cols)
  {
    int count = sorted.size();
    int rows = (count + cols - 1) / cols;
    int totalW = 0;
    for (int col = 0; col < cols; col++)
    {
      int colMax = MIN_WIXEL_WIDTH;
      for (int row = 0; row < rows; row++)
      {
        int idx = row * cols + col;
        if (idx < count)
        {
          int w = widths.get(sorted.get(idx)).intValue();
          if (w > colMax) colMax = w;
        }
      }
      totalW += colMax;
      if (col < cols - 1)
      {
        totalW += UNLINKED_COL_GAP;
      }
    }
    return totalW;
  }

  // ============================================================================
  // 2. CONNECTED LOGIC TOPOLOGICAL LEFT-TO-RIGHT LAYOUT
  // ============================================================================

  private static int layoutConnectedLogicSubgraph(
    BComponent container,
    List<BComponent> nodes,
    Map<BComponent, List<LinkEdge>> outgoing,
    Map<BComponent, List<LinkEdge>> incoming,
    int startP,
    int startQ,
    List<Runnable> undoOps)
  {
    Set<BComponent> nodeSet = new LinkedHashSet<BComponent>(nodes);

    // Step 1: Assign each node a left-to-right stage column (rank >= 0) via longest-path DAG layering
    Map<BComponent, Integer> rankMap = new HashMap<BComponent, Integer>();
    for (int i = 0; i < nodes.size(); i++)
    {
      rankMap.put(nodes.get(i), Integer.valueOf(0));
    }

    // Bellman-Ford / longest path relaxation (capped at nodes.size() iterations to handle feedback loops safely)
    int maxIterations = nodes.size() + 2;
    for (int iter = 0; iter < maxIterations; iter++)
    {
      boolean changed = false;
      for (int i = 0; i < nodes.size(); i++)
      {
        BComponent u = nodes.get(i);
        int uRank = rankMap.get(u).intValue();
        List<LinkEdge> outs = outgoing.get(u);
        if (outs == null) continue;
        for (int k = 0; k < outs.size(); k++)
        {
          BComponent v = outs.get(k).target;
          if (!nodeSet.contains(v)) continue;
          int candidateRank = Math.min(nodes.size() - 1, uRank + 1);
          if (candidateRank > rankMap.get(v).intValue())
          {
            rankMap.put(v, Integer.valueOf(candidateRank));
            changed = true;
          }
        }
      }
      if (!changed) break;
    }

    // Pull pure input nodes (no incoming links) right next to their earliest target stage (minTargetRank - 1)
    // so input constants feeding a late stage don't have long wires stretching across early columns
    for (int i = 0; i < nodes.size(); i++)
    {
      BComponent u = nodes.get(i);
      if (incoming.get(u).isEmpty() && !outgoing.get(u).isEmpty())
      {
        int minTargetRank = Integer.MAX_VALUE;
        List<LinkEdge> outs = outgoing.get(u);
        for (int k = 0; k < outs.size(); k++)
        {
          BComponent tgt = outs.get(k).target;
          if (rankMap.containsKey(tgt))
          {
            int tr = rankMap.get(tgt).intValue();
            if (tr < minTargetRank) minTargetRank = tr;
          }
        }
        if (minTargetRank > 1 && minTargetRank < Integer.MAX_VALUE)
        {
          rankMap.put(u, Integer.valueOf(minTargetRank - 1));
        }
      }
    }

    // Group nodes by column rank
    int maxRank = 0;
    for (int i = 0; i < nodes.size(); i++)
    {
      int r = rankMap.get(nodes.get(i)).intValue();
      if (r > maxRank) maxRank = r;
    }

    List<List<BComponent>> columns = new ArrayList<List<BComponent>>();
    for (int r = 0; r <= maxRank; r++)
    {
      columns.add(new ArrayList<BComponent>());
    }
    for (int i = 0; i < nodes.size(); i++)
    {
      BComponent b = nodes.get(i);
      int r = rankMap.get(b).intValue();
      columns.get(r).add(b);
    }

    // Remove any empty intermediate columns
    for (int r = columns.size() - 1; r >= 0; r--)
    {
      if (columns.get(r).isEmpty())
      {
        columns.remove(r);
      }
    }

    // Precompute wixel width and height for every node in this subgraph
    Map<BComponent, Integer> widths = new HashMap<BComponent, Integer>();
    Map<BComponent, Integer> heights = new HashMap<BComponent, Integer>();
    for (int i = 0; i < nodes.size(); i++)
    {
      BComponent b = nodes.get(i);
      widths.put(b, Integer.valueOf(computeRequiredWixelWidth(b)));
      heights.put(b, Integer.valueOf(estimateWixelHeight(b, outgoing.get(b), incoming.get(b))));
    }

    // Step 2: Order nodes vertically within each column to minimize wire crossings
    if (!columns.isEmpty())
    {
      Collections.sort(columns.get(0), new Comparator<BComponent>()
      {
        @Override
        public int compare(BComponent a, BComponent b)
        {
          String keyA = buildSourceOrderKey(a, outgoing.get(a));
          String keyB = buildSourceOrderKey(b, outgoing.get(b));
          int cmp = NATURAL_STRING_COMPARATOR.compare(keyA, keyB);
          if (cmp != 0) return cmp;
          return NATURAL_NAME_COMPARATOR.compare(a, b);
        }
      });
    }

    // Assign initial Y (q) positions and refine forward/backward via barycenter heuristic
    final Map<BComponent, Double> idealCenterY = new HashMap<BComponent, Double>();
    double cursorY = startQ;
    if (!columns.isEmpty())
    {
      List<BComponent> col0 = columns.get(0);
      for (int i = 0; i < col0.size(); i++)
      {
        BComponent b = col0.get(i);
        int h = heights.get(b).intValue();
        idealCenterY.put(b, Double.valueOf(cursorY + h / 2.0));
        cursorY += h + LOGIC_VERTICAL_GAP;
      }
    }

    // Forward sweep: order column c by average idealCenterY of upstream connected sources
    for (int c = 1; c < columns.size(); c++)
    {
      List<BComponent> col = columns.get(c);
      for (int i = 0; i < col.size(); i++)
      {
        BComponent b = col.get(i);
        List<LinkEdge> ins = incoming.get(b);
        double sum = 0.0;
        int cnt = 0;
        for (int k = 0; k < ins.size(); k++)
        {
          LinkEdge e = ins.get(k);
          Double srcY = idealCenterY.get(e.source);
          if (srcY != null)
          {
            double slotBias = slotOrderBias(e.targetSlot) * 0.25;
            sum += srcY.doubleValue() + slotBias;
            cnt++;
          }
        }
        double bary = (cnt > 0) ? (sum / cnt) : (startQ + i * 8.0);
        idealCenterY.put(b, Double.valueOf(bary));
      }

      Collections.sort(col, new Comparator<BComponent>()
      {
        @Override
        public int compare(BComponent a, BComponent b)
        {
          double ya = idealCenterY.get(a).doubleValue();
          double yb = idealCenterY.get(b).doubleValue();
          if (Math.abs(ya - yb) > 0.01)
          {
            return ya < yb ? -1 : 1;
          }
          return NATURAL_NAME_COMPARATOR.compare(a, b);
        }
      });
    }

    // Backward sweep on earlier columns so inputs align directly across from the logic block they feed
    for (int c = columns.size() - 2; c >= 0; c--)
    {
      List<BComponent> col = columns.get(c);
      for (int i = 0; i < col.size(); i++)
      {
        BComponent b = col.get(i);
        List<LinkEdge> outs = outgoing.get(b);
        List<LinkEdge> ins = incoming.get(b);
        double sum = 0.0;
        int cnt = 0;
        for (int k = 0; k < outs.size(); k++)
        {
          LinkEdge e = outs.get(k);
          Double tgtY = idealCenterY.get(e.target);
          if (tgtY != null)
          {
            double slotBias = slotOrderBias(e.targetSlot) * 1.5;
            sum += tgtY.doubleValue() + slotBias;
            cnt++;
          }
        }
        for (int k = 0; k < ins.size(); k++)
        {
          LinkEdge e = ins.get(k);
          Double srcY = idealCenterY.get(e.source);
          if (srcY != null)
          {
            sum += srcY.doubleValue();
            cnt++;
          }
        }
        if (cnt > 0)
        {
          idealCenterY.put(b, Double.valueOf(sum / cnt));
        }
      }

      Collections.sort(col, new Comparator<BComponent>()
      {
        @Override
        public int compare(BComponent a, BComponent b)
        {
          double ya = idealCenterY.get(a).doubleValue();
          double yb = idealCenterY.get(b).doubleValue();
          if (Math.abs(ya - yb) > 0.01)
          {
            return ya < yb ? -1 : 1;
          }
          return NATURAL_NAME_COMPARATOR.compare(a, b);
        }
      });
    }

    // Step 3: Compute X (p) coordinates for each stage column with generous wire channel gap
    int numCols = columns.size();
    int[] colP = new int[numCols];
    int runningP = startP;
    for (int c = 0; c < numCols; c++)
    {
      colP[c] = runningP;
      List<BComponent> col = columns.get(c);
      int maxColW = MIN_WIXEL_WIDTH;
      for (int i = 0; i < col.size(); i++)
      {
        int w = widths.get(col.get(i)).intValue();
        if (w > maxColW) maxColW = w;
      }
      int wireCount = countOutgoingLinksFromColumn(col, outgoing);
      int extraChannel = Math.min(4, Math.max(0, (wireCount - 2) / 2));
      runningP += maxColW + LOGIC_WIRE_CHANNEL_GAP + extraChannel;
    }

    // Step 4: Compute concrete integer Y (q) positions per column, aligning downstream blocks
    // vertically with the midpoint of their upstream inputs while preventing any vertical overlap
    Map<BComponent, Integer> finalQ = new HashMap<BComponent, Integer>();
    int subgraphMaxBottomQ = startQ;

    for (int c = 0; c < numCols; c++)
    {
      List<BComponent> col = columns.get(c);
      int prevBottom = startQ - LOGIC_VERTICAL_GAP;
      for (int i = 0; i < col.size(); i++)
      {
        BComponent b = col.get(i);
        int h = heights.get(b).intValue();
        int desiredQ = prevBottom + LOGIC_VERTICAL_GAP;

        List<LinkEdge> ins = incoming.get(b);
        int sumSrcCenter = 0;
        int srcCount = 0;
        for (int k = 0; k < ins.size(); k++)
        {
          BComponent src = ins.get(k).source;
          if (finalQ.containsKey(src))
          {
            int srcQ = finalQ.get(src).intValue();
            int srcH = heights.get(src).intValue();
            sumSrcCenter += (srcQ + srcH / 2);
            srcCount++;
          }
        }
        if (srcCount > 0)
        {
          int avgCenter = sumSrcCenter / srcCount;
          int alignedTop = avgCenter - (h / 2);
          if (alignedTop > desiredQ)
          {
            desiredQ = alignedTop;
          }
        }

        finalQ.put(b, Integer.valueOf(desiredQ));
        prevBottom = desiredQ + h;
        if (prevBottom > subgraphMaxBottomQ)
        {
          subgraphMaxBottomQ = prevBottom;
        }
      }
    }

    // Second pass: pull single-target upstream inputs down so they sit directly across from their target
    for (int c = numCols - 2; c >= 0; c--)
    {
      List<BComponent> col = columns.get(c);
      int nextTopLimit = Integer.MAX_VALUE;
      for (int i = col.size() - 1; i >= 0; i--)
      {
        BComponent b = col.get(i);
        int curQ = finalQ.get(b).intValue();
        int h = heights.get(b).intValue();
        List<LinkEdge> outs = outgoing.get(b);
        if (incoming.get(b).isEmpty() && outs.size() == 1)
        {
          LinkEdge edge = outs.get(0);
          BComponent tgt = edge.target;
          if (finalQ.containsKey(tgt))
          {
            List<BComponent> siblingsFeedingTgt = new ArrayList<BComponent>();
            for (int j = 0; j < col.size(); j++)
            {
              BComponent cand = col.get(j);
              if (incoming.get(cand).isEmpty()
                  && outgoing.get(cand).size() == 1
                  && outgoing.get(cand).get(0).target == tgt)
              {
                siblingsFeedingTgt.add(cand);
              }
            }
            int idxInSiblings = siblingsFeedingTgt.indexOf(b);
            if (idxInSiblings >= 0 && siblingsFeedingTgt.size() == 1)
            {
              int tgtQ = finalQ.get(tgt).intValue();
              int candidateQ = Math.min(tgtQ, nextTopLimit - LOGIC_VERTICAL_GAP - h);
              if (candidateQ > curQ)
              {
                curQ = candidateQ;
                finalQ.put(b, Integer.valueOf(curQ));
              }
            }
          }
        }
        nextTopLimit = curQ;
      }
    }

    // Step 5: Apply final (p, q, wixelWidth) to all blocks in this subgraph
    for (int c = 0; c < numCols; c++)
    {
      List<BComponent> col = columns.get(c);
      int p = colP[c];
      for (int i = 0; i < col.size(); i++)
      {
        BComponent b = col.get(i);
        int q = finalQ.get(b).intValue();
        int w = widths.get(b).intValue();
        int h = heights.get(b).intValue();
        if (q + h > subgraphMaxBottomQ)
        {
          subgraphMaxBottomQ = q + h;
        }
        applyWsAnnotation(b, p, q, w, undoOps);
      }
    }

    return subgraphMaxBottomQ;
  }

  private static int countOutgoingLinksFromColumn(
    List<BComponent> col,
    Map<BComponent, List<LinkEdge>> outgoing)
  {
    int count = 0;
    for (int i = 0; i < col.size(); i++)
    {
      List<LinkEdge> outs = outgoing.get(col.get(i));
      if (outs != null) count += outs.size();
    }
    return count;
  }

  private static String buildSourceOrderKey(BComponent src, List<LinkEdge> outs)
  {
    if (outs == null || outs.isEmpty())
    {
      return "zzz_" + safeName(src);
    }
    LinkEdge first = outs.get(0);
    return safeName(first.target) + "_" + String.format("%03d", Integer.valueOf(slotOrderBias(first.targetSlot))) + "_" + safeName(src);
  }

  private static int slotOrderBias(String slotName)
  {
    if (slotName == null) return 50;
    String s = slotName.toLowerCase();
    if ("ina".equals(s) || "in1".equals(s) || "controlledvariable".equals(s)) return 1;
    if ("inb".equals(s) || "in2".equals(s) || "setpoint".equals(s)) return 2;
    if ("inc".equals(s) || "in3".equals(s)) return 3;
    if ("ind".equals(s) || "in4".equals(s)) return 4;
    if ("fallback".equals(s) || "in10".equals(s) || "in16".equals(s)) return 5;
    return 10;
  }

  private static List<List<BComponent>> findWeaklyConnectedSubgraphs(
    List<BComponent> linkedBlocks,
    Map<BComponent, List<LinkEdge>> outgoing,
    Map<BComponent, List<LinkEdge>> incoming)
  {
    List<List<BComponent>> subgraphs = new ArrayList<List<BComponent>>();
    Set<BComponent> visited = new HashSet<BComponent>();

    for (int i = 0; i < linkedBlocks.size(); i++)
    {
      BComponent start = linkedBlocks.get(i);
      if (visited.contains(start)) continue;

      List<BComponent> component = new ArrayList<BComponent>();
      List<BComponent> queue = new ArrayList<BComponent>();
      queue.add(start);
      visited.add(start);

      int head = 0;
      while (head < queue.size())
      {
        BComponent cur = queue.get(head++);
        component.add(cur);

        List<LinkEdge> outs = outgoing.get(cur);
        if (outs != null)
        {
          for (int k = 0; k < outs.size(); k++)
          {
            BComponent nxt = outs.get(k).target;
            if (nxt != null && !visited.contains(nxt) && outgoing.containsKey(nxt))
            {
              visited.add(nxt);
              queue.add(nxt);
            }
          }
        }

        List<LinkEdge> ins = incoming.get(cur);
        if (ins != null)
        {
          for (int k = 0; k < ins.size(); k++)
          {
            BComponent prv = ins.get(k).source;
            if (prv != null && !visited.contains(prv) && outgoing.containsKey(prv))
            {
              visited.add(prv);
              queue.add(prv);
            }
          }
        }
      }

      subgraphs.add(component);
    }

    return subgraphs;
  }

  // ============================================================================
  // 3. BLOCK WIDTH & HEIGHT CALCULATION IN WIXELS
  // ============================================================================

  /**
   * Computes the required wixelWidth for a component so its full name is visible
   * without leaving excess blank space to the right of the name (minimum 10 wixels).
   */
  public static int computeRequiredWixelWidth(BComponent comp)
  {
    return computeRequiredWixelWidth(comp, null);
  }

  /**
   * Computes the required wixelWidth for a component (using {@code explicitName} if the
   * component has not yet been mounted in its parent).
   *
   * <p>In Niagara's {@code com.tridium.wiresheet.TitleBarGlyph} and {@code WixelGrid}:
   * <ul>
   *   <li>1 wixel = 12 pixels</li>
   *   <li>Title font = 10pt Bold ({@code wixel - 2})</li>
   *   <li>Left margin = 4px; right icon + margin = 22px; gap before icon = 4px (total non-text = 30px)</li>
   * </ul>
   */
  public static int computeRequiredWixelWidth(BComponent comp, String explicitName)
  {
    String rawName = (explicitName != null && !explicitName.trim().isEmpty())
      ? explicitName.trim()
      : (comp != null ? safeName(comp) : "Component");

    String displayName = rawName;
    try
    {
      displayName = SlotPath.unescape(rawName);
    }
    catch (Throwable ignore)
    {
    }

    double namePx = measureTitlePixels(displayName);
    int needed = (int) Math.ceil((namePx + 30.0) / PIXELS_PER_WIXEL);
    return Math.max(MIN_WIXEL_WIDTH, Math.min(28, needed));
  }

  private static double measureTitlePixels(String text)
  {
    if (text == null || text.isEmpty()) return 40.0;

    // 1. Try Niagara's own BFont 10pt BOLD width measurement
    try
    {
      String fontName = (BFont.DEFAULT != null && BFont.DEFAULT.getName() != null)
        ? BFont.DEFAULT.getName()
        : "Tahoma";
      BFont titleFont = BFont.make(fontName, 10.0, BFont.BOLD);
      double w = titleFont.width(text);
      if (w > 0.0)
      {
        return w;
      }
    }
    catch (Throwable ignore)
    {
    }

    // 2. Fallback to AWT 10pt Bold Tahoma FontMetrics
    if (AWT_TITLE_FONT_METRICS != null)
    {
      try
      {
        int w = AWT_TITLE_FONT_METRICS.stringWidth(text);
        if (w > 0)
        {
          return w;
        }
      }
      catch (Throwable ignore)
      {
      }
    }

    // 3. Proportional character width estimate for 10pt bold (~5.6px average)
    return text.length() * 5.6;
  }

  /**
   * Computes the rendered height of a component on the Niagara WireSheet in wixels,
   * matching {@code com.tridium.wiresheet.StdComponentGlyph.doLayout}:
   * {@code 2 (titleBar) + visibleSlots.size() + 1 (bottom resize bar) = 3 + visibleSlots.size()}.
   */
  private static int estimateWixelHeight(
    BComponent comp,
    List<LinkEdge> outgoingEdges,
    List<LinkEdge> incomingEdges)
  {
    if (comp == null) return 4;

    Set<String> visibleSlots = new HashSet<String>();

    // 1. Incoming links on comp (both resolved intra-sheet edges and any external links)
    if (incomingEdges != null)
    {
      for (int i = 0; i < incomingEdges.size(); i++)
      {
        String slot = incomingEdges.get(i).targetSlot;
        if (slot != null && !slot.isEmpty()) visibleSlots.add(slot);
      }
    }
    try
    {
      BLink[] inLinks = comp.getLinks();
      if (inLinks != null)
      {
        for (int i = 0; i < inLinks.length; i++)
        {
          if (inLinks[i] != null && inLinks[i].getTargetSlotName() != null)
          {
            visibleSlots.add(inLinks[i].getTargetSlotName());
          }
        }
      }
    }
    catch (Throwable ignore)
    {
    }

    // 2. Outgoing links from comp
    if (outgoingEdges != null)
    {
      for (int i = 0; i < outgoingEdges.size(); i++)
      {
        String slot = outgoingEdges.get(i).sourceSlot;
        if (slot != null && !slot.isEmpty()) visibleSlots.add(slot);
      }
    }

    // 3. Summary (pinned) non-hidden properties on comp
    try
    {
      Property[] props = comp.getPropertiesArray();
      if (props != null)
      {
        for (int i = 0; i < props.length; i++)
        {
          Property p = props[i];
          String pName = p.getName();
          if ("wsAnnotation".equals(pName)) continue;
          BValue val = comp.get(p);
          if (val instanceof BLink) continue;

          int flags = comp.getFlags(p);
          if ((flags & Flags.HIDDEN) != 0) continue;
          if ((flags & Flags.SUMMARY) != 0)
          {
            visibleSlots.add(pName);
          }
        }
      }
    }
    catch (Throwable ignore)
    {
    }

    int exactHeight = 3 + visibleSlots.size();
    return Math.max(3, Math.min(24, exactHeight));
  }

  public static void applyWsAnnotation(
    final BComponent comp,
    int p,
    int q,
    int wixelWidth,
    List<Runnable> undoOps)
  {
    if (comp == null) return;
    int safeP = Math.max(1, p);
    int safeQ = Math.max(1, q);
    int safeW = Math.max(MIN_WIXEL_WIDTH, wixelWidth);

    final BValue existing = comp.get("wsAnnotation");
    final BWsAnnotation oldCopy = (existing instanceof BWsAnnotation)
      ? (BWsAnnotation) existing.newCopy()
      : null;

    BWsAnnotation newAnno = BWsAnnotation.make(safeP, safeQ, safeW);

    if (existing != null)
    {
      comp.set("wsAnnotation", newAnno, null);
    }
    else
    {
      comp.add("wsAnnotation", newAnno);
    }

    if (undoOps != null && oldCopy != null)
    {
      undoOps.add(new Runnable()
      {
        @Override
        public void run()
        {
          try
          {
            comp.set("wsAnnotation", oldCopy, null);
          }
          catch (Throwable ignore)
          {
          }
        }
      });
    }
  }

  // ============================================================================
  // 4. LINK GRAPH & COMPONENT INDEXING HELPERS
  // ============================================================================

  private static void indexContainerChildren(
    BComponent container,
    BComponent[] rawChildren,
    List<BComponent> allBlocks,
    Map<String, BComponent> byName,
    Map<String, BComponent> byHandle)
  {
    for (int i = 0; i < rawChildren.length; i++)
    {
      BComponent ch = rawChildren[i];
      if (ch == null) continue;
      if (isHiddenComponent(container, ch)) continue;
      allBlocks.add(ch);
      if (ch.getName() != null)
      {
        byName.put(ch.getName().toLowerCase(), ch);
      }
      try
      {
        Object h = ch.getHandle();
        if (h != null)
        {
          byHandle.put(String.valueOf(h).trim().toLowerCase(), ch);
        }
      }
      catch (Throwable ignore)
      {
      }
    }
  }

  private static int populateIntraSheetLinks(
    BComponent container,
    List<BComponent> allBlocks,
    Map<String, BComponent> byName,
    Map<String, BComponent> byHandle,
    List<ExplicitLink> explicitLinks,
    Map<BComponent, List<LinkEdge>> outgoing,
    Map<BComponent, List<LinkEdge>> incoming)
  {
    Set<String> seenEdgeKeys = new HashSet<String>();
    int totalIntraLinks = 0;

    // 1. Inspect BLink children on every component on the sheet
    for (int i = 0; i < allBlocks.size(); i++)
    {
      BComponent target = allBlocks.get(i);
      BLink[] links = null;
      try
      {
        links = target.getLinks();
      }
      catch (Throwable ignore)
      {
      }
      if (links == null) continue;

      for (int k = 0; k < links.length; k++)
      {
        BLink lnk = links[k];
        if (lnk == null) continue;
        BComponent source = resolveSourceOnSameSheet(lnk, container, byName, byHandle);
        if (source != null && source != target && outgoing.containsKey(source))
        {
          String srcSlot = lnk.getSourceSlotName() != null ? lnk.getSourceSlotName() : "out";
          String tgtSlot = lnk.getTargetSlotName() != null ? lnk.getTargetSlotName() : "inA";
          String edgeKey = safeName(source).toLowerCase() + "." + srcSlot.toLowerCase()
            + "->" + safeName(target).toLowerCase() + "." + tgtSlot.toLowerCase();
          if (seenEdgeKeys.add(edgeKey))
          {
            LinkEdge edge = new LinkEdge(source, srcSlot, target, tgtSlot);
            outgoing.get(source).add(edge);
            incoming.get(target).add(edge);
            totalIntraLinks++;
          }
        }
      }
    }

    // 2. Merge any explicit links recorded during StationActionExecutor plan execution
    if (explicitLinks != null)
    {
      for (int i = 0; i < explicitLinks.size(); i++)
      {
        ExplicitLink exp = explicitLinks.get(i);
        if (exp == null || exp.sourceName == null || exp.targetName == null) continue;
        BComponent source = byName.get(exp.sourceName.toLowerCase());
        BComponent target = byName.get(exp.targetName.toLowerCase());
        if (source != null && target != null && source != target
            && outgoing.containsKey(source) && incoming.containsKey(target))
        {
          String edgeKey = safeName(source).toLowerCase() + "." + exp.sourceSlot.toLowerCase()
            + "->" + safeName(target).toLowerCase() + "." + exp.targetSlot.toLowerCase();
          if (seenEdgeKeys.add(edgeKey))
          {
            LinkEdge edge = new LinkEdge(source, exp.sourceSlot, target, exp.targetSlot);
            outgoing.get(source).add(edge);
            incoming.get(target).add(edge);
            totalIntraLinks++;
          }
        }
      }
    }

    return totalIntraLinks;
  }

  private static boolean isHiddenComponent(BComponent parent, BComponent child)
  {
    try
    {
      Property prop = child.getPropertyInParentComponent();
      if (prop != null && parent != null)
      {
        int flags = parent.getFlags(prop);
        return (flags & Flags.HIDDEN) != 0;
      }
    }
    catch (Throwable ignore)
    {
    }
    return false;
  }

  /**
   * Safely resolves a {@link BLink}'s source component if it resides on the same {@code container}
   * WireSheet. Handles direct links, handle ORDs ({@code h:xxxx}), and slot path ORDs without
   * ever allowing {@code UnresolvedException} to escape when {@code link.isActive()} is false
   * in Workbench client space.
   */
  private static BComponent resolveSourceOnSameSheet(
    BLink link,
    BComponent container,
    Map<String, BComponent> byName,
    Map<String, BComponent> byHandle)
  {
    if (link == null) return null;

    // 1. Try direct/active source component reference (only succeeds when active in station or direct)
    try
    {
      BComponent src = link.getSourceComponent();
      if (src != null)
      {
        if (src.getParentComponent() == container)
        {
          return src;
        }
        if (src.getName() != null)
        {
          BComponent match = byName.get(src.getName().toLowerCase());
          if (match != null && src.getParentComponent() != null
              && container.getSlotPathOrd() != null
              && container.getSlotPathOrd().equals(src.getParentComponent().getSlotPathOrd()))
          {
            return match;
          }
        }
      }
    }
    catch (Throwable ignore)
    {
    }

    // 2. Resolve via sourceOrd (the standard path in Workbench client space, matching WsController.toComponentGlyph)
    BOrd ord = null;
    try
    {
      ord = link.getSourceOrd();
    }
    catch (Throwable ignore)
    {
    }
    if (ord == null || ord.isNull()) return null;

    String ordStr = String.valueOf(ord).trim();
    String lowerOrd = ordStr.toLowerCase();

    // 2a. Handle ORD (e.g. "h:1a2b" or "station:|h:1a2b")
    int hIdx = lowerOrd.indexOf("h:");
    if (hIdx >= 0)
    {
      String handlePart = lowerOrd.substring(hIdx + 2).trim();
      int slash = handlePart.indexOf('/');
      if (slash >= 0)
      {
        handlePart = handlePart.substring(0, slash).trim();
      }
      BComponent byH = byHandle.get(handlePart);
      if (byH != null)
      {
        return byH;
      }
    }

    // 2b. Resolve BOrd against container (just like WsController.toComponentGlyph line 1867)
    try
    {
      BObject obj = ord.get(container);
      if (obj instanceof BComponent)
      {
        BComponent resolvedComp = (BComponent) obj;
        if (resolvedComp.getParentComponent() == container)
        {
          return resolvedComp;
        }
        Object h = resolvedComp.getHandle();
        if (h != null)
        {
          BComponent byH = byHandle.get(String.valueOf(h).trim().toLowerCase());
          if (byH != null) return byH;
        }
      }
    }
    catch (Throwable ignore)
    {
    }

    // 2c. Match by relative or slot-path component name if it points to a child of container
    int slash = ordStr.lastIndexOf('/');
    int colon = ordStr.lastIndexOf(':');
    int idx = Math.max(slash, colon);
    String candidateName = (idx >= 0 && idx + 1 < ordStr.length())
      ? ordStr.substring(idx + 1).trim()
      : ordStr;
    try
    {
      candidateName = SlotPath.unescape(candidateName);
    }
    catch (Throwable ignore)
    {
    }
    BComponent found = byName.get(candidateName.toLowerCase());
    if (found != null)
    {
      // Verify that if ordStr has a parent path, it matches container's path
      if (slash > 0 && container.getSlotPathOrd() != null)
      {
        String containerPath = container.getSlotPathOrd().toString().toLowerCase();
        String ordParent = ordStr.substring(0, slash).toLowerCase();
        if (!ordParent.endsWith(containerPath) && !containerPath.endsWith(ordParent))
        {
          return null;
        }
      }
      return found;
    }

    return null;
  }

  private static String safeName(BComplex c)
  {
    if (c instanceof BComponent)
    {
      String n = ((BComponent) c).getName();
      if (n != null && !n.isEmpty()) return n;
    }
    return c != null && c.getType() != null ? c.getType().getTypeName() : "Component";
  }

  private static final class LinkEdge
  {
    final BComponent source;
    final String sourceSlot;
    final BComponent target;
    final String targetSlot;

    LinkEdge(BComponent source, String sourceSlot, BComponent target, String targetSlot)
    {
      this.source = source;
      this.sourceSlot = sourceSlot;
      this.target = target;
      this.targetSlot = targetSlot;
    }
  }

  private static final Comparator<BComponent> NATURAL_NAME_COMPARATOR = new Comparator<BComponent>()
  {
    @Override
    public int compare(BComponent a, BComponent b)
    {
      return NATURAL_STRING_COMPARATOR.compare(safeName(a), safeName(b));
    }
  };

  /**
   * Case-insensitive natural alphanumeric comparator (so Point2 sorts before Point10).
   */
  private static final Comparator<String> NATURAL_STRING_COMPARATOR = new Comparator<String>()
  {
    @Override
    public int compare(String s1, String s2)
    {
      if (s1 == null) return (s2 == null) ? 0 : -1;
      if (s2 == null) return 1;

      int i1 = 0;
      int i2 = 0;
      int len1 = s1.length();
      int len2 = s2.length();

      while (i1 < len1 && i2 < len2)
      {
        char c1 = s1.charAt(i1);
        char c2 = s2.charAt(i2);

        if (Character.isDigit(c1) && Character.isDigit(c2))
        {
          long num1 = 0;
          while (i1 < len1 && Character.isDigit(s1.charAt(i1)))
          {
            num1 = num1 * 10 + (s1.charAt(i1) - '0');
            i1++;
          }
          long num2 = 0;
          while (i2 < len2 && Character.isDigit(s2.charAt(i2)))
          {
            num2 = num2 * 10 + (s2.charAt(i2) - '0');
            i2++;
          }
          if (num1 != num2)
          {
            return (num1 < num2) ? -1 : 1;
          }
        }
        else
        {
          char u1 = Character.toUpperCase(c1);
          char u2 = Character.toUpperCase(c2);
          if (u1 != u2)
          {
            return u1 - u2;
          }
          i1++;
          i2++;
        }
      }
      return len1 - len2;
    }
  };
}
