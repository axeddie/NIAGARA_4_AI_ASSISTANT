/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.baja.gx.BBrush;
import javax.baja.gx.BColor;
import javax.baja.gx.BImage;
import javax.baja.gx.BInsets;
import javax.baja.gx.Graphics;
import javax.baja.gx.Point;
import javax.baja.sys.BComplex;
import javax.baja.ui.BBorder;
import javax.baja.ui.BToggleButton;
import javax.baja.ui.BWidget;
import javax.baja.ui.BWidgetApplication;
import javax.baja.ui.BWidgetShell;
import javax.baja.ui.CommandArtifact;
import javax.baja.ui.MouseCursor;
import javax.baja.ui.ToggleCommand;
import javax.baja.ui.event.BMouseEvent;
import javax.baja.ui.pane.BBorderPane;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.workbench.BWbShell;

/**
 * Installs and manages the resizable right-side AI panel and toolbar toggle button
 * on Niagara Workbench shells.
 */
public final class EddiesAiShellManager
{
  private static final Logger LOG = Logger.getLogger("EddiesWorkbenchAI");

  private static final Map<BWbShell, ShellAiState> SHELL_STATES =
    Collections.synchronizedMap(new WeakHashMap<BWbShell, ShellAiState>());

  private static volatile boolean watcherStarted = false;

  private EddiesAiShellManager()
  {
  }

