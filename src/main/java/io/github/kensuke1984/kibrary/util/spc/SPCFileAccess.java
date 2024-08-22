/**
 *
 */
package io.github.kensuke1984.kibrary.util.spc;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * Spectrum file written by DSM. Binary format.
 *
 * @author Kensuke Konishi
 * @since version 0.0.1
 * @author anselme add network
 */
public interface SPCFileAccess {

    /**
     * @return number of bodies
     */
    int nbody();

    /**
     * @return list of spc bodies
     */
    List<SPCBody> getSpcBodyList();

    void setSpcBody(int i, SPCBody body);

    /**
     * @return array of body Rs
     */
    double[] getBodyR();

    /**
     * @return (FullPosition) Position of a seismic source.
     */
    FullPosition getSourcePosition();

    /**
     * @return ID of a source
     */
    String getSourceID();

    /**
     * @return ID of observer (station_network)
     */
    String getReceiverID();

    /**
     * @return HorizontalPosition of an observer.
     */
    HorizontalPosition getReceiverPosition();

    /**
     * @return length of time
     */
    double tlen();

    /**
     * @return number of steps in frequency domain.
     */
    int np();

    /**
     * @return OMEGAI
     */
    double omegai();

    /**
     * @return SPCType of this
     */
    SPCType getSpcFileType();


    SPCFileName getSpcFileName();


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * SPC files in binary format will be read and output in ascii format.
     * @param args Options.
     * @throws IOException if an I/O error occurs
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
    public static Options defineOptions() throws IOException{
        Options options = Summon.defaultOptions();
        //input
        options.addOption(Option.builder("s").longOpt("spc").hasArg().argName("spcFile").required()
                .desc("Path of input SPC file.").build());
        // phi
        options.addOption(Option.builder("p").longOpt("phi").hasArg().argName("phi")
                .desc("Value of azimuth.").build());
        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Path of output file. When not set, writes in standard output.").build());
        options.addOption(Option.builder("h").longOpt("header")
                .desc("Show only header.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String str = cmdLine.getOptionValue("s");
        SPCFileName spcName = new FormattedSPCFileName(Paths.get(str));

        SPCFileAccess dsmOutput = null;
        double phi;
        if (cmdLine.hasOption("p")) {
            phi = Double.parseDouble(cmdLine.getOptionValue("p"));
            dsmOutput = SPCFile.getInstance(spcName, phi);
        } else {
            phi = 0.0;
            dsmOutput = spcName.read();
        }

        Path outputIdsPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o")) : null;
        boolean onlyHeader = cmdLine.hasOption("h");

        print(dsmOutput, phi, onlyHeader, outputIdsPath);
    }

    public static void print(SPCFileAccess dsmOutput, double phi, boolean onlyHeader, Path outputIdsPath) throws IOException {
        String sourceID = dsmOutput.getSourceID();
        String receiverID = dsmOutput.getReceiverID();
        FullPosition sourcePosition = dsmOutput.getSourcePosition();
        HorizontalPosition receiverPosition = dsmOutput.getReceiverPosition();
        double r = dsmOutput.getBodyR()[0];

        SPCType spcType = dsmOutput.getSpcFileType();
        int nComponent = spcType.getNComponent();
        Complex[][] spcs = new Complex[nComponent][];
        for (int i = 0; i < nComponent; i++) {
            spcs[i] = dsmOutput.getSpcBodyList().get(0).getSpcComponent(i).getValueInFrequencyDomain();
        }

        if (outputIdsPath != null) {
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputIdsPath))) {
                pw.println("SPCType: " + spcType);
                pw.println("Source: " + sourceID + " " + sourcePosition);
                pw.println("Receiver: " + receiverID + " " + receiverPosition);
                pw.println("Radius of body 0: " + r);
                pw.println("Phi = " + phi);
                if (!onlyHeader) {
                    for (int k = 0; k < spcs[0].length; k++) {
                        String realPartLine = "(Real) " + k;
                        String imagPartLine = "(Imag) " + k;
                        for (int i = 0; i < nComponent; i++) {
                            realPartLine += String.format(" %.16e", spcs[i][k].getReal());
                            imagPartLine += String.format(" %.16e", spcs[i][k].getImaginary());
                        }
                        pw.println(realPartLine);
                        pw.println(imagPartLine);
                    }
                }
            }
        } else {
            System.out.println("SPCType: " + spcType);
            System.out.println("Source: " + sourceID + " " + sourcePosition);
            System.out.println("Receiver: " + receiverID + " " + receiverPosition);
            System.out.println("Radius of body #0: " + r);
            System.out.println("Phi = " + phi);
            if (!onlyHeader) {
                for (int k = 0; k < spcs[0].length; k++) {
                    String realPartLine = "(Real) " + k;
                    String imagPartLine = "(Imag) " + k;
                    for (int i = 0; i < nComponent; i++) {
                        realPartLine += String.format(" %.16e", spcs[i][k].getReal());
                        imagPartLine += String.format(" %.16e", spcs[i][k].getImaginary());
                    }
                    System.out.println(realPartLine);
                    System.out.println(imagPartLine);
                }
            }
        }
    }

}
