package io.github.kensuke1984.kibrary.visual;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Creates partial waveform temporaly snapshot data and sensitivity kernel tempolary snapshot data.
 * For each event and observer, snapshot data file will be created.
 * For each component and partial type which you select, snapshot data file will be created.
 *<p>
 * Timewindow and use phases are refered to {@link PartialID}.
 * <p>
 * The format of output files is [ parturbation point (lat, long, depth), values of data at each time ].
 * <p>
 * each event directories will be created under "KernelTemporalVisual" directories.
 * each observer directories will be created under the event directories.
 * Output files will be created under each of the existing event directories.
 *
 * @author rei
 * @since 2022/01/05
 */
public class SensitivityKernelPlotCreater {

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("USAGE:");
            System.err.println("partialID path, partial path, phase, component, partial types");
            System.err.println("If you want to select several components, split them by comma(,) like 'Z,T,R'");
            System.err.println("If you want to select several partial types, split them by comma(,) like 'MU,LAMBDA'");
        }


        Path partialIDPath = Paths.get(args[0]);
        Path partialPath = Paths.get(args[1]);
        PartialID[] partials = PartialIDFile.read(partialIDPath, partialPath);

        Set<SACComponent> components = Arrays.stream(args[2].split(",")).map(SACComponent::valueOf).collect(Collectors.toSet());
        Set<PartialType> partialTypes = Arrays.stream(args[3].split(",")).map(PartialType::valueOf).collect(Collectors.toSet());

        Path dir0 = Paths.get("KernelTemporalVisual");
        Files.createDirectories(dir0);
        for (PartialID partial : partials) {

                for (SACComponent component : components) {
                    if (component != partial.getSacComponent())
                        continue;

                    for (PartialType partialType : partialTypes) {
                        if (partialType != partial.getPartialType())
                            continue;

                        Path dir1 = dir0.resolve(partial.getGlobalCMTID().toString());
                        if (!Files.exists(dir1))
                            Files.createDirectories(dir1);
                        Path dir2 = dir1.resolve(partial.getObserver().toString());
                        if (!Files.exists(dir2))
                            Files.createDirectories(dir2);

                        double[] data = partial.getData();
                        double t0 = partial.getStartTime();

                        String phaselist = "";

                        for (Phase phase : partial.getPhases()) {
                            phaselist = phaselist + phase;
                        }

                        Path filePath = dir2.resolve("partialTemporal_snapshots_"
                                + phaselist + "_" + component+ "_" + partialType
                                + String.format("_t0%d.txt", (int) t0));

                        Path filePath_sensitivity = dir2.resolve("sensitivityKernelTemporal_snapshots_"
                                + phaselist + "_" + component + "_" + partialType
                                + String.format("_t0%d.txt", (int) t0));


                        if (!Files.exists(filePath))
                            Files.createFile(filePath);
                        if (!Files.exists(filePath_sensitivity))
                            Files.createFile(filePath_sensitivity);

                        double cumulativeSensitivity = 0.;

                        PrintWriter writer2 = new PrintWriter(new FileWriter(filePath.toString(), true));
                        double lat = partial.getVoxelPosition().getLatitude();
                        double lon = partial.getVoxelPosition().getLongitude();
                        if (lon < 0)
                            lon += 360.;
                        double r = partial.getVoxelPosition().getR();
                        writer2.write(String.format("%.3f %.3f %.1f ", lat, lon, r));
                        BufferedWriter writer2_s = Files.newBufferedWriter(filePath_sensitivity, StandardOpenOption.APPEND);
                        writer2_s.write(String.format("%.3f %.3f %.1f ", lat, lon, r));

                        for (int i = 0; i < data.length; i++) {
                            writer2.write(String.format("%.5e ", data[i]));

                            cumulativeSensitivity += data[i] * data[i];
                            writer2_s.write(String.format("%.5e ", cumulativeSensitivity));
                        }

//                        PrintWriter writer2 = new PrintWriter(new FileWriter("partial.lst", true));
//
//                        if (lat < 8.76 && lat > 8.74 && lon < -91.24 && lon > -91.26 && r > 3500. && r < 3510.) {
//                            for (int i = 0; i < data.length; i++) {
//                                double time = partial.getStartTime() + i;
//                                writer2.println(time + " " +data[i]);
//                            }
//                        }

                        writer2.println();
                        writer2.close();
                        writer2_s.newLine();
                        writer2_s.close();

                    }
                }
            }
        }
}
