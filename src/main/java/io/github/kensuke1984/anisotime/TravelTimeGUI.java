/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.kensuke1984.anisotime;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.WindowConstants;

/**
 * 
 * GUI for ANISOtime
 * 
 * @version 0.3.3.2
 * 
 * @author Kensuke Konishi
 */
class TravelTimeGUI extends javax.swing.JFrame {

	private static final long serialVersionUID = -4093263118460123169L;

	/**
	 * Creates new form TravelTimeGUI
	 */
	TravelTimeGUI() {
		initComponents();
	}

	private RaypathTabs raypathTabs;

	int getNumberOfRaypath() {
		return resultWindow.getN();
	}

	void selectRaypath(int i) {
		raypathTabs.selectTab(i);
	}

	void setRaypathVisible(boolean bool) {
		raypathTabs.setVisible(bool);
	}

	void addPath(double[] x, double[] y) {
		raypathTabs.addPath(x, y);
	}

	void createNewRaypathTabs() {
		if (raypathTabs != null)
			raypathTabs.dispose();
		VelocityStructure structure = jPanelParameter.getStructure();
		raypathTabs = new RaypathTabs(this, new RaypathPanel(structure.earthRadius(), structure.coreMantleBoundary(),
				structure.innerCoreBoundary()));
		resultWindow.clearRows();
	}

	VelocityStructure getStructure() {
		return jPanelParameter.getStructure();
	}

	double getEventR() {
		return jPanelParameter.getEventR();
	}

	double getMostImportant() {
		return jPanelParameter.getMostImportant();
	}

	/**
	 * @return 0(default): All, 1: P-SV, 2: SH
	 */
	int getPolarization() {
		return jMenuBar1.getPolarization();
	}

	void setMode(ComputationMode mode) {
		jPanelParameter.changeBorderTitle(jMenuBar1.getModeName() + "  " + jMenuBar1.getPoleString());
		jPanelParameter.setMode(mode);
		phaseWindow.setDiffractionMode(mode == ComputationMode.DIFFRACTION);

	}

	void changePropertiesVisible() {
		jPanelParameter.changePropertiesVisible();
	}

	void setPolarity(int i) {
		phaseWindow.setPolarity(i);
		jMenuBar1.setPolarity(i);
		jPanelParameter.changeBorderTitle(jMenuBar1.getModeName() + "  " + jMenuBar1.getPoleString());
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	private void initComponents() {
		setTitle("ANISOtime " + ANISOtime.version + " " + ANISOtime.codename);
		setLocationRelativeTo(null);
		phaseWindow = new PhaseWindow();
		resultWindow = new ResultWindow(this);

		jPanelParameter = new ParameterInputPanel();
		// jPanelTurningInformation = new TurningInformationPanel();
		// jPanelPhase = new PhasePanel();
		buttonCompute = new JButton("Compute");
		buttonShow = new JButton("Save");
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		// jMenuItem1 = new JMenuItem();
		jMenuBar1 = new TravelTimeMenuBar(this);
		setJMenuBar(jMenuBar1);
		// textFieldRayParameter.setText("0");

		buttonCompute.addActionListener(this::buttonComputeActionPerformed);

		buttonShow.addActionListener(this::buttonShowPerformed);

		GroupLayout layout = new GroupLayout(getContentPane());
		getContentPane().setLayout(layout);
		layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING).addGroup(layout.createSequentialGroup()
				.addContainerGap()
				.addGroup(layout.createParallelGroup(Alignment.CENTER).addGroup(layout.createSequentialGroup()
						.addGroup(layout.createParallelGroup(Alignment.LEADING).addGroup(layout.createSequentialGroup())
								.addComponent(jPanelParameter, GroupLayout.PREFERRED_SIZE, 300, Short.MAX_VALUE)))
						.addGroup(layout.createSequentialGroup().addGroup(
								layout.createSequentialGroup().addComponent(buttonCompute).addComponent(buttonShow)))
						.addComponent(resultWindow))
				.addContainerGap()));
		layout.setVerticalGroup(layout.createParallelGroup(Alignment.LEADING)
				.addGroup(layout.createSequentialGroup().addContainerGap()
						.addComponent(jPanelParameter, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
								GroupLayout.PREFERRED_SIZE)
						.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
								.addComponent(buttonCompute).addComponent(buttonShow))
				.addComponent(resultWindow, 100, 100, 100).addContainerGap()));

		pack();
		setPolarity(0);
		setLocation(getX() - getWidth() / 2, getY() - getHeight() / 2);
		phaseWindow.setLocation(getX() + getWidth(), getY());
		phaseWindow.setVisible(true);
		// raypathTabs = new RaypathTabs(this);
		// resultWindow.setLocation(getX()-resultWindow.getWidth(), getY());
	}// </editor-fold>//GEN-END:initComponents

	/**
	 * when the button "Compute" is clicked.
	 */
	void compute() {
	}

	/**
	 * when the button "Save" is clicked.
	 */
	void save() {
	}

	Phase[] getSelectedPhases() {
		return phaseWindow.getSelectedPhases();
	}

	private void buttonShowPerformed(java.awt.event.ActionEvent evt) {
		save();
	}

	private void buttonComputeActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_buttonComputeActionPerformed
		compute();
	}// GEN-LAST:event_buttonComputeActionPerformed

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String args[]) {
		try {
			for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					javax.swing.UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		} catch (ClassNotFoundException ex) {
			java.util.logging.Logger.getLogger(TravelTimeGUI.class.getName()).log(java.util.logging.Level.SEVERE, null,
					ex);
		} catch (InstantiationException ex) {
			java.util.logging.Logger.getLogger(TravelTimeGUI.class.getName()).log(java.util.logging.Level.SEVERE, null,
					ex);
		} catch (IllegalAccessException ex) {
			java.util.logging.Logger.getLogger(TravelTimeGUI.class.getName()).log(java.util.logging.Level.SEVERE, null,
					ex);
		} catch (javax.swing.UnsupportedLookAndFeelException ex) {
			java.util.logging.Logger.getLogger(TravelTimeGUI.class.getName()).log(java.util.logging.Level.SEVERE, null,
					ex);
		}
		// </editor-fold>

		/* Create and display the form */
		java.awt.EventQueue.invokeLater(() -> new TravelTimeGUI().setVisible(true));
	}

	void setResult(int i) {
		resultWindow.setColor(i);
	}

	ComputationMode selectedMode() {
		return jMenuBar1.selectedMode();
	}

	void addResult(double epicentralDistance, double depth, String phase, double travelTime, double rayparameter) {
		resultWindow.addRow(epicentralDistance, depth, phase, travelTime, rayparameter);
	}

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private JButton buttonCompute;
	private JButton buttonShow;
	private TravelTimeMenuBar jMenuBar1;
	private ParameterInputPanel jPanelParameter;
	private ResultWindow resultWindow;
	private PhaseWindow phaseWindow;
	// End of variables declaration//GEN-END:variables
}
