package io.github.kensuke1984.kibrary.external.specfem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.MathAid;

/**
 * Creates input files for SPECFEM.
 *
 * @author otsuru
 * @since 2022/3/14
 */
public class SPECFEMSetup {

    private Path workPath;
    private Path outPath;

    public static void main(String[] args) {
        // TODO 自動生成されたメソッド・スタブ

    }

    public SPECFEMSetup() {

    }

    private void createRunDirectory(int runNum) throws IOException {
        Path runPath = outPath.resolve("run" + MathAid.padToString(runNum, 4, "0"));
        Path dataPath = runPath.resolve("DATA");

        Files.createDirectories(dataPath);
        Files.createDirectories(runPath.resolve("DATABASES_MPI"));
        Files.createDirectories(runPath.resolve("OUTPUT_FILES"));

        generateCmtSolutionFile(dataPath);
        generateStationFile(dataPath);


    }

    private void generateCmtSolutionFile(Path dataPath) {
        Path cmtSolutionPath = dataPath.resolve("CMTSOLUTION");

    }
    private void generateStationFile(Path dataPath) {
        Path stationPath = dataPath.resolve("STATION");

    }

}
