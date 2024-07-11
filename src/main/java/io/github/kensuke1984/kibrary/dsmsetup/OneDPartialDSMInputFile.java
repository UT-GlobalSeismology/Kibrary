package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;

/**
 * Information file for SSHPSV and SSHSH
 * <p>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 * @since version 0.0.6.1
 * @author anselme add system to comment out perturbations that are too close to the source and takes too long to compute
 * add PAR0 (partial for density)
 */
public class OneDPartialDSMInputFile extends SyntheticDSMInputFile {

    /**
     * perturbation radii
     */
    private final double[] radii;
    /**
     * Whether perturbation radii are close to epicentral radius.
     * The perturbation radii close to epicentral radius are commented out.
     */
    private boolean[] commentPerturbationR;

    private Set<HorizontalPosition> observerPositions;

    public OneDPartialDSMInputFile(PolynomialStructure structure, GlobalCMTAccess event, Set<Observer> observers, String outputDir,
                      double[] perturbationR, double tlen, int np) {
        super(structure, event, observers, outputDir, tlen, np);
        observerPositions = observers.stream().map(Observer::getPosition).collect(Collectors.toSet());

        radii = perturbationR.clone();
        commentPerturbationR = new boolean[perturbationR.length];
        double eventR = event.getCmtPosition().getR();
        for (int i = 0; i < perturbationR.length; i++) {
            commentPerturbationR[i] = false;
            if (Math.abs(eventR - perturbationR[i]) < 10.)
                commentPerturbationR[i] = true;
        }
    }

    /**
     * Creates a file for sshpsv(TI)
     *
     * @param psvPath write path
     * @param options options for writing
     * @throws IOException if any
     */
    public void writeTIPSV(Path psvPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(psvPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toPSVlines();
            Arrays.stream(structurePart).forEach(pw::println);

            // source
            FullPosition eventLocation = event.getCmtPosition();
            pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude()
                    + " r0(km), lat, lon (deg)");
            pw.println(Arrays.stream(event.getCmt().toDSMStyle()).mapToObj(Double::toString).collect(Collectors.joining(" "))
                    + " Moment Tensor (1.e25 dyne cm)");

            // output files
            pw.println("c directory of outputs");
            pw.println(output + "/");

            // receiver
            pw.println(observerPositions.size() + " nsta");
            observerPositions.stream().sorted().map(HorizontalPosition::toCode).forEach(obsPosCode -> {
                pw.println(obsPosCode + "." + event);
            });
            observerPositions.stream().sorted().forEach(pos -> pw.println(pos.getLatitude() + " " + pos.getLongitude()));

            // radii
            int nComment = (int) IntStream.range(0, commentPerturbationR.length)
                .mapToObj(i -> commentPerturbationR[i]).filter(c -> c).count();
            pw.println(radii.length - nComment + " nr");
            for (int i = 0; i < radii.length; i++) {
                if (commentPerturbationR[i])
                    pw.println("c " + radii[i]);
                else
                    pw.println(radii[i]);
            }
            pw.println("end");

        }
    }

    /**
     * Creates a file for sshpsvi(isotropic)
     *
     * @param psvPath write path
     * @param options options for writing
     * @throws IOException if any
     */
    public void writeISOPSV(Path psvPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(psvPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toPSVlines();
            Arrays.stream(structurePart).forEach(pw::println);

            FullPosition eventLocation = event.getCmtPosition();
            // source
            pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude()
                    + " r0(km), lat, lon (deg)");
            pw.println(Arrays.stream(event.getCmt().toDSMStyle()).mapToObj(Double::toString).collect(Collectors.joining(" "))
                    + " Moment Tensor (1.e25 dyne cm)");

            // output files
            pw.println("c directory of outputs");
            pw.println(output + "/");

            // receiver
            pw.println(observerPositions.size() + " nsta");
            observerPositions.stream().sorted().map(HorizontalPosition::toCode).forEach(obsPosCode -> {
                pw.println(obsPosCode + "." + event);
            });
            observerPositions.stream().sorted().forEach(pos -> pw.println(pos.getLatitude() + " " + pos.getLongitude()));

            // radii
            int nComment = (int) IntStream.range(0, commentPerturbationR.length)
                    .mapToObj(i -> commentPerturbationR[i]).filter(c -> c).count();
            pw.println(radii.length - nComment + " nr");
            for (int i = 0; i < radii.length; i++) {
                if (commentPerturbationR[i])
                    pw.println("c " + radii[i]);
                else
                    pw.println(radii[i]);
            }
            pw.println("end");

        }
    }

    /**
     * Creates a file for sshsh(TI)
     *
     * @param shPath  write path
     * @param options options for writing
     * @throws IOException if any
     */
    public void writeTISH(Path shPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(shPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toSHlines();
            Arrays.stream(structurePart).forEach(pw::println);

            FullPosition eventLocation = event.getCmtPosition();
            // source
            pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude()
                    + " r0(km), lat, lon (deg)");
            pw.println(Arrays.stream(event.getCmt().toDSMStyle()).mapToObj(Double::toString).collect(Collectors.joining(" "))
                    + " Moment Tensor (1.e25 dyne cm)");

            // output files
            pw.println("c directory of outputs");
            pw.println(output + "/");

            // receiver
            pw.println(observerPositions.size() + " nsta");
            observerPositions.stream().sorted().map(HorizontalPosition::toCode).forEach(obsPosCode -> {
                pw.println(obsPosCode + "." + event);
            });
            observerPositions.stream().sorted().forEach(pos -> pw.println(pos.getLatitude() + " " + pos.getLongitude()));

            // radii
            int nComment = (int) IntStream.range(0, commentPerturbationR.length)
                    .mapToObj(i -> commentPerturbationR[i]).filter(c -> c).count();
            pw.println(radii.length - nComment + " nr");
            for (int i = 0; i < radii.length; i++) {
                if (commentPerturbationR[i])
                    pw.println("c " + radii[i]);
                else
                    pw.println(radii[i]);
            }
            pw.println("end");
        }
    }

    /**
     * Creates a file for sshshi(isotropic)
     *
     * @param shPath  write path
     * @param options for writing
     * @throws IOException if any
     */
    public void writeISOSH(Path shPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(shPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toSHlines();
            Arrays.stream(structurePart).forEach(pw::println);

            // source
            FullPosition eventLocation = event.getCmtPosition();
            pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude()
                    + " r0(km), lat, lon (deg)");
            pw.println(Arrays.stream(event.getCmt().toDSMStyle()).mapToObj(Double::toString).collect(Collectors.joining(" "))
                    + " Moment Tensor (1.e25 dyne cm)");

            // output files
            pw.println("c directory of outputs");
            pw.println(output + "/");

            // receiver
            pw.println(observerPositions.size() + " nsta");
            observerPositions.stream().sorted().map(HorizontalPosition::toCode).forEach(obsPosCode -> {
                pw.println(obsPosCode + "." + event);
            });
            observerPositions.stream().sorted().forEach(pos -> pw.println(pos.getLatitude() + " " + pos.getLongitude()));

            // radii
            int nComment = (int) IntStream.range(0, commentPerturbationR.length)
                    .mapToObj(i -> commentPerturbationR[i]).filter(c -> c).count();
            pw.println(radii.length - nComment + " nr");
            for (int i = 0; i < radii.length; i++) {
                if (commentPerturbationR[i])
                    pw.println("c " + radii[i]);
                else
                    pw.println(radii[i]);
            }
            pw.println("end");
        }
    }
}
