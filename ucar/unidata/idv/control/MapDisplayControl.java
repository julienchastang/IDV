/*
 * $Id: MapDisplayControl.java,v 1.95 2007/08/21 22:41:38 jeffmc Exp $
 *
 * Copyright  1997-2004 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */




package ucar.unidata.idv.control;


import org.w3c.dom.Element;

import ucar.unidata.data.DataChoice;

import ucar.unidata.gis.maps.*;

import ucar.unidata.idv.IdvResourceManager;
import ucar.unidata.idv.MapViewManager;


import ucar.unidata.ui.LatLonPanel;
import ucar.unidata.ui.MapPanel;


import ucar.unidata.util.FileManager;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.IOUtil;
import ucar.unidata.util.LogUtil;


import ucar.unidata.util.Misc;

import ucar.unidata.util.PatternFileFilter;
import ucar.unidata.util.Resource;
import ucar.unidata.util.TwoFacedObject;
import ucar.unidata.xml.*;



import ucar.visad.display.*;


import visad.*;

import visad.georef.*;


import java.awt.*;
import java.awt.Container;
import java.awt.event.*;

import java.net.URL;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;


/**
 * A control for displaying map lines.
 *
 * @author IDV Development Team
 * @version  $Revision: 1.95 $
 */
public class MapDisplayControl extends DisplayControlImpl {


    /** Where we put the map guis */
    private JPanel contents;

    /**
     * Set by the map xml, holds the level of the map used in the
     * positionSlider
     */
    private double mapPosition = -.99;


    /**
     * List of MapState objects
     */
    private List mapStates = new ArrayList();


    /** Holds the latitude display info */
    private LatLonState latState = null;

    /** Holds the longitude display info */
    private LatLonState lonState = null;


    /**
     * The initialMap - set by the property in the controls.xml
     */
    private String initialMap;

    /**
     * The description of the initialMap
     */
    private String initialMapDescription;


    /**
     * Holds the mapsHolder and the latLonHolder
     */
    private CompositeDisplayable theHolder;


    /**
     * This holds the map displayables
     */
    private CompositeDisplayable mapsHolder;

    /**
     * This holds the latlon displayables
     */
    private CompositeDisplayable latLonHolder;



    /**
     * Is this map display the one used for the default map of a view manager.
     */
    private boolean isDefaultMap = false;

    /**
     * A flag that gets set when the user creates the 'default map'
     * This flag tells the MapDisplay to initialize itself with the user's
     * default maps.
     */
    private boolean initializeAsDefault = false;

    /**
     * This holds the list of default  map data objects when this display control
     *  is used as the default map in a map view.
     */
    private List defaultMapData;

    /**
     * This holds the default lat. data  when this display control
     *  is used as the default map in a map view.
     */
    private LatLonData defaultLatData;

    /**
     * This holds the default long. data  when this display control
     *  is used as the default map in a map view.
     */
    private LatLonData defaultLonData;

    /**
     * Have this around when we are initializing.
     * It tells us whether to not include maps in the map list
     * that are not visible
     */
    private boolean ignoreNonVisibleMaps = true;


    /**
     * Have this around when we are initializing.
     * It tells us whether to not to show this in the display list
     */
    private boolean myShowInDisplayList = false;


    /**
     * position slider
     */
    private JSlider levelSlider = null;

    /** flag for slider events */
    private boolean ignoreSliderEvents = false;

    /** static counter */
    static int cnt = 0;

    /** instance for this instantiation */
    int mycnt = cnt++;

    /**
     * Default Constructor.
     */
    public MapDisplayControl() {
        setLockVisibilityToggle(true);
    }



    /**
     * Special constructor  for creating a map display for a particular
     * MapViewManager
     *
     * @param mapViewManager   The map view manager this map display is
     *                         the default map for
     * @param mapInfo          Holds the map info
     */
    public MapDisplayControl(MapViewManager mapViewManager, MapInfo mapInfo) {
        super(mapViewManager.getIdv());
        setLockVisibilityToggle(true);
        defaultViewManager  = mapViewManager;
        this.mapPosition    = mapInfo.getMapPosition();
        this.defaultMapData = mapInfo.getMapDataList();
        this.defaultLatData = mapInfo.getLatData();
        this.defaultLonData = mapInfo.getLonData();
        if (mapInfo.getJustLoadedLocalMaps()) {
            ignoreNonVisibleMaps = false;
        }
    }



    /**
     * Special constructor  for creating a map display for a particular
     * MapData
     *
     * @param mapData The mapData  may be null.  If null, then the user
     *                is prompted to choose a map.
     */
    public MapDisplayControl(MapData mapData) {
        setLockVisibilityToggle(true);
        this.defaultMapData = (mapData == null)
                              ? new ArrayList()
                              : Misc.newList(mapData);
    }



