package io.github.kensuke1984.anisotime;

import org.apache.commons.io.IOUtils;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Menubar for GUI
 *
 * @author Kensuke Konishi
 * @version 0.1.5.2
 */
final class MenuBar extends JMenuBar {

    /**
     * 2016/12/20
     */
    private static final long serialVersionUID = -1528882471062596159L;
    private final ANISOtimeGUI GUI;
    private ButtonGroup buttonGroupModes = new ButtonGroup();
    private ButtonGroup buttonGroupPolarization = new ButtonGroup();
    private JMenuItem jMenuItemExit;
    private JMenuItem jMenuItemRayparameterMode;
    private JMenuItem jMenuItemEpicentralDistanceMode;
    private JMenuItem jMenuItemParameterDescription;
    private JMenuItem jMenuItemAbout;
    private JMenuItem jMenuItemMail;
    private JMenuItem jMenuItemPreferences;
    private JMenuItem jMenuItemPSVSH;
    private JMenuItem jMenuItemPSV;
    private JMenuItem jMenuItemSH;

    MenuBar(ANISOtimeGUI gui) {
        GUI = gui;
        initComponents();
    }

    private void initComponents() {
        JMenu jMenuFile = new JMenu("File");
        JMenu jMenuHelp = new JMenu("Help");

        JMenu jMenuSettings = new JMenu("Settings");
        JMenu jMenuModes = new JMenu("Switch Mode");
        JMenu jMenuPolarization = new JMenu("Switch Polarization");
        jMenuItemExit = new JMenuItem("Exit");
        jMenuItemParameterDescription = new JMenuItem("Parameter description");
        jMenuItemRayparameterMode = new JRadioButtonMenuItem("Ray parameter Mode");
        jMenuItemEpicentralDistanceMode = new JRadioButtonMenuItem("Epicentral Distance Mode");
        jMenuItemPreferences = new JMenuItem("Preferences");

        jMenuItemAbout = new JMenuItem("About");
        jMenuItemMail = new JMenuItem("Feedback");

        jMenuItemPSVSH = new JRadioButtonMenuItem("All");
        jMenuItemPSV = new JRadioButtonMenuItem("P-SV");
        jMenuItemSH = new JRadioButtonMenuItem("SH");
        buttonGroupPolarization.add(jMenuItemPSVSH);
        buttonGroupPolarization.add(jMenuItemPSV);
        buttonGroupPolarization.add(jMenuItemSH);

        buttonGroupModes.add(jMenuItemEpicentralDistanceMode);
        buttonGroupModes.add(jMenuItemRayparameterMode);

        // first is normal mode
        jMenuItemEpicentralDistanceMode.setSelected(true);
        jMenuItemPSVSH.setSelected(true);
        jMenuSettings.add(jMenuModes);
        jMenuSettings.add(jMenuPolarization);
        jMenuSettings.add(jMenuItemPreferences);
        jMenuPolarization.add(jMenuItemPSVSH);
        jMenuPolarization.add(jMenuItemPSV);
        jMenuPolarization.add(jMenuItemSH);
        jMenuHelp.add(jMenuItemAbout);
        jMenuHelp.add(jMenuItemParameterDescription);
        jMenuHelp.add(jMenuItemMail);
        jMenuModes.add(jMenuItemEpicentralDistanceMode);
        jMenuModes.add(jMenuItemRayparameterMode);
        jMenuFile.add(jMenuItemExit);
        add(jMenuFile);
        add(jMenuSettings);
        add(jMenuHelp);
        setListeners();
    }

    private void setListeners() {
        jMenuItemPSVSH.addActionListener(e -> GUI.setPolarity(0));
        jMenuItemPSV.addActionListener(e -> GUI.setPolarity(1));
        jMenuItemSH.addActionListener(e -> GUI.setPolarity(2));

        jMenuItemRayparameterMode.addActionListener(e -> GUI.setMode(ComputationMode.RAY_PARAMETER));
        jMenuItemEpicentralDistanceMode.addActionListener(e -> GUI.setMode(ComputationMode.EPICENTRAL_DISTANCE));
        jMenuItemPreferences.addActionListener(e -> GUI.changePropertiesVisible());

        jMenuItemExit.addActionListener(evt -> System.exit(0));

        jMenuItemParameterDescription.addActionListener(e -> {
            URL descriptionPdf = getClass().getClassLoader().getResource("description.pdf");
            Path pdfFile;
            try {
                pdfFile = Files.createTempFile("ANISOtime_description", ".pdf");
            } catch (Exception e2) {
                pdfFile = Paths.get(System.getProperty("user.dir"), "ANISOtime_description.pdf");
            }
            try (BufferedOutputStream pdfOutStream = new BufferedOutputStream(Files.newOutputStream(pdfFile));
                 InputStream pdfStream = descriptionPdf.openStream()) {
                IOUtils.copy(pdfStream, pdfOutStream);
            } catch (Exception e2) {
                e2.printStackTrace();
                return;
            }
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().open(pdfFile.toFile());
                    pdfFile.toFile().deleteOnExit();
                    return;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // JOptionPane.showMessageDialog(null,
                    // "미안 해요. PDF를 열 수 없습니다");
                }
            }
            JOptionPane
                    .showMessageDialog(null, "Can't open a pdf file. Look at " + pdfFile + " and delete by yourself.");
        });
        jMenuItemAbout.addActionListener(e -> About.main(null));
        jMenuItemMail.addActionListener(e -> {
            try {
                Desktop.getDesktop().mail(new URI("mailto:kensuke@earth.sinica.edu.tw"));
            } catch (Exception e2) {
                JOptionPane.showMessageDialog(null,
                        "<html>Can't launch a mailer. Please send Email to <a href>kensuke@earth.sinica.edu.tw</a>.");
            }
        });

    }
}
