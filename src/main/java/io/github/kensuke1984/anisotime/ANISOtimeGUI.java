/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.kensuke1984.anisotime;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.UIManager.LookAndFeelInfo;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GUI for ANISOtime
 * <p>
 * TODO relative absolute small p, s do not show up
 *
 * @author Kensuke Konishi
 * @version 0.5.4.2b
 */
class ANISOtimeGUI extends javax.swing.JFrame {
    /**
     * 2020/1/23
     */
    private static final long serialVersionUID = -685659184433433887L;
    private RaypathWindow raypathWindow;
    private volatile VelocityStructure structure;
    private volatile double eventR;
    /**
     * Epicentral Distance mode: epicentral distance[deg]<br>
     * Ray parameter mode: ray parameter<br>
     */
    private volatile double mostImportant;
    private volatile ComputationMode mode;
    /**
     * 0(default): All, 1: P-SV, 2: SH
     */
    private volatile int polarity;
    private volatile Set<Phase> phaseSet;
    private ParameterInputPanel jPanelParameter;
    private ResultWindow resultWindow;
    private PhaseWindow phaseWindow;

    /**
     * Creates new form TravelTimeGUI
     */
    ANISOtimeGUI() {
        initComponents();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            Logger.getLogger(ANISOtimeGUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        SwingUtilities.invokeLater(() -> new ANISOtimeGUI().setVisible(true));
    }

    void selectRaypath(int i) {
        raypathWindow.selectPath(i);
    }

    private void addPath(double[] x, double[] y) {
        raypathWindow.addPath(x, y);
    }

    private void createNewRaypathTabs() {
        if (raypathWindow != null) raypathWindow.dispose();
        raypathWindow = new RaypathWindow(this, new RaypathPanel(structure));
        resultWindow.clearRows();
    }

    void setStructure(VelocityStructure structure) {
        this.structure = structure;
    }

    /**
     * @param eventDepth [km] depth of the source (NOT radius)
     */
    void setEventDepth(double eventDepth) {
        eventR = structure.earthRadius() - eventDepth;
    }

    /**
     * @param d Epicentral Distance mode: epicentral distance[deg]<br>
     *          Ray parameter mode: ray parameter<br>
     */
    void setMostImportant(double d) {
        mostImportant = d;
    }

    void setMode(ComputationMode mode) {
        this.mode = mode;
        jPanelParameter.changeBorderTitle(mode + "  " + getPoleString());
        jPanelParameter.setMode(mode);
    }

    void changePropertiesVisible() {
        jPanelParameter.changePropertiesVisible();
    }

    /**
     * @param i 0(default): All, 1: P-SV, 2: SH
     */
    void setPolarity(int i) {
        polarity = i;
        phaseWindow.setPolarity(i);
        jPanelParameter.changeBorderTitle(mode + " " + getPoleString());
    }

    private String getPoleString() {
        switch (polarity) {
            case 0:
                return "Polarity:All";
            case 1:
                return "Polarity:P-SV";
            case 2:
                return "Polarity:SH";
            default:
                throw new RuntimeException("Unexpected");
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    private void initComponents() {
        setTitle("ANISOtime " + ANISOtime.VERSION + " " + ANISOtime.CODENAME);
        setLocationRelativeTo(null);
        phaseWindow = new PhaseWindow(this);
        resultWindow = new ResultWindow(this);

        jPanelParameter = new ParameterInputPanel(this);
        JButton buttonCompute = new JButton("Compute");
        JButton buttonSave = new JButton("Save");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        MenuBar jMenuBar1 = new MenuBar(this);
        setJMenuBar(jMenuBar1);

        buttonCompute.addActionListener(this::buttonComputeActionPerformed);

        buttonSave.addActionListener(this::buttonSavePerformed);

        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING).addGroup(
                layout.createSequentialGroup().addContainerGap().addGroup(layout.createParallelGroup(Alignment.CENTER)
                        .addGroup(layout.createSequentialGroup().addGroup(
                                layout.createParallelGroup(Alignment.LEADING).addGroup(layout.createSequentialGroup())
                                        .addComponent(jPanelParameter, GroupLayout.PREFERRED_SIZE, 300,
                                                Short.MAX_VALUE))).addGroup(layout.createSequentialGroup().addGroup(
                                layout.createSequentialGroup().addComponent(buttonCompute).addComponent(buttonSave)))
                        .addComponent(resultWindow)).addContainerGap()));
        layout.setVerticalGroup(layout.createParallelGroup(Alignment.LEADING).addGroup(
                layout.createSequentialGroup().addContainerGap()
                        .addComponent(jPanelParameter, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE).addGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(buttonCompute).addComponent(buttonSave))
                        .addComponent(resultWindow, 100, 100, 100).addContainerGap()));
        pack();
        setLocation(getX() - getWidth() / 2, getY() - getHeight() / 2);
        phaseWindow.setLocation(getX() + getWidth(), getY());
        phaseWindow.setVisible(true);
        setPolarity(0);
        setMode(ComputationMode.EPICENTRAL_DISTANCE);
    }