    /**
     * Append any label information to the list of labels.
     *
     * @param labels   in/out list of labels
     * @param legendType The type of legend, BOTTOM_LEGEND or SIDE_LEGEND
     */
    public void getLegendLabels(List labels, int legendType) {
        //        System.err.println ("getLegendLabels " + mapStates.size());
        super.getLegendLabels(labels, legendType);
        int cnt = 0;
        int i   = 0;

        for (; i < mapStates.size(); i++) {
            MapState mapState = (MapState) mapStates.get(i);
            if ( !mapState.getVisible()) {
                continue;
            }
            labels.add(mapState.getDescription());
            //Only put in three maps
            if (cnt++ > 2) {
                if (i < mapStates.size() - 1) {
                    labels.add("...");
                }
                break;
            }
        }
    }




    /**
     * Clear the current state and copy the state held by the given newMap
     *
     * @param newMap The map display we copy from
     */
    public void loadNewMap(MapDisplayControl newMap) {
        setDisplayInactive();
        try {
            setLegendLabelTemplate(newMap.getLegendLabelTemplate());
            setDisplayListTemplate(newMap.getDisplayListTemplate());
            setShowInDisplayList(newMap.getShowInDisplayList());
            if (newMap.getDisplayListColor() != null) {
                setDisplayListColor(newMap.getDisplayListColor());
            }

            //Use the collapsed/expanded state from the new map
            setCollapseLegend(newMap.getCollapseLegend());
            deactivateDisplays();
            this.latState.initWith(newMap.latState);
            this.lonState.initWith(newMap.lonState);
            if (getDisplayVisibility() != newMap.getDisplayVisibility()) {
                setDisplayVisibility(newMap.getDisplayVisibility());
            }

            for (int i = 0; i < mapStates.size(); i++) {
                Displayable theMap = ((MapState) mapStates.get(i)).getMap();
                if (theMap != null) {
                    mapsHolder.removeDisplayable(theMap);
                }
            }

            this.mapStates = new ArrayList();
            for (int i = 0; i < newMap.mapStates.size(); i++) {
                MapState mapState =
                    new MapState((MapData) newMap.mapStates.get(i));
                if (mapState.init(this)) {
                    mapStates.add(mapState);
                }
            }


            this.mapPosition = newMap.mapPosition;

            if (contents != null) {
                fillContents();
            }

            setSliderPosition();
            applyMapPosition();
            activateDisplays();
            updateLegendLabel();
        } catch (Exception exc) {
            logException("Loading new map", exc);
        }
        setDisplayActive();
    }


    /**
     * Called to make this kind of Display Control; also calls code to
     * made its Displayable.
     *
     * @param dataChoice the DataChoice of the moment -
     *                   not used yet; can be null.
     *
     * @return  true if successful
     *
     * @throws RemoteException  Java RMI error
     * @throws VisADException   VisAD Error
     */
    public boolean init(DataChoice dataChoice)
            throws VisADException, RemoteException {

        theHolder    = new CompositeDisplayable("theHolder " + mycnt);
        mapsHolder   = new CompositeDisplayable("maps holder " + mycnt);
        latLonHolder = new CompositeDisplayable("latlonholder " + mycnt);
        theHolder.addDisplayable(mapsHolder);
        theHolder.addDisplayable(latLonHolder);

        addDisplayable(theHolder);

        if (initializeAsDefault) {
            initialMap          = null;
            initializeAsDefault = false;
            isDefaultMap        = true;
            setCanDoRemoveAll(false);
            MapInfo mapInfo =
                getControlContext().getResourceManager().createMapInfo();
            if (mapInfo.getJustLoadedLocalMaps()) {
                ignoreNonVisibleMaps = false;
            }
            defaultMapData = mapInfo.getMapDataList();
            defaultLatData = mapInfo.getLatData();
            defaultLonData = mapInfo.getLonData();
            mapPosition    = mapInfo.getMapPosition();
        }

        ignoreNonVisibleMaps = false;

        if (defaultMapData != null) {
            if ( !defaultMapData.isEmpty()) {
                for (int i = 0; i < defaultMapData.size(); i++) {
                    MapData mapData = (MapData) defaultMapData.get(i);
                    if (mapData.getVisible() || !ignoreNonVisibleMaps) {
                        addMap(new MapState(mapData));
                    }
                }
            } else {  // constructed with null MapData
                if ( !selectMap()) {
                    return false;
                }
            }

            defaultMapData = null;
        }

        if (defaultLatData != null) {
            latState = new LatLonState(defaultLatData);
            latState.init(this);
        }

        if (defaultLonData != null) {
            lonState = new LatLonState(defaultLonData);
            lonState.init(this);
        }

        if (((initialMap != null) && (initialMap.trim().length() > 0))
                && !isDefaultMap) {
            addMap(initialMap, (initialMapDescription != null)
                               ? initialMapDescription
                               : initialMap);
            initialMap            = null;
            initialMapDescription = null;
        }


        //Now check for any persisted maps
        for (int i = 0; i < mapStates.size(); i++) {
            ((MapState) mapStates.get(i)).init(this);
        }
        setSliderPosition();
        applyMapPosition();
        return true;
    }




