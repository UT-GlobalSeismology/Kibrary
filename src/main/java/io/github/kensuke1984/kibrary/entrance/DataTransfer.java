package io.github.kensuke1984.kibrary.entrance;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;

import io.github.kensuke1984.kibrary.Environment;
import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * Downloads mseed files prepared after sending breqfast mails. Download is done through FTP access to IRIS server.
 * Output directory "seedsTransferredAt*" is created under the current path,
 * and eventDir/mseed created under it will include the downloaded mseed files.
 * TODO: OHP (Ocean Hemisphere network Project of ERI) will be prepared.
 *
 * @author Kensuke Konishi
 */
public final class DataTransfer {
    private DataTransfer() {}

    /**
     * user PATH in IRIS
     */
    private static final String IRIS_USER_PATH = "/pub/userdata/" + Environment.getUserName() + "/";
    public static final String IRIS_FTP = "ftp.iris.washington.edu";

    /**
     * @param args [option] [tag]<br>
     *             If option -c, then check the number of files in the server,
     *             else FTP [date string] to get seed files(*.seed) in
     *             (/pub/userdata/`USERNAME`/) with the `tag`.
     *             If "-a", then get all seed files in the folder. <br>
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        OptionGroup actionOption = new OptionGroup();
        actionOption.setRequired(true);
        actionOption.addOption(Option.builder("c").longOpt("checknum")
                .desc("Check the number of files prepared at server").build());
        actionOption.addOption(Option.builder("t").longOpt("tag").hasArg().argName("tag")
                .desc("Download files that contain the specified string").build());
        actionOption.addOption(Option.builder("a").longOpt("all")
                .desc("Download all files at server").build());
        options.addOptionGroup(actionOption);

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {

        String optionStr = null;
        if (cmdLine.hasOption("c")) {
            optionStr = "-c";
        } else if (cmdLine.hasOption("t")) {
            optionStr = cmdLine.getOptionValue("t");
        } else if (cmdLine.hasOption("a")) {
            optionStr = "-a";
        }
        get(optionStr);
    }

    private static void get(String date) {

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
            FTPFileFilter fff = file -> date.equals("-a") || date.equals("-c") ? file.getName().endsWith("seed") :
                    file.getName().endsWith("seed") && file.getName().contains(date);
            FTPFile[] ffiles = ftpclient.listFiles(IRIS_USER_PATH, fff);
            System.err.println(ffiles.length + " seed files are found in the server.");
            for (FTPFile f : ffiles)
                System.err.println(f);
            if (date.equals("-c")) return;

            // wait
            System.err.println("Downloading in 10 s");
            Thread.sleep(10 * 1000);

            // create output folder
            Path outPath = DatasetAid.createOutputFolder(Paths.get(""), "transferred", null, GadgetAid.getTemporaryString());

            // download
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
