package io.github.kensuke1984.kibrary.firsthandler;

import io.github.kensuke1984.kibrary.external.ExternalProcess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTSearch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seed file utility mseed2sac must be in PATH.
 *
 * @author Kenji Kawai
 * @version 0.1.1
 * @see <a href=http://ds.iris.edu/ds/nodes/dmc/forms/rdseed/>download</a>
 * @see <a href=https://ds.iris.edu/ds/nodes/dmc/manuals/rdseed/>manual</a>
 */
public class MSEEDFile {

    /**
     * date format made by rdseed e.g. 1993,052,07:01:12.4000
     */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d\\d\\d\\d),(\\d\\d\\d),(\\d\\d):(\\d\\d):(\\d\\d).(\\d\\d\\d\\d)");
    private static final DateTimeFormatter HEADER_FORMAT = DateTimeFormatter.ofPattern("yyyy,DDD,HH:mm:ss.SSSS");

    static {
        if (!ExternalProcess.isInPath("mseed2sac")) throw new RuntimeException("No mseed2sac in PATH.");
    }

    /**
     * path of a SEED file
     */
    private final Path ORIGINAL_PATH;
    /**
     * B010F05 Starting date of this volume:
     */
    private LocalDateTime startingDate;
    /**
     * B010F06 Ending date of this volume:
     */
    private LocalDateTime endingDate;
    /**
     * B010F07 Creation date of this volume:
     */
    private LocalDateTime creationDate;
    /**
     * B010F08 Originating Organization:
     */
    private String originatingOrganization;
    /**
     * B010F09 Volume Label:
     */
    private String volumeLabel;

    /**
     * temporary symbolic link for shortening path
     */
    private final Path TEMP_LINK;


    public MSEEDFile(Path mseedPath) throws IOException {
        ORIGINAL_PATH = mseedPath;
        volumeLabel = mseedPath.toString().split(Pattern.quote("."))[0];
        
        TEMP_LINK = Files.createSymbolicLink(Files.createTempDirectory("seed").resolve(mseedPath.getFileName()),
                mseedPath.toAbsolutePath());
        // searchRdseed();
        // readVolumeHeader();
        // mseed2sac(""); //ここで呼び出すと良くない
    }

    /**
     * Displays Global CMT IDs which might be contained in the mseedfile
     *
     * @param args [seed file name]
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
//        if (args.length != 1) throw new IllegalArgumentException("Usage: [mseed file name]");
//        MSEEDFile mseed = new MSEEDFile(Paths.get(args[0]));
//        GlobalCMTSearch sc = new GlobalCMTSearch(mseed.startingDate, mseed.endingDate);
//        sc.search().forEach(System.out::println);
    	System.out.println("hello");
    }

    /**
     * @param dateString YYYY,DDD,HH:MM:
     * @return time for the dateString
     */
    private static LocalDateTime toLocalDateTime(String dateString) {
        return LocalDateTime.parse(dateString, HEADER_FORMAT);
    }

    @Override
    public String toString() {
        return ORIGINAL_PATH.toString();
    }

    /**
     * @return Path of the seed file.
     */
    public Path getSeedPath() {
        return ORIGINAL_PATH;
    }

    public LocalDateTime getStartingDate() {
        return startingDate;
    }

    public LocalDateTime getEndingDate() {
        return endingDate;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public String getOriginatingOrganization() {
        return originatingOrganization;
    }

    public String getVolumeLabel() {
        return volumeLabel;
    }

    /**
     * mseed2sac を行ったときの出力を返す
     * @param outputPath
     * @param mseedfile
     * @return 
     * @throws IOException
     * @author kenji
     */
    private boolean mseed2sac(Path outputPath, Path mseedfile) throws IOException {
      
        String command = "mseed2sac " + mseedfile;
        
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s")); //  runevalresp in MseedSAC.javaを参考にした

        pb.directory(new File(outputPath.toString()).getAbsoluteFile());	
//        System.out.println("working directory is: " + pb.directory()); //4debug
        try {
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    
    }

    /**
     * Run mseed2sac $mseedFile<br>
     *
     * @param outputPath for miniseed
     * @throws IOException if any
     */
    void extract(Path outputPath) throws IOException {
        if (Files.exists(outputPath)) Files.createDirectories(outputPath);
        System.err.println("Extracting mseed File: " + ORIGINAL_PATH + " in " + outputPath);
        mseed2sac(outputPath, ORIGINAL_PATH);
    }

    /**
     * Creates a symbolic link (absolute path) to the seed file in the directory.
     *
     * @param directory if it does not exist, it will be created
     * @throws IOException if an I/O error occurs
     */
    void createLink(Path directory) throws IOException {
        Files.createSymbolicLink(directory.resolve(ORIGINAL_PATH.getFileName()), ORIGINAL_PATH.toAbsolutePath());
//        Files.move(seedPath, directory.resolve(seedPath.getFileName()));
//        System.out.println(directory.resolve(ORIGINAL_PATH.getFileName()) +" "+ ORIGINAL_PATH.toAbsolutePath());
//        Files.move(ORIGINAL_PATH.toAbsolutePath(), directory.resolve(ORIGINAL_PATH.getFileName()) ); // 4debug
    }

}