    /**
     * Helper method to create a LatLonState object
     *
     * @param latitude Is it latitude or longitude
     * @param min Minimum value
     * @param max Maximum value
     * @param spacing Line spacing
     *
     * @return The LatLonState
     */
    private LatLonState createLatLonState(boolean latitude, float min,
                                          float max, float spacing) {
        LatLonState lls = new LatLonState(latitude, Color.blue, spacing,
                                          1.0f, 1);
        lls.init(this);
        lls.setVisible(false);
        lls.setFastRendering(getDefaultFastRendering());
        lls.setMinValue(min);
        lls.setMaxValue(max);
        return lls;
    }


    /**
     * Merge the maps contained by that into this
     *
     * @param that The other display control to merge its maps
     */
    public void merge(MapDisplayControl that) {}


    /**
     * Overwrite base class method so we can apply the visibility changes
     * to the maps.
     *
     * @param on Is visible
     */
    public void setDisplayVisibility(boolean on) {
        try {
            super.setDisplayVisibility(on);
            for (int i = 0; i < mapStates.size(); i++) {
                MapState mapState = (MapState) mapStates.get(i);
                mapState.checkVisibility();
            }
            if (latState != null) {
                latState.checkVisibility();
                lonState.checkVisibility();
            }
        } catch (Exception exc) {
            logException("Setting visibility", exc);
        }
    }


    /**
     * Add the given map path into the list of maps
     *
     * @param mapPath Url or resource path of the map file
     * @param description The description of the map
     */
    private void addMap(String mapPath, String description) {
        addMap(new MapState(mapPath, description, Color.blue, 1.0f, 0));
    }



    /**
     * Add the given MapState into the list of maps
     *
     * @param mapState Url or resource path of the map file
     */
    private void addMap(MapState mapState) {
        setDisplayInactive();
        if (mapState.init(this)) {
            mapStates.add(mapState);
            updateLegendLabel();
        }
        setDisplayActive();
    }


