package io.github.kensuke1984.kibrary.util;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;

import io.github.kensuke1984.kibrary.Environment;

/**
 * Utilities for handling gadgets such as mail, clipboard, or Strings.
 *
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class GadgetAid {
    private GadgetAid() {}

    /**
     * @param path      Path of the target
     * @param algorithm MD5, SHA-256, ...
     * @return byte string
     * @throws IOException              if any
     * @throws NoSuchAlgorithmException if any
     */
    public static String checksum(Path path, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        try (DigestInputStream digestInputStream = new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(path)), md)) {
            while (digestInputStream.read() != -1) ;
        }
        StringBuilder result = new StringBuilder();
        for (byte b : md.digest())
            result.append(String.format("%02x", b));
        return result.toString();
    }

    /**
     * @param subject       of the mail
     * @param to            address of the mail
     * @param lines         mail
     * @param authenticator for Gmail
     * @throws Exception if any
     */
    public static void sendGmail(String subject, String to, String[] lines, DefaultAuthenticator authenticator)
            throws Exception {
        Email email = new SimpleEmail();
        email.setHostName("smtp.googlemail.com");
        email.setSmtpPort(465);
        email.setAuthenticator(authenticator);
        email.setSSLOnConnect(true);
        email.setFrom(Environment.getEmail());
        email.setSubject(subject);
        email.setMsg(String.join("\n", lines));
        email.addTo(to);
        email.send();
    }

    public static void sendMail(String address, String title, String... bodies) throws URISyntaxException, IOException {
        String body = String.join("%0A", bodies);
        URI uri = new URI("mailto:" + address + "?subject=" + title.replace(" ", "%20") + "&body=" +
                body.replace(" ", "%20").replace("\n", "%0A"));
        Desktop.getDesktop().mail(uri);
    }

    /**
     * Input dialog or input prompt shows up.
     * Your input is hidden.
     *
     * @param phrase key for the password
     * @return password, secret phrase, ...
     */
    public static String getPassword(String phrase) throws InterruptedException {
        return GraphicsEnvironment.isHeadless() ?
                String.copyValueOf(System.console().readPassword("Password for " + phrase)) :
                PasswordInput.getPassword(phrase);
    }

    /**
     * @return String in the clipboard
     * @throws UnsupportedFlavorException if the clipboard has any that can not be string.
     * @throws IOException                if an I/O error occurs.
     */
        public static String getClipBoard() throws UnsupportedFlavorException, IOException {
            return Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null)
                    .getTransferData(DataFlavor.stringFlavor).toString();
        }

    /**
     * Reads input from standard input.
     * @return (String) Value read from standard input (System.in)
     * @throws IOException if any
     */
    public static String readInputLine() throws IOException {
        String s;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(CloseShieldInputStream.wrap(System.in)))) {
            s = br.readLine().trim();
        }
        return s;
    }

    /**
     * Reads input from user. Attempt for input dialog is made first; when it fails, standard input is used.
     * @param message (String) Message for user
     * @param initialValue (String) Initial value to show in input field
     * @return (String) Input from user, trimmed
     * @throws IOException
     *
     * @author otsuru
     * @since 2022/4/8
     */
    public static String readInputDialogOrLine(String message, String initialValue) throws IOException {
        String string;
        try {
            string = JOptionPane.showInputDialog(message, initialValue);
        } catch (Exception e) {
            System.err.print(message + " : ");
            string = GadgetAid.readInputLine();
        }
        return string;
    }

    /**
     * Reads input from user. Attempt for input dialog is made first; when it fails, standard input is used.
     * @param message (String) Message for user
     * @return (String) Input from user, trimmed
     * @throws IOException
     *
     * @author otsuru
     * @since 2022/4/8
     */
    public static String readInputDialogOrLine(String message) throws IOException {
        return readInputDialogOrLine(message, "");
    }

    /**
     * print all input objects using {@link java.io.PrintStream#println()}
     *
     * @param objs to be printed
     */
    public static void println(Object... objs) {
        System.out.println(Arrays.stream(objs).map(Object::toString).collect(Collectors.joining(" ")));
    }

    /**
     * Print a message into both a file and the standard error.
     * @param pw (PrintWriter)
     * @param output (String) message to print
     *
     * @author otsuru
     * @since 2021/12/27
     */
    public static void dualPrintln(PrintWriter pw, String output) {
        pw.println(output);
        System.err.println(output);
    }

    /**
     * e.g. num=2, values={1,2,3,4} &rarr; {1,1}, {1,2}, {1,3}, ...{4,3},{4,4}
     *
     * @param num    number of elements for each pattern
     * @param values elements are chosen from these values
     * @return Array of all possible patterns of num-elements from the values.
     * Each element in the array has the array of num elements. patterns[i][j]
     * is j-th degit in the i-th pattern
     */
    public static double[][] makePatterns(int num, double[] values) {
        int patternN = (int) Math.pow(values.length, num);
        double[][] patterns = new double[patternN][num];
        int i = 0;
        do {
            for (int j = 0; j < num; j++)
                patterns[i][j] = values[(i / (int) Math.pow(values.length, j)) % values.length];
        } while (++i < patternN);
        return patterns;
    }

    /**
     * Change the input to an intelligible expression.
     *
     * @param nanoSeconds [ns] time
     * @return ?d, ?h, ?min and ?s
     */
    public static String toTimeString(long nanoSeconds) {
        long used = 0;
        long days = TimeUnit.NANOSECONDS.toDays(nanoSeconds);
        used += TimeUnit.DAYS.toNanos(days);
        long hours = TimeUnit.NANOSECONDS.toHours(nanoSeconds - used);
        used += TimeUnit.HOURS.toNanos(hours);
        long mins = TimeUnit.NANOSECONDS.toMinutes(nanoSeconds - used);
        used += TimeUnit.MINUTES.toNanos(mins);
        double sec = (nanoSeconds - used) / 1000000000.0;
        return (days == 0 ? "" : days + "d, ") + (hours == 0 ? "" : hours + "h, ")
                + (mins == 0 ? "" : mins + " min and ") + sec + " s";
    }

    /**
     * Create a string for temporary files or w/e
     *
     * @return yyyyMMddHHmmss
     */
    public synchronized static String getTemporaryString() {
        try {
            Thread.sleep(1000);
            return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        } catch (InterruptedException ie) {
            return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        }
    }


}
