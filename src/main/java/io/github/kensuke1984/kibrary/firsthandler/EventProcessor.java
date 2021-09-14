package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

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
    /**
     * [s] delta for SAC files. SAC files with different delta will be interpolated
     * or downsampled.
     */
    private static final double  DELTA = 0.05;

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
                SACFileName newFile = newSacName(sacPath);
                Path newSacPath = OUTPUT_PATH.resolve(newFile.toString());
                Files.copy(sacPath, newSacPath);

                StationInformationIRIS sii = new StationInformationIRIS(newFile);
                sii.readStationInformation(INPUT_DIR.toPath());
                fixHeader(newSacPath,sii);

//                fixDelta(sacPath);
            }
        }

    }

    /**
     * This method generates a SEED style SAC name from an MSEED style one.
     * MSEED style: "IU.MAJO.00.BH2.M.2014.202.144400.SAC"
     * SEED style: "2010.028.07.54.00.0481.IC.SSE.00.BHE.M.SAC"
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @throws IOException
     * @author kenji
     */
    private SACFileName newSacName(Path sacPath) throws IOException {
        String[] oldFile = sacPath.getFileName().toString().split("\\.");

        Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(sacPath);
        int i1 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZYEAR));
        int i2 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZJDAY));
        int i3 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZHOUR));
        int i4 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMIN));
        int i5 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZSEC));
        int i6 = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMSEC));

        String newName = i1 + "." + i2 + "." + i3 + "." + i4 + "." + i5 + "." + i6
                + "." + oldFile[0] + "." + oldFile[1] + "." + oldFile[2]
                + "." + oldFile[3] + "." + "M" + ".SAC";
        return new SACFileName(newName);
    }

    /**
     * Set SAC headers related to stations via StationInformation downloaded from IRIS/WS
     * @param sacPath (Path) Path of SAC files whose name will be fixed.
     * @param sii (StationInformationIRIS) provides station information
     * @throws IOException
     * @author kenji
     */
    private void fixHeader(Path sacPath, StationInformationIRIS sii) throws IOException {
        try (SAC sacD = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();
            sacD.inputCMD("cd " + cwd);// set current directory
            sacD.inputCMD("r " + sacPath.getFileName());// read
            sacD.inputCMD("ch lovrok true");// overwrite permission
            sacD.inputCMD("ch cmpaz " + sii.getAzimuth() + " cmpinc " + sii.getDip());
            sacD.inputCMD("ch stlo "  +sii.getLongitude() + " stla " +sii.getLatitude());
            sacD.inputCMD("interpolate delta " + DELTA);
            sacD.inputCMD("w over");
        }
    }

}