    /**
     * Add the  relevant view menu items into the list
     *
     * @param items List of menu items
     * @param forMenuBar Is this for the menu in the window's menu bar or
     * for a popup menu in the legend
     */
    protected void getViewMenuItems(List items, boolean forMenuBar) {
        JMenu mapsMenu = new JMenu("Maps");
        items.add(mapsMenu);
        for (int i = 0; i < mapStates.size(); i++) {
            final MapState mapState = (MapState) mapStates.get(i);
            JCheckBoxMenuItem cbx =
                new JCheckBoxMenuItem(mapState.getDescription(),
                                      mapState.getVisible());
            cbx.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent event) {
                    mapState.setVisible( !mapState.getVisible());
                    mapState.mapPanel.setVisibility(mapState.getVisible());
                }
            });
            mapsMenu.add(cbx);
        }
    }


    /**
     * Add the  relevant edit menu items into the list
     *
     * @param items List of menu items
     * @param forMenuBar Is this for the menu in the window's menu bar or
     * for a popup menu in the legend
     */
    protected void getEditMenuItems(List items, boolean forMenuBar) {
        JMenuItem mi;
        //        if ( !forMenuBar) {            return;        }

        mi = new JMenuItem("Add Your Own Map...");
        mi.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                selectMap();
            }
        });
        items.add(mi);
        JMenu addMapMenu = new JMenu("Add System Map");
        items.add(addMapMenu);

        List maps = getControlContext().getResourceManager().getMaps();
        for (int i = 0; i < maps.size(); i++) {
            final MapData mapData = (MapData) maps.get(i);
            mi = new JMenuItem(mapData.getDescription());
            addMapMenu.add(mi);
            mi.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    addMap(new MapState(mapData));
                    fillContents();
                }
            });
        }
        super.getEditMenuItems(items, forMenuBar);
    }


    /**
     * Add the  relevant file menu items into the list
     *
     * @param items List of menu items
     * @param forMenuBar Is this for the menu in the window's menu bar or
     * for a popup menu in the legend
     */
    protected void getFileMenuItems(List items, boolean forMenuBar) {

        super.getFileMenuItems(items, forMenuBar);
        JMenu     defaultMenu = new JMenu("Default Maps");
        JMenuItem mi = new JMenuItem("Save as the Default Map Preference");
        mi.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                saveAsPreference();
            }
        });
        defaultMenu.add(mi);


        mi = new JMenuItem("Remove Local Map Defaults");
        mi.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if (GuiUtils.askYesNo(
                        "Remove Default Maps",
                        "Are you sure you want to remove the default maps?")) {
                    if (getControlContext().getResourceManager()
                            .removeLocalMaps()) {
                        LogUtil.userMessage(
                            "This will take effect when you run the IDV next");
                    } else {
                        LogUtil.userMessage(
                            "There were no local default maps defined");
                    }
                }
            }
        });
        defaultMenu.add(mi);
        items.add(defaultMenu);
    }


    /**
     * Add the  relevant File-&gt;Save menu items into the list
     *
     * @param items List of menu items
     * @param forMenuBar Is this for the menu in the window's menu bar or
     * for a popup menu in the legend
     */
    protected void getSaveMenuItems(List items, boolean forMenuBar) {

        super.getSaveMenuItems(items, forMenuBar);
        items.add(GuiUtils.makeMenuItem("Export to Plugin", this,
                                        "saveToPlugin"));
    }




    /**
     * Ask the user to choose a map file and try to add it.
     *
     * @return true if a map was selected and added
     */
    protected boolean selectMap() {

        final JPanel colorButton = new GuiUtils.ColorSwatch(Color.blue,
                                       "Set Map Line Color");
        colorButton.setToolTipText("Set the line color");

        final JTextField nameFld = new JTextField("", 20);
        nameFld.setToolTipText("Enter an optional map name");
        final JTextField fileFld   = new JTextField("", 20);
        JButton          browseBtn = new JButton("Browse");
        browseBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                String filename =
                    FileManager.getReadFile(new PatternFileFilter(".+\\.shp",
                        "Shape Files (*.shp)"));
                if (filename != null) {
                    fileFld.setText(filename);
                }
            }
        });

        JComboBox styleBox = new JComboBox(new String[] { "_____", "_ _ _",
                ".....", "_._._" });
        styleBox.setMaximumSize(new Dimension(30, 16));
        styleBox.setToolTipText("Set the line style");
        styleBox.setSelectedIndex(0);

        JComboBox widthBox = new JComboBox(new String[] { "1.0", "1.5", "2.0",
                "2.5", "3.0" });
        widthBox.setToolTipText("Set the line width");
        widthBox.setMaximumSize(new Dimension(30, 16));
        widthBox.setEditable(true);

        while (true) {
            GuiUtils.setHFill();
            JPanel fileLine = GuiUtils.doLayout(new Component[] { fileFld,
                    browseBtn }, 2, GuiUtils.WT_YN, GuiUtils.WT_N);
            JPanel nameLine = GuiUtils.left(GuiUtils.hbox(nameFld,
                                  new JLabel(" (Optional)")));
            GuiUtils.tmpInsets = new Insets(4, 4, 4, 4);

            JPanel panel = GuiUtils.doLayout(new Component[] {
                GuiUtils.rLabel("Map file or URL: "), fileLine,
                GuiUtils.rLabel("Name: "), nameLine,
                GuiUtils.rLabel("Color: "), GuiUtils.left(colorButton),
                GuiUtils.rLabel("Line style: "), GuiUtils.left(styleBox),
                GuiUtils.rLabel("Line width: "), GuiUtils.left(widthBox)
            }, 2, GuiUtils.WT_NY, GuiUtils.WT_N);
            if ( !GuiUtils.showOkCancelDialog(null, "Add a map",
                    GuiUtils.inset(panel, 4), null)) {
                return false;
            }
            String filename = fileFld.getText().trim();
            if (filename.length() == 0) {
                userMessage("Please select a map file or URL");
                continue;
            }
            String description = nameFld.getText().trim();
            if (description.trim().length() == 0) {
                description =
                    IOUtil.getFileTail(IOUtil.stripExtension(filename));
            }
            addMap(new MapState(
                filename, description, colorButton.getBackground(),
                Float.parseFloat((String) widthBox.getSelectedItem()),
                styleBox.getSelectedIndex()));
            fillContents();
            break;
        }
        return true;

    }

    /**
     * Turn the map state held by this object into xml and write it out as the
     * user's map preference.
     */
    private void saveAsPreference() {
        String xml = new MapInfo(mapStates, latState, lonState,
                                 (float) mapPosition).getXml();
        getControlContext().getResourceManager().writeMapState(xml);
    }

    /**
     * Turn the map state held by this object into xml and write it out as the
     * user's map preference.
     */
    public void saveToPlugin() {
        String xml = new MapInfo(mapStates, latState, lonState,
                                 (float) mapPosition).getXml();
        getControlContext().getIdv().getPluginManager().addText(xml,
                "maps.xml");
    }


    /**
     * Make the UI contents for this control.
     *
     * @return  UI container
     */
    public Container doMakeContents() {
        contents = new JPanel(new BorderLayout());
        if (latState == null) {
            latState = createLatLonState(true, -90.f, 90.f, 30.f);
        }
        if (lonState == null) {
            lonState = createLatLonState(false, -180.f, 180.f, 45.f);
        }
        LatLonPanel latPanel = new LatLonPanel(latState);
        latState.myLatLonPanel = latPanel;
        LatLonPanel lonPanel = new LatLonPanel(lonState);
        lonState.myLatLonPanel = lonPanel;
        JPanel llPanel = LatLonPanel.layoutPanels(latPanel, lonPanel);

        try {
            latLonHolder.addDisplayable(latState.getLatLonLines());
            latLonHolder.addDisplayable(lonState.getLatLonLines());
        } catch (Exception exc) {
            logException("Initializing latlon lines", exc);
        }

        JScrollPane sp =
            new JScrollPane(
                contents, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(10);

        JViewport vp = sp.getViewport();
        vp.setViewSize(new Dimension(600, 200));
        sp.setPreferredSize(new Dimension(600, 200));


        JPanel displayPanel = GuiUtils.topCenter(llPanel, ( !useZPosition()
                ? GuiUtils.filler()
                : GuiUtils.top(makePositionSlider())));


        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Maps", sp);
        tabbedPane.add("Settings", displayPanel);

        /**
         *
         * JPanel outerPanel = GuiUtils.topCenter(
         *                       llPanel, GuiUtils.topCenter(
         *                           GuiUtils.vbox(
         *                               makePositionSlider(), new JLabel(
         *                                   " Maps:")), sp));
         */
        fillContents();


        return tabbedPane;

        //        return outerPanel;
    }



    /**
     * Apply the map (height) position to the displays
     */
    private void applyMapPosition() {
        try {
            DisplayRealType drt = getDisplayAltitudeType();
            if (drt == null) {
                return;
            }
            double[] range        = new double[2];
            double   realPosition = 0;
            if (drt.getRange(range)) {
                // range is -1 to 1, need to normalize to display
                double pcnt = (mapPosition - (-1)) / 2;
                if ( !useZPosition()) {
                    pcnt = .51;
                }
                realPosition = Math.min((range[0]
                                         + (range[1] - range[0])
                                           * pcnt), range[1]);
            }

            ConstantMap constantMap = new ConstantMap(realPosition, drt);
            theHolder.addConstantMap(constantMap);
        } catch (Exception exc) {
            logException("Setting map position", exc);
        }

    }


    /**
     * Create and return the JSliderused to set the map position
     *
     * @return Map position slider
     */
    private JComponent makePositionSlider() {
        levelSlider = new JSlider(-99, 100, (int) (100 * mapPosition));
        levelSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JSlider slider = (JSlider) e.getSource();
                if ( !slider.getValueIsAdjusting() && !ignoreSliderEvents) {
                    mapPosition = slider.getValue() / 100.;
                    applyMapPosition();
                }
            }
        });
        JPanel labelPanel = GuiUtils.leftCenterRight(new JLabel("Bottom"),
                                GuiUtils.cLabel("Middle"),
                                GuiUtils.rLabel("Top"));

        JPanel sliderPanel = GuiUtils.doLayout(new Component[] {
                                 new JLabel("Map position:   "),
                                 levelSlider, new JLabel(" "),
                                 labelPanel }, 2, GuiUtils.WT_NY,
                                     GuiUtils.WT_NN);
        return GuiUtils.inset(sliderPanel, new Insets(20, 4, 4, 4));

    }


    /**
     * Set the slider position without throwing an event
     */
    private void setSliderPosition() {
        if (levelSlider != null) {
            ignoreSliderEvents = true;
            levelSlider.setValue((int) (100 * mapPosition));
            ignoreSliderEvents = false;
        }
    }


    /**
     * This method removes any old components from the contents panel and
     * adds the attrbiute panel for each map.
     */
    private void fillContents() {

        if (contents == null) {
            return;
        }
        contents.removeAll();

        ImageIcon upIcon =
            GuiUtils.getImageIcon(
                "/ucar/unidata/idv/control/images/LevelUp.gif");
        ImageIcon downIcon =
            GuiUtils.getImageIcon(
                "/ucar/unidata/idv/control/images/LevelDown.gif");

        ImageIcon removeIcon =
            GuiUtils.getImageIcon("/auxdata/ui/icons/Delete16.gif");


        int       colCnt = 0;
        Hashtable catMap = new Hashtable();
        List      cats   = new ArrayList();
        for (int i = 0; i < mapStates.size(); i++) {
            final int listIndex = i;
            MapState  mapState  = (MapState) mapStates.get(i);
            String    cat       = mapState.getCategory();
            if (cat == null) {
                cat = "Maps";
            }
            List mapPanels = (List) catMap.get(cat);
            if (mapPanels == null) {
                mapPanels = new ArrayList();
                catMap.put(cat, mapPanels);
                cats.add(cat);
            }
            MapPanel mapPanel = new MapPanel(mapState);
            mapState.mapPanel = mapPanel;

            JButton removeBtn = new JButton(removeIcon);
            removeBtn.setContentAreaFilled(false);
            removeBtn.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            removeBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    removeMap(listIndex);
                }
            });
            removeBtn.setToolTipText("Remove the map");


            JButton moveUpBtn = new JButton(upIcon);
            moveUpBtn.setContentAreaFilled(false);
            moveUpBtn.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            moveUpBtn.setToolTipText("Move the map up");
            moveUpBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    moveMap(listIndex, -1);
                }
            });

            moveUpBtn.setEnabled(listIndex > 0);
            JButton moveDownBtn = new JButton(downIcon);
            moveDownBtn.setContentAreaFilled(false);
            moveDownBtn.setToolTipText("Move the map down");
            moveDownBtn.setBorder(BorderFactory.createEmptyBorder(2, 2, 2,
                    2));
            moveDownBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    moveMap(listIndex, 1);
                }
            });
            moveDownBtn.setEnabled(listIndex < mapStates.size() - 1);

            JPanel upDownPanel = GuiUtils.doLayout(new Component[] {
                                     moveUpBtn,
                                     moveDownBtn }, 1, GuiUtils.WT_N,
                                         GuiUtils.WT_N);
            mapPanels.add(removeBtn);
            mapPanels.add(upDownPanel);
            mapPanels.addAll(mapPanel.getGuiComponents());
            if (i == 0) {
                colCnt = mapPanels.size();
            }
            /*            mapPanels.add(
                GuiUtils.leftCenter(
                GuiUtils.leftCenter(removeBtn, upDownPanel), mapPanel));*/
        }

        final ImageIcon openIcon =
            GuiUtils.getImageIcon("/auxdata/ui/icons/CategoryOpen.gif");

        final ImageIcon closeIcon =
            GuiUtils.getImageIcon("/auxdata/ui/icons/CategoryClosed.gif");

        List comps = new ArrayList();
        List cbxs  = new ArrayList();
        for (int i = 0; i < cats.size(); i++) {
            String cat       = (String) cats.get(i);
            List   mapPanels = (List) catMap.get(cat);
            GuiUtils.tmpInsets = new Insets(3, 3, 3, 3);
            final JPanel catPanel =
                GuiUtils.inset(GuiUtils.doLayout(mapPanels, colCnt,
                    GuiUtils.WT_NNY, GuiUtils.WT_N), new Insets(0, 10, 0, 0));

            final JToggleButton cbx = new JToggleButton(openIcon, true);
            cbxs.add(cbx);
            cbx.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            cbx.setContentAreaFilled(false);
            cbx.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    catPanel.setVisible( !catPanel.isVisible());
                    if (catPanel.isVisible()) {
                        cbx.setIcon(openIcon);
                    } else {
                        cbx.setIcon(closeIcon);
                    }


                }
            });
            comps.add(GuiUtils.leftCenter(GuiUtils.inset(cbx, 5),
                                          new JLabel(cat)));
            comps.add(catPanel);
        }


        if (cbxs.size() > 1) {
            for (int i = 0; i < cbxs.size(); i++) {
                ((JToggleButton) cbxs.get(i)).doClick();
            }
        }

        JComponent mapGui = GuiUtils.vbox(comps);
        mapGui = GuiUtils.left(mapGui);
        //        GuiUtils.tmpInsets = new Insets(3, 3, 3, 3);
        //        JComponent mapGui = GuiUtils.doLayout(comps, 1,
        //                                GuiUtils.WT_NNY, GuiUtils.WT_N);
        contents.add(GuiUtils.top(mapGui));
        contents.repaint();
        contents.validate();
        updateLegendLabel();

    }


    /**
     * Remove the map at position index.
     *
     * @param index Index of map to remove
     */
    private void removeMap(int index) {
        try {
            MapState    mapState = (MapState) mapStates.remove(index);
            Displayable theMap   = mapState.getMap();
            if (theMap != null) {
                mapsHolder.removeDisplayable(theMap);
            }
            fillContents();
        } catch (Exception exc) {
            logException("Removing map", exc);
        }
    }



    /**
     * Move the map at position index either up in the list (delta=1)
     * or down (delta=-1).
     *
     * @param index Index of map to move
     * @param delta What direction
     */
    private void moveMap(int index, int delta) {
        setDisplayInactive();
        try {
            MapState mapState = (MapState) mapStates.remove(index);
            mapStates.add(index + delta, mapState);
            //When we have an addDisplayable method here we'll use that
            //but for now we'll remove them all  and then re-add them
            //      mapsHolder.removeDisplayable (mapState.getMap()); make sure the map is non-null
            //      mapsHolder.addDisplayable (index+delta, mapState.getMap());
            deactivateDisplays();
            for (int i = 0; i < mapStates.size(); i++) {
                Displayable theMap = ((MapState) mapStates.get(i)).getMap();
                if (theMap != null) {
                    mapsHolder.removeDisplayable(theMap);
                }
            }
            for (int i = 0; i < mapStates.size(); i++) {
                Displayable theMap = ((MapState) mapStates.get(i)).getMap();
                if (theMap != null) {
                    mapsHolder.addDisplayable(theMap);
                }
            }
            fillContents();
            activateDisplays();
        } catch (Exception exc) {
            logException("Moving map position", exc);
        }
        setDisplayActive();
    }


    /**
     * Method to call if projection changes.  Override superclass
     * method to set vertical position ConstantMaps for new projection.
     */
    public void projectionChanged() {
        super.projectionChanged();
        applyMapPosition();
    }

    /**
     * Set the InitialMap property. This is the property that is set from
     * the controls.xml file that defines the initial, default map to use.
     *
     * @param value The new value for InitialMap
     */
    public void setInitialMap(String value) {
        initialMap = value;
    }

    /**
     * Get the InitialMap property.
     *
     * @return The InitialMap
     */
    public String getInitialMap() {
        return initialMap;
    }


    /**
     * Set the InitialMapDescription property. This is the property that is set
     * from the controls.xml file that defines the initial, default map to use.
     *
     * @param value The new value for InitialMapDescription
     */
    public void setInitialMapDescription(String value) {
        initialMapDescription = value;
    }

    /**
     * Get the InitialMapDescription property.
     *
     * @return The InitialMapDescription
     */
    public String getInitialMapDescription() {
        return initialMapDescription;
    }


    /**
     * This class holds the state associated with a given lat/lon
     */
    public static class LatLonState extends LatLonData {

        /** This display control I am part of */
        private MapDisplayControl mapDisplayControl;

        /** The panel that represents me */
        LatLonPanel myLatLonPanel;

        /** Flag to keep from infinite looping */
        private boolean ignoreStateChange = false;

        /**
         * Parameterless ctor
         */
        public LatLonState() {}


        /**
         * Ctor for creating from a LatLonData
         *
         * @param that The object to copy from
         */
        public LatLonState(LatLonData that) {
            super(that);
        }

        /**
         * The ctor
         *
         * @param isLatitude Is this latitude or longitude
         * @param color Line color
         * @param spacing Line spacing
         * @param lineWidth Line width
         * @param lineStyle Line style
         */
        public LatLonState(boolean isLatitude, Color color, float spacing,
                           float lineWidth, int lineStyle) {
            super(isLatitude, color, spacing, lineWidth, lineStyle);
        }


        /**
         * Set the MapDisplayControl
         *
         * @param mapDisplayControl The display I am part of
         */
        protected void init(MapDisplayControl mapDisplayControl) {
            this.mapDisplayControl = mapDisplayControl;
        }


        /**
         * Copy the state from the given LatLonData
         *
         * @param that The LatLonData to copy from
         */
        public void initWith(LatLonData that) {
            try {
                super.initWith(that);

                if (myLatLonPanel != null) {
                    myLatLonPanel.setLatLonData(this);
                }
                //This applies my state to the latlonlines
                getLatLonLines();
            } catch (Exception exc) {
                mapDisplayControl.logException("initWith", exc);
            }
        }


        /**
         * Apply the visibility to the myLatLon
         */
        protected void checkVisibility() {
            if ((myLatLon == null) || (myLatLonPanel == null)) {
                return;
            }
            try {
                myLatLon.setVisible(getRealVisibility());
            } catch (Exception exc) {
                mapDisplayControl.logException("Setting visibility", exc);
            }
        }

        /**
         * Overwrite the base class method to take into account the
         * display's visibility
         *
         * @return THe actual visibility to use
         */
        protected boolean getRealVisibility() {
            if (mapDisplayControl == null) {
                return super.getRealVisibility();
            }
            return super.getRealVisibility()
                   && mapDisplayControl.getDisplayVisibility();
        }


        /**
         * Called by the base class when one of the latlon values have changed.
         */
        public void stateChanged() {
            if (ignoreStateChange || (myLatLon == null)
                    || (myLatLonPanel == null)) {
                return;
            }
            ignoreStateChange = true;
            try {
                myLatLonPanel.applyStateToData();
                //This triggers the application of the state to the myLatLon
                getLatLonLines();
            } catch (Exception exc) {
                logException("State change", exc);
            }
            ignoreStateChange = false;
        }
    }

    /**
     * This method can be overwritten by the derived classes that do not want the
     * general application of the fast rendering flag.
     *
     * @return Don't use fast rendering
     */
    protected boolean shouldApplyFastRendering() {
        return false;
    }

    /**
     * What is the default fast rendering value
     *
     * @return false
     */
    protected boolean getDefaultFastRendering() {
        return false;
    }


    /**
     * This class holds the state associated with a given map
     */
    public static class MapState extends MapData {

        /** This display control I am part of */
        private MapDisplayControl mapDisplayControl;

        /** map panel */
        private MapPanel mapPanel;

        /**
         * ctor for persistence
         */
        public MapState() {}



        /**
         * ctor for instantiating from a MapData
         *
         * @param that The MapData to copy from
         */
        public MapState(MapData that) {

            this.source        = that.getSource();
            this.category      = that.getCategory();
            this.visible       = that.getVisible();
            this.mapColor      = that.getColor();
            this.lineWidth     = that.getLineWidth();
            this.lineStyle     = that.getLineStyle();
            this.description   = that.getDescription();
            this.fastRendering = that.getFastRendering();
        }


        /**
         * Construct this MapState.
         *
         * @param mapPath File path, resource path or url of the map file.
         * @param description Map description
         * @param mapColor The initial color to use
         * @param lineWidth The initial line width to use
         * @param lineStyle The initial line style to use
         */
        public MapState(String mapPath, String description, Color mapColor,
                        float lineWidth, int lineStyle) {
            super(mapPath, null);
            this.category    = "Maps";
            this.visible     = true;
            this.mapColor    = mapColor;
            this.lineWidth   = lineWidth;
            this.lineStyle   = lineStyle;
            this.description = description;
        }


        /**
         * Create, if needed, and return the SampledSet that represents this map
         *
         * @param source The map source (may be a java resource, url or file)
         *
         * @return The map data
         */
        private SampledSet getData(String source) {
            return MapInfo.createMapData(source);
        }




        /**
         * Take into account the visibility of the display control
         */
        protected void checkVisibility() {
            if (mapDisplayControl == null) {
                return;
            }
            boolean isVisible = getVisible()
                                && mapDisplayControl.getDisplayVisibility();
            try {
                //Check if we need to create the map
                if (myMap == null) {
                    //If we are not visible then don't create the map.
                    if ( !isVisible) {
                        return;
                    }
                    //Load in the data
                    SampledSet mapSet = getData(source);
                    if (mapSet == null) {
                        return;
                    }
                    // System.err.println("creating map:" + description + " from: " + source);
                    myMap = new MapLines("Map:" + source);
                    myMap.setMapLines(mapSet);
                    myMap.setUseFastRendering(fastRendering);
                    //Set the colors, etc.
                    stateChanged();
                    //Add in the map 
                    mapDisplayControl.mapsHolder.addDisplayable(myMap);
                    //Since the stateChanged method calls checkVisiblity again 
                    //we return here
                    return;
                }
                myMap.setVisible(isVisible);
            } catch (Exception exc) {
                mapDisplayControl.logException("Setting visibility", exc);
            }
        }

        /**
         * Initialize the map.
         *
         * @param mapDisplayControl The display control we are part of
         * @return true if everything ok. False otherwise. If a problem this method
         * will signal the user.
         */
        public boolean init(MapDisplayControl mapDisplayControl) {
            this.mapDisplayControl = mapDisplayControl;
            try {
                //Check if we are already initialized
                if (myMap != null) {
                    return true;
                }
                checkVisibility();
                return true;
            } catch (Exception exc) {
                mapDisplayControl.logException("Making map:" + source, exc);
            }
            return false;
        }

        /**
         * A method that allows derived classes to be told
         * when the state has changed.
         */
        protected void stateChanged() {
            try {
                //Call this first to create the myMap if needed
                checkVisibility();
                if (myMap == null) {
                    return;
                }

                myMap.setDisplayInactive();
                myMap.setUseFastRendering(fastRendering);
                myMap.setColor(mapColor);
                myMap.setLineWidth(lineWidth);
                myMap.setLineStyle(lineStyle);
                myMap.setDisplayActive();

                if (mapDisplayControl != null) {
                    mapDisplayControl.updateLegendLabel();
                }
            } catch (Exception exc) {
                //
            }
        }

    }



    /**
     *  Set the MapStates property.
     *
     *  @param value The new value for MapStates
     */
    public void setMapStates(List value) {
        mapStates = value;
    }

    /**
     *  Get the MapStates property.
     *
     *  @return The MapStates
     */
    public List getMapStates() {
        return mapStates;
    }


    /**
     * Get the object that holds the latitude state
     *
     * @return The latitude state
     */
    public LatLonState getLatState() {
        return latState;
    }

    /**
     * Set the object that holds the latitude state
     *
     *
     * @param value The new latitude state
     */
    public void setLatState(LatLonState value) {
        latState = value;
    }


    /**
     * Get the object that holds the longitude state
     *
     * @return The long. state
     */
    public LatLonState getLonState() {
        return lonState;
    }

    /**
     * Set the object that holds the longitude state
     *
     * @param value The new long. state
     */
    public void setLonState(LatLonState value) {
        lonState = value;
    }

    /**
     * Set the IsDefaultMap property.
     *
     * @param value The new value for IsDefaultMap
     */
    public void setIsDefaultMap(boolean value) {
        isDefaultMap = value;
        if (isDefaultMap) {
            setCanDoRemoveAll(false);
        }
    }



    /**
     * Clear out any lingering references
     *
     * @throws RemoteException  remote display problem
     * @throws VisADException   local display problem
     */
    public void doRemove() throws RemoteException, VisADException {
        List infos = getDisplayInfos();
        if ((infos != null) && (infos.size() == 0)) {
            theHolder.destroyAll();
        }
        super.doRemove();
        theHolder    = null;
        mapsHolder   = null;
        latLonHolder = null;
    }


    /**
     * Set the initializeAsDefault property. This is used
     * when we do an addDefaultMap
     *
     * @param value The value
     */
    public void setInitializeAsDefault(boolean value) {
        initializeAsDefault = value;
    }





    /**
     * Get the IsDefaultMap property.
     *
     * @return The IsDefaultMap
     */
    public boolean getIsDefaultMap() {
        return isDefaultMap;
    }

    /**
     *  Set the MapPosition property.
     *
     *  @param value The new value for MapPosition
     */
    public void setMapPosition(double value) {
        mapPosition = value;
    }

    /**
     *  Get the MapPosition property.
     *
     *  @return The MapPosition
     */
    public double getMapPosition() {
        return mapPosition;
    }

    /**
     * Set the ShowInDisplayList property.
     *
     * @param value The new value for ShowInDisplayList
     */
    public void setShowInDisplayList(boolean value) {
        myShowInDisplayList = value;
        super.setShowInDisplayList(value);
    }

    /**
     * Get the ShowInDisplayList property.
     *
     * @return The ShowInDisplayList
     */
    public boolean getShowInDisplayList() {
        return myShowInDisplayList;
    }

}

