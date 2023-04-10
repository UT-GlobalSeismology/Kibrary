package io.github.kensuke1984.kibrary.quick;

import java.io.IOException;
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

public class LookAtBPspc {

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
        options.addOption(Option.builder("p").longOpt("phi").hasArg().argName("phi").required()
                .desc("Export content of spcSac file").build());
        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Set path of output file").build());
        return options;
    }

    public static void run(CommandLine cmdLine) throws IOException{
        String str = cmdLine.getOptionValue("i");
        SPCFileName spcName = new FormattedSPCFileName(Paths.get(str));

        SPCFileAccess dsmOutput = null;
        if (cmdLine.hasOption("p")) {
            double phi = Double.parseDouble(cmdLine.getOptionValue("p"));
            System.out.println("Phi = " + phi);
            dsmOutput = SPCFile.getInstance(spcName, phi);
        } else {
            dsmOutput = spcName.read();
        }

        print(dsmOutput, cmdLine);
    }

    public static void print(SPCFileAccess dsmOutput, CommandLine cmdLine) throws IOException{
        String obsName = dsmOutput.getStationCode();
        String netwkName = dsmOutput.getNetworkCode();
        String sourceID = dsmOutput.getSourceID();
        HorizontalPosition observerPosition = dsmOutput.getObserverPosition();
        FullPosition sourceLocation = dsmOutput.getSourcePosition();

        double distance = sourceLocation.computeEpicentralDistanceRad(observerPosition) * 180. / Math.PI;

//		SPCTensorComponent c1 = SPCTensorComponent.valueOf27Conmponent(1, 1, 2);
//		SPCTensorComponent c2 = SPCTensorComponent.valueOf27Conmponent(1, 2, 2);
//		SPCTensorComponent c3 = SPCTensorComponent.valueOf27Conmponent(1, 3, 2);
//		SPCTensorComponent c4 = SPCTensorComponent.valueOf27Conmponent(2, 1, 2);
//		SPCTensorComponent c5 = SPCTensorComponent.valueOf27Conmponent(2, 2, 2);
//		SPCTensorComponent c6 = SPCTensorComponent.valueOf27Conmponent(2, 3, 2);
//		SPCTensorComponent c7 = SPCTensorComponent.valueOf27Conmponent(3, 1, 2);
//		SPCTensorComponent c8 = SPCTensorComponent.valueOf27Conmponent(3, 2, 2);
//		SPCTensorComponent c9 = SPCTensorComponent.valueOf27Conmponent(3, 3, 2);

        SPCTensorComponent[] c = new SPCTensorComponent[27];
        for (int i=1; i <= 3; i++) {
            for (int r=1; r <= 3; r++) {
                for (int s=1; s <= 3; s++) {
                    c[9*(i-1)+3*(r-1)+s-1] = SPCTensorComponent.valueOf27Conmponent(i, r, s);
                }
            }
        }

//		SpcTensorComponent c1 = SpcTensorComponent.valueOfBP(1, 1, 3);
//		SpcTensorComponent c2 = SpcTensorComponent.valueOfBP(1, 2, 3);
//		SpcTensorComponent c3 = SpcTensorComponent.valueOfBP(1, 3, 3);
//		SpcTensorComponent c4 = SpcTensorComponent.valueOfBP(2, 1, 3);
//		SpcTensorComponent c5 = SpcTensorComponent.valueOfBP(2, 2, 3);
//		SpcTensorComponent c6 = SpcTensorComponent.valueOfBP(2, 3, 3);
//		SpcTensorComponent c7 = SpcTensorComponent.valueOfBP(3, 1, 3);
//		SpcTensorComponent c8 = SpcTensorComponent.valueOfBP(3, 2, 3);
//		SpcTensorComponent c9 = SpcTensorComponent.valueOfBP(3, 3, 3);

//		Complex[] spcC1 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c1).getValueInFrequencyDomain();
//		Complex[] spcC2 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c2).getValueInFrequencyDomain();
//		Complex[] spcC3 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c3).getValueInFrequencyDomain();
//		Complex[] spcC4 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c4).getValueInFrequencyDomain();
//		Complex[] spcC5 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c5).getValueInFrequencyDomain();
//		Complex[] spcC6 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c6).getValueInFrequencyDomain();
//		Complex[] spcC7 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c7).getValueInFrequencyDomain();
//		Complex[] spcC8 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c8).getValueInFrequencyDomain();
//		Complex[] spcC9 = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c9).getValueInFrequencyDomain();

        Complex[][] spcC = new Complex[27][];
        for (int j=0; j<27; j++) {
            spcC[j] = dsmOutput.getSpcBodyList().get(0).getSpcComponent(c[j]).getValueInFrequencyDomain();
        }

        /* TODO to ryoichi: uncomment this when done modifying
        double[] rs = dsmOutput.getBodyR();

        if (cmdLine.hasOption("o")) {
            Path outputIdsPath;
            outputIdsPath = Paths.get(cmdLine.getOptionValue("o"));
            try(PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputIdsPath))) {
                pw.println("Epicentral distance = " + distance);
                pw.println("#Observer: " + obsName + " " + netwkName + " " + observerPosition + " Source: " + sourceID + " " + sourceLocation);
                pw.println("#perturbation radius= " + rs[0]);
                for (int i=0; i < spcC1.length; i++) {
                    String real = "";
                    String imag = "";
                    real = String.format(" %.16e", spcC1[i].getReal()) + String.format(" %.16e", spcC2[i].getReal()) + String.format(" %.16e", spcC3[i].getReal())
                    + String.format(" %.16e", spcC4[i].getReal()) + String.format(" %.16e", spcC5[i].getReal()) + String.format(" %.16e", spcC6[i].getReal())
                    + String.format(" %.16e", spcC7[i].getReal()) + String.format(" %.16e", spcC8[i].getReal()) + String.format(" %.16e", spcC9[i].getReal());
                    imag = String.format(" %.16e", spcC1[i].getImaginary()) + String.format(" %.16e", spcC2[i].getImaginary()) + String.format(" %.16e", spcC3[i].getImaginary())
                    + String.format(" %.16e", spcC4[i].getImaginary()) + String.format(" %.16e", spcC5[i].getImaginary()) + String.format(" %.16e", spcC6[i].getImaginary())
                    + String.format(" %.16e", spcC7[i].getImaginary()) + String.format(" %.16e", spcC8[i].getImaginary()) + String.format(" %.16e", spcC9[i].getImaginary());
                    pw.println("(Real) " + i + real);
                    pw.println("(Imag) " + i + imag);
                }
            }
        } else {
            System.out.println("Epicentral distance = " + distance);
            System.out.println("#Observer: " + obsName + " " + netwkName + " " + observerPosition + " Source: " + sourceID + " " + sourceLocation);
            System.out.println("#perturbation radius= " + rs[0]);
            for (int i = 0; i < spcC1.length; i++) {
                System.out.printf("(Real) %d %.16e %.16e %.16e %.16e %.16e %.16e %.16e %.16e %.16e\n", i, spcC1[i].getReal(), spcC2[i].getReal(), spcC3[i].getReal()
                        ,spcC4[i].getReal(), spcC5[i].getReal(),spcC6[i].getReal(), spcC7[i].getReal(),spcC8[i].getReal(), spcC9[i].getReal());
                System.out.printf("(Imag) %d %.16e %.16e %.16e %.16e %.16e %.16e %.16e %.16e %.16e\n", i, spcC1[i].getImaginary(), spcC2[i].getImaginary(), spcC3[i].getImaginary()
                        ,spcC4[i].getImaginary(), spcC5[i].getImaginary(),spcC6[i].getImaginary(), spcC7[i].getImaginary(),spcC8[i].getImaginary(), spcC9[i].getImaginary());
            }
        }
        */
    }

    public static void printHeader(SPCFileAccess dsmOutput) {
        String obsName = dsmOutput.getStationCode();
        String netwkName = dsmOutput.getNetworkCode();
        String sourceID = dsmOutput.getSourceID();
        HorizontalPosition observerPosition = dsmOutput.getObserverPosition();
        FullPosition sourceLocation = dsmOutput.getSourcePosition();

        System.out.println("#Observer: " + obsName + " " + netwkName + " " + observerPosition + " Source: " + sourceID + " " + sourceLocation);
    }
}