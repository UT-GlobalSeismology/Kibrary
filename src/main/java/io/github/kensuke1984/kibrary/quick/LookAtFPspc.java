package io.github.kensuke1984.kibrary.quick;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCFile;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCTensorComponent;

public class LookAtFPspc {

    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    public static Options defineOptions() throws IOException{
        Options options = Summon.defaultOptions();
        //input
        options.addOption(Option.builder("i").longOpt("input").hasArg().argName("inputSpcsac").required()
                .desc("Export content of spcSac file").build());
        // phi
        options.addOption(Option.builder("p").longOpt("phi").hasArg().argName("phi")
                .desc("Value of azimuth").build());
        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file").build());
        return options;
    }

    public static void run(CommandLine cmdLine) throws IOException {
        String str = cmdLine.getOptionValue("i");
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

        print(dsmOutput, cmdLine, phi);
    }

    public static void print(SPCFileAccess dsmOutput, CommandLine cmdLine, double phi) throws IOException {
        String obsName = dsmOutput.getReceiverID();
        String sourceID = dsmOutput.getSourceID();
        HorizontalPosition observerPosition = dsmOutput.getReceiverPosition();
        FullPosition sourceLocation = dsmOutput.getSourcePosition();

        double r = dsmOutput.getBodyR()[0];
        Complex[][] spcs = new Complex[9][];
        for(int i = 1; i <= 3; i++) {
            for(int j = 1; j <= 3; j++) {
                SPCTensorComponent comp = SPCTensorComponent.valueOf9Component(i, j);
                spcs[3*(i-1)+j-1] = dsmOutput.getSpcBodyList().get(0).getSpcComponent(comp).getValueInFrequencyDomain();
            }
        }

        if (cmdLine.hasOption("o")) {
            Path outputIdsPath;
            outputIdsPath = Paths.get(cmdLine.getOptionValue("o"));
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputIdsPath))) {
                pw.println("#Observer: " + obsName + " " + observerPosition + " Source: " + sourceID + " " + sourceLocation);
                pw.println("Phi = " + phi);
                pw.println("perturbation radius=" + r);
                for (int k = 0; k < spcs[0].length; k++) {
                    String real = "";
                    String imag = "";
                    for (int i = 0; i < 9; i++) {
                        real += String.format(" %.16e", spcs[i][k].getReal());
                        imag += String.format(" %.16e", spcs[i][k].getImaginary());
                    }
                    pw.println("(Real) " + k + real);
                    pw.println("(Imag) " + k + imag);
                }
            }
         } else {
            System.out.println("#Observer: " + obsName + " " + observerPosition + " Source: " + sourceID + " " + sourceLocation);
            System.out.println("Phi = " + phi);
            System.out.println("perturbation radius=" + r);
            for (int k = 0; k < spcs[0].length; k++) {
                String real = "";
                String imag = "";

                for (int i = 0; i < 9; i++) {
                    real += String.format(" %.16e", spcs[i][k].getReal());
                    imag += String.format(" %.16e", spcs[i][k].getImaginary());
                }
                System.out.println("(Real) " + k + real);
                System.out.println("(Imag) " + k + imag);
            }
        }

    }

    public static void printHeader(SPCFileAccess dsmOutput) {
        String obsName = dsmOutput.getReceiverID();
        String sourceID = dsmOutput.getSourceID();
        HorizontalPosition observerPosition = dsmOutput.getReceiverPosition();
        FullPosition sourceLocation = dsmOutput.getSourcePosition();

        System.out.println("#Observer: " + obsName + " " + observerPosition + " Source: " + sourceID + " " + sourceLocation);
    }

}