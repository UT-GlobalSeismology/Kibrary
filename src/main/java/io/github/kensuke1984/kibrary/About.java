package io.github.kensuke1984.kibrary;

import java.awt.GraphicsEnvironment;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.GroupLayout;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

/**
 * About Kibrary.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @see <a href=https://github.com/kensuke1984/Kibrary>GitHub</a>
 * @see <a href=https://kensuke1984.github.io/Kibrary>Javadoc</a>
 */
public final class About extends javax.swing.JFrame {
    public static final String VERSION = "0.5.0.0";
    private static final String LINE = "Kibrary " + VERSION + " \n" +
            "Main authors: Kensuke Konishi, Anselme F.E. Borgeaud, Keisuke Otsuru \n" +
            "Copyright \u00a9 2015-2023 \n" +
            "This software is licensed under the GNU General Public License Version 3, 29 June 2007 (https://www.gnu.org/licenses/).\n";

    /**
     * Shows information about this package.
     * @param args will be ignored
     */
    public static void main(String[] args) {
        run(null);
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        return null;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     */
    public static void run(CommandLine cmdLine) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            Logger.getLogger(About.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (!GraphicsEnvironment.isHeadless()) SwingUtilities.invokeLater(() -> new About().setVisible(true));
        else System.err.println(LINE);
    }

    private About() {
        super("About Kibrary");
        initComponents();
    }

    private void initComponents() {
        JTextArea jTextArea1 = new JTextArea(LINE, 5, 20);
        JScrollPane jScrollPane1 = new JScrollPane(jTextArea1);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jTextArea1.setLineWrap(true);
        jTextArea1.setWrapStyleWord(true);
        jTextArea1.setEditable(false);
        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(
                layout.createSequentialGroup().addContainerGap()
                        .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 376, Short.MAX_VALUE).addContainerGap()));
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(
                layout.createSequentialGroup().addContainerGap()
                        .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 276, Short.MAX_VALUE).addContainerGap()));
        pack();
        SwingUtilities.invokeLater(() -> jScrollPane1.getVerticalScrollBar().setValue(0));
    }

}
