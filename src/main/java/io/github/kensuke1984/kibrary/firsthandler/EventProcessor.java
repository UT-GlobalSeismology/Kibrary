package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.EventFolder;

/**
 * Class for extracting a mseed file. It creates SAC files from the mseed file.
 * <p>
 * This class requires that mseed2sac, evalresp and sac exists in your PATH.
 * The software
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/mseed2sac/>mseed2sac</a>,
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/evalresp/>evalresp</a> and
 * <a href=https://ds.iris.edu/ds/nodes/dmc/software/downloads/sac/>SAC</a> can be found in IRIS.
 *
 * @author Kenji Kawai
 * @version 0.1.1
 */
class EventProcessor implements Runnable {

    private final EventFolder INPUT_DIR;
    private final Path OUTPUT_PATH;

    EventProcessor(EventFolder eventDir, Path outPath) throws IOException {
        INPUT_DIR = eventDir;
        OUTPUT_PATH = outPath.resolve(eventDir.getName());

    }

    @Override
    public void run() {

        try {
            Files.createDirectories(OUTPUT_PATH);
            preprocess();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on pre-processing " + INPUT_DIR.getName(), e);
        }
    }

    private void preprocess() throws IOException {
        try (DirectoryStream<Path> sacPaths = Files.newDirectoryStream(INPUT_DIR.toPath(), "*.SAC")) {
            for (Path sacPath : sacPaths) {
                Files.copy(sacPath, OUTPUT_PATH.resolve(sacPath.getFileName()));
//                fixDelta(sacPath);
            }
        }

    }

    /**
     * Set SAC headers related to stations via StationInformation downloaded from IRIS/WS
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @param sii (StationInformationIRIS) provides station information
     * @throws IOException
     * @author kenji
     */
/*    private void fixHeader(Path sacPath, StationInformationIRIS sii) throws IOException {
        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();
            sacD.inputCMD("cd " + cwd);// set current directory
            sacD.inputCMD("r " + sacPath.getFileName());// read
            sacD.inputCMD("ch lovrok true");// overwrite permission
            sacD.inputCMD("ch cmpaz " + sii.getAzimuth() + " cmpinc " + sii.getDip());
            sacD.inputCMD("ch stlo "  +sii.getLongitude() + " stla " +sii.getLatitude());
            sacD.inputCMD("interpolate delta " + delta);
            sacD.inputCMD("w over");
        }
    }
*/
}