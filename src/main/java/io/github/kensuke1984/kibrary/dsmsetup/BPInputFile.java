package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;

import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;


/**
 * Information file for computation of back propagation.
 * The source is the observer, and the receivers are the perturbation points.
 * <p>
 * This class is <b>immutable</b>
 *
 * @author Kensuke Konishi
 * @since version 0.0.6
 */
public class BPInputFile extends DSMInputHeader {

    private final String output;

    /**
     * Radii of the perturbation points
     */
    private final double[] receiverRadii;
    /**
     * Position of the perturbation points
     */
    private final HorizontalPosition[] receiverPositions;
    // private double sourceR;
    /**
     * Position of the observer
     */
    private final HorizontalPosition sourcePosition;
    private final PolynomialStructure structure;

    /**
     * @param observer              Information of observer
     * @param outputDir            the name of the write folder
     * @param structure            velocity structure
     * @param tlen                 must be a power of 2 / 10 (2<sup>n</sup>)/10
     * @param np                   must be a power of 2 (2<sup>n</sup>)
     * @param perturbationRadii   will be copied
     * @param perturbationPositions will be copied
     */
    public BPInputFile(HorizontalPosition observerPositon, String outputDir, PolynomialStructure structure, double tlen, int np,
                  double[] perturbationRadii, HorizontalPosition[] perturbationPositions) {
        super(tlen, np);
        this.sourcePosition = observerPositon;
        this.output = outputDir;
        this.structure = structure;
        this.receiverRadii = perturbationRadii.clone();
        this.receiverPositions = perturbationPositions.clone();
    }

    public BPInputFile(String outputDir, PolynomialStructure structure, double tlen, int np,
            double[] perturbationRadii, HorizontalPosition[] perturbationPositions) {
        super(tlen, np);
        this.sourcePosition = null;
        this.output = outputDir;
        this.structure = structure;
        this.receiverRadii = perturbationRadii.clone();
        this.receiverPositions = perturbationPositions.clone();
    }

    /**
     * @return name of the write folder
     */
    public String getOutputDir() {
        return output;
    }

    /**
     * @return radii for the perturbation points
     */
    public double[] getPerturbationPointDepth() {
        return receiverRadii.clone();
    }

    /**
     * @return structure to be used
     */
    public PolynomialStructure getStructure() {
        return structure;
    }

    /**
     * Write an information file for psvbp
     *
     * @param outPath Path for the file
     * @param options for opening the file
     * @throws IOException If an I/O error happens
     */
    public void writePSVBP(Path outPath, OpenOption... options) throws IOException {

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            Arrays.stream(structure.toPSVlines()).forEach(pw::println);

            // source
            pw.println("0 " + // BPINFOには震源深さいらない
                    sourcePosition.getLatitude() + " " + sourcePosition.getLongitude());

            // write info
            pw.println("c write directory");
            pw.println(output + "/");
            pw.println(sourcePosition.toCode());
            pw.println("c events and stations");

            // horizontal positions for perturbation points
            pw.println(receiverPositions.length + " nsta");
            Arrays.stream(receiverPositions).forEach(pp -> pw.println(pp.getLatitude() + " " + pp.getLongitude()));

            // radii for perturbation points
            pw.println(receiverRadii.length + " nr");
            Arrays.stream(receiverRadii).forEach(pw::println);
            pw.println("end");
        }
    }

    /**
     * write the information file for psvbpcat (psvbp catalog)
     * @param outPath
     * @param thetamin
     * @param thetamax
     * @param dtheta
     * @param options
     * @throws IOException
     * @author anselme
     */
    public void writePSVBPCat(Path outPath, double thetamin, double thetamax, double dtheta, OpenOption... options)
            throws IOException {

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            Arrays.stream(structure.toPSVlines()).forEach(pw::println);

            // source
            pw.println("0. 0. 0.");

            // output info
            pw.println("c output directory");
            pw.println(output + "/");
            pw.println("340A_TA");
            pw.println("c events and stations");

            // catalogue epicentral distance sampling
            pw.println(thetamin + " " + thetamax + " " + dtheta);

            // radii for perturbation points
            pw.println(receiverRadii.length + " nr");
            Arrays.stream(receiverRadii).forEach(pw::println);
            pw.println("end");
        }
    }

    /**
     * Write an information file for shbp
     *
     * @param outPath Path for the file
     * @param options for opening the file
     * @throws IOException if an I/O error happens
     */
    public void writeSHBP(Path outPath, OpenOption... options) throws IOException {

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            Arrays.stream(structure.toSHlines()).forEach(pw::println);

            // source
            pw.println("0 " + // BPINFOには震源深さいらない
                    sourcePosition.getLatitude() + " " + sourcePosition.getLongitude());

            // write info
            pw.println("c write directory");
            pw.println(output + "/");
            pw.println(sourcePosition.toCode());
            pw.println("c events and stations");

            // horizontal positions for perturbation points
            pw.println(receiverPositions.length + " nsta");
            Arrays.stream(receiverPositions).forEach(pp -> pw.println(pp.getLatitude() + " " + pp.getLongitude()));

            // radii for perturbation points
            pw.println(receiverRadii.length + " nr");
            Arrays.stream(receiverRadii).forEach(pw::println);
            pw.println("end");
        }
    }

    /**
     * write the information file for shbpcat (shbp catalog)
     * @param outPath
     * @param thetamin
     * @param thetamax
     * @param dtheta
     * @param options
     * @throws IOException
     * @author anselme
     */
    public void writeSHBPCat(Path outPath, double thetamin, double thetamax, double dtheta, OpenOption... options) throws IOException {

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            Arrays.stream(structure.toSHlines()).forEach(pw::println);

            // source
            pw.println("0. 0. 0.");

            // output info
            pw.println("c output directory");
            pw.println(output + "/");
            pw.println("340A_TA");
            pw.println("c events and stations");

            // catalogue epicentral distance sampling
            pw.println(thetamin + " " + thetamax + " " + dtheta);

            // radii for perturbation points
            pw.println(receiverRadii.length + " nr");
            Arrays.stream(receiverRadii).forEach(pw::println);
            pw.println("end");
        }
    }

}
