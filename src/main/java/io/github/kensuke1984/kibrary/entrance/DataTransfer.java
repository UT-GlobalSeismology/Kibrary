package io.github.kensuke1984.kibrary.entrance;

import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.util.GadgetUtils;

/**
 * Downloads mseed files prepared after sending breqfast mails. Download is done through FTP access to IRIS server.
 * Output directory "seedsTransferredAt*" is created under the current path,
 * and eventDir/mseed created under it will include the downloaded mseed files.
 * TODO: OHP (Ocean Hemisphere network Project of ERI) will be prepared.
 *
 * @author Kensuke Konishi
 */
final class DataTransfer {

    /**
     * user PATH in IRIS
     */
    private static final String IRIS_USER_PATH = "/pub/userdata/" + Environment.getUserName() + "/";
    public static final String IRIS_FTP = "ftp.iris.washington.edu";

    private DataTransfer() {
    }

    /**
     * @param args [option] [tag]<br>
     *             If option -c, then check the number of files in the server,
     *             else FTP [date string] to get seed files(*.seed) in
     *             (/pub/userdata/`USERNAME`/) with the `tag`. If "*" (you might
     *             need "\*"), then get all seed files in the folder. <br>
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage:");
            System.err.println(" [-c] : check the number of files prepared at server.");
            System.err.println(" [tag] : download files that contain the specified string");
            System.err.println(" [*] (may need be \\*) : download all files at server");
            System.err.println(" You must specify one of these options.");
            return;
        }

        long startTime = System.nanoTime();
        System.err.println(DataTransfer.class.getName() + " is starting.");

        Path outPath = Paths.get("seedsTransferredAt" + GadgetUtils.getTemporaryString());
        get(args[0], outPath);

        System.err.println(DataTransfer.class.getName() + " finished in " +
                GadgetUtils.toTimeString(System.nanoTime() - startTime));

    }

    private static void get(String date, Path outPath) {

        // create an FTPClient
        FTPClient ftpclient = new FTPClient();

        try {
            // connect
            ftpclient.connect(IRIS_FTP);
            int reply = ftpclient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) throw new RuntimeException("connect fail");
            // login
            if (!ftpclient.login("anonymous", "password")) throw new RuntimeException("login fail");
            // passive mode
            ftpclient.enterLocalPassiveMode();
            // binary mode
            ftpclient.setFileType(FTP.BINARY_FILE_TYPE);
            // ftpclient.changeWorkingDirectory(userPath);

            // read existing files
            FTPFileFilter fff = file -> date.equals("*") || date.equals("-c") ? file.getName().endsWith("seed") :
                    file.getName().endsWith("seed") && file.getName().contains(date);
            FTPFile[] ffiles = ftpclient.listFiles(IRIS_USER_PATH, fff);
            System.err.println(ffiles.length + " seed files are found in the server.");
            for (FTPFile f : ffiles)
                System.err.println(f);
            if (date.equals("-c")) return;

            // wait
            System.err.println("Downloading in 10 s");
            Thread.sleep(10 * 1000);

            // download
            Files.createDirectories(outPath);
            System.err.println("Output folder is " + outPath);
            for (FTPFile ffile : ffiles) {

                // get event ID and create event directory
                String[] parts = ffile.getName().split("\\.");
                String eventID = parts[0];
                Path eventMseedPath = outPath.resolve(eventID).resolve("mseed");
                Files.createDirectories(eventMseedPath);

                // download file in event directory
                try (BufferedOutputStream ostream = new BufferedOutputStream(
                        Files.newOutputStream(eventMseedPath.resolve(ffile.getName()), StandardOpenOption.CREATE_NEW))) {
                    System.err.println("Receiving " + ffile.getName());
                    ftpclient.retrieveFile(IRIS_USER_PATH + "/" + ffile.getName(), ostream);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ftpclient.isConnected()) try {
                ftpclient.disconnect();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }


}