    /**
     * phases selected at the time considering polarity. When S is
     * checked and polarity is ALL, then SH and SV return.
     */
    void setPhaseSet(Set<String> phaseSet) {
        this.phaseSet = new HashSet<>();

        switch (polarity) {
            case 0:
                for (String phase : phaseSet) {
                    this.phaseSet.add(Phase.create(phase, true));
                    this.phaseSet.add(Phase.create(phase, false));
                }
                return;
            case 1:
                for (String phase : phaseSet)
                    this.phaseSet.add(Phase.create(phase, true));
                return;
            case 2:
                for (String phase : phaseSet) {
                    Phase p = Phase.create(phase, false);
                    if (!p.isPSV()) this.phaseSet.add(p);
                }
                return;
            default:
                throw new RuntimeException("anekusupekutedo");
        }
    }

    /**
     * when the button "Save" is clicked.
     */
    private void buttonSavePerformed(ActionEvent evt) {
        FutureTask<Path> askOutPath = new FutureTask<>(() -> {
            JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));
            fileChooser.setDialogTitle("Output the path?");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int action = fileChooser.showOpenDialog(null);
            if (action == JFileChooser.CANCEL_OPTION || action == JFileChooser.ERROR_OPTION) return null;
            return fileChooser.getSelectedFile().toPath();
        });

        SwingUtilities.invokeLater(askOutPath);

        Runnable output = () -> {
            List<Raypath> raypathList;
            List<Phase> phaseList;
            Path outputDirectory;
            switch (mode) {
                case EPICENTRAL_DISTANCE:
                    raypathList = new ArrayList<>();
                    phaseList = new ArrayList<>();
                    RaypathCatalog catalog = RaypathCatalog
                            .computeCatalog(structure, ComputationalMesh.simple(structure), Math.toRadians(1));
                    double epicentralDistance = Math.toRadians(mostImportant);
                    for (Phase phase : phaseSet) {
                        Raypath[] raypaths = catalog.searchPath(phase, eventR, epicentralDistance, false);
                        for (Raypath raypath : raypaths) {
                            raypathList.add(raypath);
                            phaseList.add(phase);
                        }
                    }
                    for (int i = 0; i < phaseList.size(); i++) {
                        Phase phase = phaseList.get(i);
                        if (!phase.isDiffracted()) continue;
                        Raypath raypath = raypathList.get(i);
                        double delta = raypath.computeDelta(phase, eventR);
                        double dDelta = Math.toDegrees(epicentralDistance - delta);
                        phaseList.set(i, Phase.create(phase.toString() + dDelta));
                    }
                    break;
                case RAY_PARAMETER:
                    raypathList = new ArrayList<>();
                    phaseList = new ArrayList<>(phaseSet);
                    Raypath raypath = new Raypath(mostImportant, structure);
                    raypath.compute();
                    for (int i = 0; i < phaseList.size(); i++)
                        raypathList.add(raypath);
                    break;
                default:
                    throw new RuntimeException("unekuspekudte");
            }

            try {
                outputDirectory = askOutPath.get();
                if (outputDirectory == null) return;
                if (raypathList.size() != phaseList.size()) throw new RuntimeException("UNEXPECTED");
                for (int i = 0; i < raypathList.size(); i++) {
                    String name = phaseList.get(i).isPSV() ? phaseList.get(i) + "_PSV" : phaseList.get(i) + "_SH";
                    Path outEPSFile = outputDirectory.resolve(name + ".eps");
                    Path outInfoFile = outputDirectory.resolve(name + ".inf");
                    Path outDataFile = outputDirectory.resolve(name + ".dat");
                    raypathList.get(i).outputEPS(outEPSFile, phaseList.get(i), eventR);
                    raypathList.get(i).outputInfo(outInfoFile, phaseList.get(i), eventR);
                    raypathList.get(i).outputDat(outDataFile, phaseList.get(i), eventR);
                }
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities
                        .invokeLater(() -> JOptionPane.showMessageDialog(null, "Cannot write files about the path"));
            }
        };
        new Thread(output).start();
    }

    /**
     * when the button "Compute" is clicked.
     */
    private void buttonComputeActionPerformed(ActionEvent e) {
        createNewRaypathTabs();
        switch (mode) {
            case EPICENTRAL_DISTANCE:
                new Thread(this::runEpicentralDistanceMode).start();
                break;
            case RAY_PARAMETER:
                new Thread(this::runRayParameterMode).start();
                break;
        }
    }

    private void runRayParameterMode() {
        Raypath raypath = new Raypath(mostImportant, structure);
        raypath.compute();
        List<Raypath> raypaths = new ArrayList<>();
        List<Phase> phases = new ArrayList<>(phaseSet);

        for (int i = 0; i < phases.size(); i++)
            raypaths.add(raypath);

        showResult(null, raypaths, phases);
    }

    private RaypathCatalog getCatalog() {
        return RaypathCatalog
                .computeCatalog(structure, ComputationalMesh.simple(structure), RaypathCatalog.DEFAULT_MAXIMUM_D_DELTA);
    }

    private void runEpicentralDistanceMode() {
        RaypathCatalog catalog = getCatalog();
        List<Raypath> raypathList = new ArrayList<>();
        List<Phase> phaseList = new ArrayList<>();
        double epicentralDistance = Math.toRadians(mostImportant);
        for (Phase phase : phaseSet) {
            Raypath[] raypaths = catalog.searchPath(phase, eventR, epicentralDistance, false);
            for (Raypath raypath : raypaths) {
                if (!phase.isDiffracted()) {
                    raypathList.add(raypath);
                    phaseList.add(phase);
                    continue;
                }
                double deltaOnBoundary = Math.toDegrees(epicentralDistance - raypath.computeDelta(phase, eventR));
                if (deltaOnBoundary < 0) {
                    System.err.println(phase + " would have longer distance than " +
                            Math.toDegrees(raypath.computeDelta(phase, eventR)) + " (Your input:" +
                            Math.toDegrees(epicentralDistance) + ")");
                    continue;
                }
                raypathList.add(raypath);
                phaseList.add(Phase.create(phase.toString() + deltaOnBoundary));
            }
        }

        if (raypathList.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No raypaths found.");
        } else {
            double[] delta = new double[raypathList.size()];
            Arrays.fill(delta, epicentralDistance);
            showResult(delta, raypathList, phaseList);
        }
    }

    /**
     * This method shows results containing i th phase of i th raypath
     *
     * @param delta       [deg] Array of epicentral distance
     * @param raypathList List of {@link Raypath}
     * @param phaseList   List of {@link Phase}
     */
    private synchronized void showResult(double[] delta, List<Raypath> raypathList, List<Phase> phaseList) {
        Objects.requireNonNull(raypathList);
        Objects.requireNonNull(phaseList);
        if (raypathList.size() != phaseList.size()) throw new RuntimeException("UNEXPECTED");
        boolean added = false;
        for (int i = 0; i < phaseList.size(); i++) {
            Raypath raypath = raypathList.get(i);
            Phase phase = getCatalog()
                    .getActualTargetPhase(raypath, phaseList.get(i), eventR, delta[i], false); //TODO relative angle
            double epicentralDistance = Math.toDegrees(raypath.computeDelta(phase, eventR));
            double travelTime = raypath.computeT(phase, eventR);
            if (Double.isNaN(epicentralDistance)) continue;
            String title = phase.isPSV() ? phase.getDISPLAY_NAME() + " (P-SV)" : phase.getDISPLAY_NAME() + " (SH)";
            double depth = raypath.getStructure().earthRadius() - eventR;
            if (delta == null) {
                added = true;
                resultWindow.addRow(epicentralDistance, depth, title, travelTime, raypath.getRayParameter());
                showRayPath(raypath, phase);
            } else {
                double time = travelTime;
                if (!phase.isDiffracted())
                    time = getCatalog().travelTimeByThreePointInterpolate(phase, eventR, delta[i], false, raypath);
                if (!Double.isNaN(time)) {
                    added = true;
                    resultWindow.addRow(epicentralDistance, depth, title, time, raypath.getRayParameter());
                    showRayPath(raypath, phase);
                }
            }
        }
        try {
            if (added) SwingUtilities.invokeLater(() -> {
                raypathWindow.setVisible(true);
                resultWindow.setColor(0);
                raypathWindow.selectPath(0);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void showRayPath(Raypath raypath, Phase phase) {
        double[][] points = raypath.getRouteXY(phase, eventR);
        if (points != null) {
            double[] x = new double[points.length];
            double[] y = new double[points.length];
            Arrays.setAll(x, i -> points[i][0]);
            Arrays.setAll(y, i -> points[i][1]);
            try {
                SwingUtilities.invokeAndWait(() -> addPath(x, y));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
