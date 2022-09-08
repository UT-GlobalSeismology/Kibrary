package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;

/**
 * Information file for computation of forward propagation.
 * The source is the event, and the receivers are the perturbation points.
 * <p>
 * This class is <b>immutable</b>
 *
 * @author Kensuke Konishi
 * @version 0.0.6.2
 * @author anselme add information files for psv/sh fpcat (catalog)
 */
public class FPInputFile extends DSMInputHeader {

    private final GlobalCMTAccess event;
    private final HorizontalPosition[] positions;
    private final double[] radii;
    private final PolynomialStructure structure;
    private final String output;

    /**
     * @param event              source
     * @param outputDir          write folder
     * @param structure          structure
     * @param tlen               [s] must be a power of 2 (2<sup>n</sup>)/10
     * @param np                 must be a power of 2 (2<sup>n</sup>)
     * @param perturbationRadii will be copied
     * @param perturbationPositions  will be copied
     */
    public FPInputFile(GlobalCMTAccess event, String outputDir, PolynomialStructure structure, double tlen, int np,
                  double[] perturbationRadii, HorizontalPosition[] perturbationPositions) {
        super(tlen, np);
        this.event = event;
        output = outputDir;
        this.structure = structure;
        positions = perturbationPositions.clone();
        radii = perturbationRadii.clone();
    }

    /**
     * Write an information file for shfp.
     *
     * @param outPath Path for the file
     * @param options for opening the file
     * @throws IOException If an I/O error happens
     */
    public void writeSHFP(Path outPath, OpenOption... options) throws IOException {
        // if(true)return;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toSHlines();
            Arrays.stream(structurePart).forEach(pw::println);

            // source
            pw.println(event.getCmtPosition().getR() + " " + event.getCmtPosition().getLatitude() + " " +
                    event.getCmtPosition().getLongitude());
            double[] mt = event.getCmt().getDSMmt();
            pw.println(Arrays.stream(mt).mapToObj(Double::toString).collect(Collectors.joining(" ")) +
                    " Moment Tensor (1.e25 dyne cm)");

            // write info
            pw.println("c write directory");
            pw.println(output + "/");
            pw.println(event);
            pw.println("c events and stations");

            // horizontal positions for perturbation points
            pw.println(positions.length + " nsta");
            Arrays.stream(positions).forEach(pp -> pw.println(pp.getLatitude() + " " + pp.getLongitude()));

            // radii for perturbation points
            pw.println(radii.length + " nr");
            Arrays.stream(radii).forEach(pw::println);
            pw.println("end");
        }
    }

    /**
     * write the information file for shfpcat (catalog)
     * @param outPath
     * @param thetamin
     * @param thetamax
     * @param dtheta
     * @param options
     * @throws IOException
     * @author anselme
     */
    public void writeSHFPCAT(Path outPath, double thetamin, double thetamax, double dtheta, OpenOption... options) throws IOException {
        // if(true)return;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toSHlines();
            Arrays.stream(structurePart).forEach(pw::println);

            // source
            pw.println(event.getCmtPosition().getR() + " 0. 0.");
            double[] mt = event.getCmt().getDSMmt();
            pw.println(Arrays.stream(mt).mapToObj(Double::toString).collect(Collectors.joining(" "))
                    + " Moment Tensor (1.e25 dyne cm)");

            // output info
            pw.println("c output directory");
            pw.println(output + "/");
            pw.println(event.toString());
            pw.println("c events and stations");

            // catalogue epicentral distance sampling
            pw.println(thetamin + " " + thetamax + " " + dtheta);

            // radii for perturbation points
//			int nComment = (int) IntStream.range(0, commentPerturbationR.length)
//					.mapToObj(i -> commentPerturbationR[i]).filter(c -> c).count();
//			pw.println(RADII.length - nComment + " nr");
            pw.println(radii.length + " nr");
            for (int i = 0; i < radii.length; i++) {
//				if (commentPerturbationR[i])
//					pw.println("c " + RADII[i]);
//				else
                pw.println(radii[i]);
            }
            pw.println("end");
        }
    }

    /**
     * Write an information file for psvfp
     *
     * @param outPath Path for the file
     * @param options for opening the file
     * @throws IOException If an I/O error happens
     */
    public void writePSVFP(Path outPath, OpenOption... options) throws IOException {
        // if(true)return;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toPSVlines();
            Arrays.stream(structurePart).forEach(pw::println);

            // source
            pw.println(event.getCmtPosition().getR() + " " + event.getCmtPosition().getLatitude() + " " +
                    event.getCmtPosition().getLongitude());
            double[] mt = event.getCmt().getDSMmt();
            pw.println(Arrays.stream(mt).mapToObj(Double::toString).collect(Collectors.joining(" ")) +
                    " Moment Tensor (1.e25 dyne cm)");

            // write info
            pw.println("c write directory");
            pw.println(output + "/");
            pw.println(event);
            pw.println("c events and stations");

            // horizontal positions for perturbation points
            pw.println(positions.length + " nsta");
            Arrays.stream(positions).forEach(pp -> pw.println(pp.getLatitude() + " " + pp.getLongitude()));

            // radii for perturbation points
            pw.println(radii.length + " nr");
            Arrays.stream(radii).forEach(pw::println);
            pw.println("end");
        }
    }

    /**
     * write the information file for psvfpcat (catalog)
     * @param outPath
     * @param thetamin
     * @param thetamax
     * @param dtheta
     * @param options
     * @throws IOException
     */
    public void writePSVFPCAT(Path outPath, double thetamin, double thetamax, double dtheta, OpenOption... options) throws IOException {
        // if(true)return;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toPSVlines();
            Arrays.stream(structurePart).forEach(pw::println);

            // source
            pw.println(event.getCmtPosition().getR() + " " + event.getCmtPosition().getLatitude() + " "
                    + event.getCmtPosition().getLongitude());
            double[] mt = event.getCmt().getDSMmt();
            pw.println(Arrays.stream(mt).mapToObj(Double::toString).collect(Collectors.joining(" "))
                    + " Moment Tensor (1.e25 dyne cm)");

            // output info
            pw.println("c output directory");
            pw.println(output + "/");
            pw.println(event.toString());
            pw.println("c events and stations");

            // catalogue epicentral distance sampling
            pw.println(thetamin + " " + thetamax + " " + dtheta);

            // radii for perturbation points
//			int nComment = (int) IntStream.range(0, commentPerturbationR.length)
//					.mapToObj(i -> commentPerturbationR[i]).filter(c -> c).count();
//			pw.println(RADII.length - nComment + " nr");
            pw.println(radii.length + " nr");
            for (int i = 0; i < radii.length; i++) {
//				if (commentPerturbationR[i])
//					pw.println("c " + RADII[i]);
//				else
                    pw.println(radii[i]);
            }
            pw.println("end");
        }
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
        return radii.clone();
    }

    /**
     * @return structure to be used
     */
    public PolynomialStructure getStructure() {
        return structure;
    }

}