  /**
   * Starts a lightweight daemon watcher thread (matching the proven pattern in eddiesWorkbench-wb)
   * that checks for active Workbench shells and docks the right-side AI panel on the UI thread.
   */
  public static synchronized void scheduleInstall()
  {
    if (watcherStarted)
    {
      return;
    }
    watcherStarted = true;

    try
    {
      EddiesAiHomePageCustomizer.customizeAboutHtml();
    }
    catch (Throwable ignore)
    {
    }

    Thread watcher = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        while (true)
        {
          try
          {
            Thread.sleep(500L);
            SwingUtilities.invokeLater(new Runnable()
            {
              @Override
              public void run()
              {
                try
                {
                  installOnAllShells();
                }
                catch (Throwable t)
                {
                  LOG.warning("Error installing Eddie's AI panel: " + t);
                }
              }
            });
          }
          catch (InterruptedException ie)
          {
            return;
          }
          catch (Throwable ignore)
          {
          }
        }
      }
    }, "EddiesAiShellWatcher");
    watcher.setDaemon(true);
    watcher.setPriority(Thread.MIN_PRIORITY);
    watcher.start();
  }

  /**
   * Scans all open Workbench shells and installs the right-side AI panel if not yet installed.
   */
  public static void installOnAllShells()
  {
    try
    {
      BWidgetApplication app = BWidget.getApplication();
      if (app == null) return;

      BWidgetShell[] shells = app.getShells();
      if (shells == null) return;

      for (int i = 0; i < shells.length; i++)
      {
        if (shells[i] instanceof BWbShell)
        {
          installIfNeeded((BWbShell) shells[i]);
        }
      }
    }
    catch (Throwable t)
    {
      LOG.warning("installOnAllShells error: " + t);
    }
  }

  /**
   * Toggles the right-side AI panel open or closed in the specified Workbench shell.
   */
  public static void togglePanel(BWbShell shell)
  {
    if (shell == null) return;
    ShellAiState state = installIfNeeded(shell);
    if (state != null)
    {
      setPanelVisible(shell, !state.dockPane.isVisible());
    }
  }

  /**
   * Shows or hides the right-side AI panel in the specified Workbench shell.
   */
  public static void setPanelVisible(BWbShell shell, boolean visible)
  {
    if (shell == null) return;
    ShellAiState state = SHELL_STATES.get(shell);
    if (state == null)
    {
      state = installIfNeeded(shell);
    }
    if (state == null) return;

    state.dockPane.setVisible(visible);
    if (state.toggleCommand != null && state.toggleCommand.isSelected() != visible)
    {
      state.toggleCommand.setSelected(visible);
    }
    state.dockPane.relayout();
    state.wbPane.relayoutSync();
    state.wbPane.relayout();
    state.wbPane.repaint();

    if (visible)
    {
      state.aiPanel.focusPromptInput();
    }
  }

  private static synchronized ShellAiState installIfNeeded(final BWbShell shell)
  {
    ShellAiState existing = SHELL_STATES.get(shell);
    if (existing != null)
    {
      return existing;
    }

    BWidget root = shell.getContent();
    if (!(root instanceof BEdgePane))
    {
      return null;
    }

    final BEdgePane wbPane = (BEdgePane) root;
    BWidget centerArea = wbPane.getCenter();
    if (centerArea == null || centerArea.isNull())
    {
      return null;
    }

    // Create the right-side AI panel controller and wrap its BEdgePane in a bordered pane
    final BEddiesAiRightPanel aiPanel = new BEddiesAiRightPanel(shell, new Runnable()
    {
      @Override
      public void run()
      {
        setPanelVisible(shell, false);
      }
    });

    BBorderPane panelWrapper = new BBorderPane(aiPanel.getWidget(), BInsets.make(0.0));
    panelWrapper.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(185, 195, 210))));
    panelWrapper.setFill(BBrush.makeSolid(BColor.make(252, 253, 255)));

    // Create the resizable dock container with a draggable splitter bar on its left edge.
    // Docking into wbPane.setRight(...) avoids unparenting any running JavaFX/Swing views in wbPane.getCenter().
    final BResizableDockPane dockPane = new BResizableDockPane(wbPane, panelWrapper, 380.0);
    dockPane.setVisible(true);
    wbPane.setRight(dockPane);

    // Add an "Eddie's AI" toggle button to the right end of the Workbench toolbar row
    ToggleCommand toggleCmd = installToolbarToggleButton(wbPane, shell);

    // Sync context when user activity occurs in the shell
    shell.addActivityListener(new BWbShell.ActivityListener()
    {
      @Override
      public void activity()
      {
        if (dockPane.isVisible())
        {
          aiPanel.syncContextFromShell();
        }
      }
    });

    ShellAiState state = new ShellAiState(wbPane, dockPane, aiPanel, toggleCmd);
    SHELL_STATES.put(shell, state);

    dockPane.relayout();
    wbPane.relayoutSync();
    wbPane.relayout();
    wbPane.repaint();

    EddiesAiHomePageCustomizer.installAndRefresh(shell);

    LOG.info("Docked Eddie's AI Assistant right-side panel into Workbench shell.");
    return state;
  }

  private static ToggleCommand installToolbarToggleButton(BEdgePane wbPane, final BWbShell shell)
  {
    try
    {
      BWidget top = wbPane.getTop();
      if (!(top instanceof BEdgePane)) return null;

      BWidget toolBarRow = ((BEdgePane) top).getCenter();
      if (!(toolBarRow instanceof BEdgePane)) return null;

      BEdgePane toolBarEdge = (BEdgePane) toolBarRow;
      BImage bulbIcon = BImage.NULL;
      try
      {
        bulbIcon = BImage.make("module://icons/x16/lightBulb.png");
      }
      catch (Exception ignore)
      {
      }

      ToggleCommand toggleCmd = new ToggleCommand(
        wbPane,
        "Eddie's AI",
        bulbIcon,
        null,
        "Toggle Eddie's AI Right-Side Panel")
      {
        @Override
        public CommandArtifact doInvoke()
        {
          setPanelVisible(shell, isSelected());
          return null;
        }
      };
      toggleCmd.setSelected(true);

      BToggleButton toggleBtn = new BToggleButton(toggleCmd, true, true);
      BBorderPane btnHolder = new BBorderPane(toggleBtn, BInsets.make(2.0, 6.0, 2.0, 8.0));

      BWidget existingRight = toolBarEdge.getRight();
      if (existingRight == null || existingRight.isNull())
      {
        toolBarEdge.setRight(btnHolder);
        toolBarEdge.relayout();
      }
      return toggleCmd;
    }
    catch (Exception e)
    {
      return null;
    }
  }

  /**
   * Right-docked container pane with a user-resizable preferred width and a draggable
   * left-edge splitter handle.
   */
  private static final class BResizableDockPane extends BEdgePane
  {
    private static final double MIN_WIDTH = 250.0;
    private double dockWidth;

    BResizableDockPane(BEdgePane wbPane, BWidget content, double initialWidth)
    {
      this.dockWidth = initialWidth;
      setLeft(new BLeftResizeHandle(wbPane, this));
      setCenter(content);
    }

    double getDockWidth()
    {
      return dockWidth;
    }

    void setDockWidth(double newWidth)
    {
      double maxW = 900.0;
      BComplex parent = getParent();
      if (parent instanceof BWidget)
      {
        double parentW = ((BWidget) parent).getWidth();
        if (parentW > 400.0)
        {
          maxW = Math.max(MIN_WIDTH + 40.0, parentW - 260.0);
        }
      }
      this.dockWidth = Math.max(MIN_WIDTH, Math.min(newWidth, maxW));
    }

    @Override
    public void computePreferredSize()
    {
      super.computePreferredSize();
      setDockWidth(dockWidth);
      setPreferredSize(dockWidth, Math.max(100.0, getPreferredHeight()));
    }
  }

  /**
   * 6px wide vertical splitter bar on the left edge of the docked AI panel.
   * Dragging left/right smoothly resizes the panel width.
   */
  private static final class BLeftResizeHandle extends BWidget
  {
    private static final double BAR_WIDTH = 6.0;
    private static final BBrush BG_NORMAL = BBrush.makeSolid(BColor.make(222, 228, 236));
    private static final BBrush BG_ACTIVE = BBrush.makeSolid(BColor.make(175, 198, 230));
    private static final BBrush GRIP_BRUSH = BBrush.makeSolid(BColor.make(120, 132, 150));

    private final BEdgePane wbPane;
    private final BResizableDockPane dockPane;
    private boolean hovered;
    private boolean dragging;
    private double dragStartMouseX;
    private double dragStartWidth;

    BLeftResizeHandle(BEdgePane wbPane, BResizableDockPane dockPane)
    {
      this.wbPane = wbPane;
      this.dockPane = dockPane;
      setMouseCursor(MouseCursor.wResize);
    }

    @Override
    public void computePreferredSize()
    {
      setPreferredSize(BAR_WIDTH, 100.0);
    }

    @Override
    public boolean receiveInputEvents()
    {
      return true;
    }

    @Override
    public Object fw(int id, Object a, Object b, Object c, Object d)
    {
      if (id == 304)
      {
        return MouseCursor.wResize;
      }
      return super.fw(id, a, b, c, d);
    }

    @Override
    public void mouseEntered(BMouseEvent event)
    {
      hovered = true;
      setMouseCursor(MouseCursor.wResize);
      repaint();
    }

    @Override
    public void mouseMoved(BMouseEvent event)
    {
      setMouseCursor(MouseCursor.wResize);
    }

    @Override
    public void mouseExited(BMouseEvent event)
    {
      hovered = false;
      if (!dragging)
      {
        repaint();
      }
    }

    @Override
    public void mousePressed(BMouseEvent event)
    {
      dragging = true;
      Point ptInWb = translateToAncestor(wbPane, new Point(event.getX(), event.getY()));
      dragStartMouseX = ptInWb.x;
      dragStartWidth = dockPane.getDockWidth();
      repaint();
    }

    @Override
    public void mouseDragged(BMouseEvent event)
    {
      if (!dragging) return;
      Point ptInWb = translateToAncestor(wbPane, new Point(event.getX(), event.getY()));
      double deltaX = dragStartMouseX - ptInWb.x;
      dockPane.setDockWidth(dragStartWidth + deltaX);
      dockPane.relayout();
      wbPane.relayoutSync();
      wbPane.relayout();
      wbPane.repaint();
    }

    @Override
    public void mouseReleased(BMouseEvent event)
    {
      if (dragging)
      {
        dragging = false;
        dockPane.relayout();
        wbPane.relayoutSync();
        wbPane.relayout();
        wbPane.repaint();
      }
    }

    @Override
    public void paint(Graphics g)
    {
      double w = getWidth();
      double h = getHeight();
      if (w <= 0 || h <= 0) return;

      g.setBrush((hovered || dragging) ? BG_ACTIVE : BG_NORMAL);
      g.fillRect(0.0, 0.0, w, h);

      // Draw a subtle vertical grip indicator in the center of the splitter bar
      double cx = Math.floor(w / 2.0) - 1.0;
      double cy = h / 2.0;
      g.setBrush(GRIP_BRUSH);
      for (int i = -3; i <= 3; i++)
      {
        g.fillRect(cx, cy + (i * 5.0), 2.0, 2.0);
      }
    }
  }

  private static final class ShellAiState
  {
    final BEdgePane wbPane;
    final BResizableDockPane dockPane;
    final BEddiesAiRightPanel aiPanel;
    final ToggleCommand toggleCommand;

    ShellAiState(
      BEdgePane wbPane,
      BResizableDockPane dockPane,
      BEddiesAiRightPanel aiPanel,
      ToggleCommand toggleCommand)
    {
      this.wbPane = wbPane;
      this.dockPane = dockPane;
      this.aiPanel = aiPanel;
      this.toggleCommand = toggleCommand;
    }
  }
}
