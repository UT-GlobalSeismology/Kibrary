package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.spc.SPCMode;

/**
 * Class for automatically generating shellscript files to execute DSM programs such as TIPSV, SHFP, etc.
 *
 * @author otsuru
 * @since 2021/12/23
 */
class DSMShellscript {

    /**
     * Information file name is header_[psv,sh].inf
     */
    private String header;
    private boolean mpi;
    /**
     * Number of sources
     */
    private int nSources;
    /**
     * Number of blocks of {nSimRun} sources to operate for
     */
    private int nBlock;
    /**
     *  Number of MPI processes to run simultaneously
     */
    private int nSimRun;
    /**
     * Number of cores to run per MPI process
     */
    private int nCore;

    /**
     * Set parameters.
     * Number of cores that will run simultaneously = nSimRun * nCore.
     * Number of sources that will be processed = nBlock * nSimRun.
     * If MPI is not used, nCore is ignored.
     * @param mpi (boolean) Whether to use MPI.
     * @param nSources (int) Number of sources to be processed.
     * @param header (String) Prefix of information file name.
     */
    DSMShellscript(boolean mpi, int nSources, String header) {
        this.mpi = mpi;
        this.header = header;
        this.nSources = nSources;

        int nThreads = Runtime.getRuntime().availableProcessors() - 1;
        if (mpi) {
            if (nThreads >= 32) {
                nCore = 8;
            } else if (nThreads >= 8) {
                nCore = 4;
            } else {
                nCore = 1;
            }
        } else {
            nCore = 1;
        }

        // number of sources to compute simultaneously
        // Number of cores that will run simultaneously = nSimRun * nCore. Remainders will not be used.
        nSimRun = nThreads / nCore;

        // number of sets of computation
        // Number of sources that will be processed = nBlock * nSimRun.
        nBlock = MathAid.divideUp(nSources, nSimRun);
    }

    /**
     * Writes a shellscript file to execute DSM.
     * @param type ({@link DSMType}) SYNTHETIC, FP, BP, I1D, or TI1D.
     * @param mode ({@link SPCMode}) PSV or SH.
     * @param listFileName (String) Name of file containing list of sources to compute for.
     * @param outputPath (Path) Path of shellscript file to create.
     * @throws IOException
     *
     * @author otsuru
     * @since 2022/2/5
     */
    void write(DSMType type, SPCMode mode, String listFileName, Path outputPath) throws IOException {
        String enterFolder;
        String exitFolder;
        String programName;

        switch (type) {
        case SYNTHETIC:
            enterFolder = "./";
            exitFolder = "../";
            programName = (mode == SPCMode.PSV ? "tipsv" : "tish");
            break;
        case FP:
            enterFolder = "./FPpool/";
            exitFolder = "../../";
            programName = (mode == SPCMode.PSV ? "psvfp" : "shfp");
            break;
        case BP:
            enterFolder = "./BPpool/";
            exitFolder = "../../";
            programName = (mode == SPCMode.PSV ? "psvbp" : "shbp");
            break;
        case I1D:
            enterFolder = "./";
            exitFolder = "../";
            programName = (mode == SPCMode.PSV ? "sshpsvi" : "sshshi");
            break;
        case TI1D:
            enterFolder = "./";
            exitFolder = "../";
            programName = (mode == SPCMode.PSV ? "sshpsv" : "sshsh");
            break;
        default:
            throw new IllegalArgumentException("This SPCType is not supported yet.");
        }

        String programString;
        if (mpi) programString = "mpirun -n $Ncore $(which mpi-" + programName + ")";
        else programString = programName;
        String fileNameRoot = FileAid.extractNameRoot(outputPath);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println("#!/bin/sh");
            pw.println("Nblock=" + nBlock);
            pw.println("Nsimrun=" + nSimRun);
            pw.println("Ncore=" + nCore);
            pw.println();
            pw.println("echo \"" + MathAid.switchSingularPlural(nSources, "source", "sources") + " \"");
            pw.println("start=$(date +'%s')");
            pw.println("echo \"start: $(date -d \"@${start}\" +'%Y-%m-%d %H:%M:%S (%:z)')\"");
            pw.println();
            pw.println("for i in $(seq 1 $Nblock)");
            pw.println("do");
            pw.println("  nstart=$(echo \"$(( ($i-1) * $Nsimrun + 1))\")");
            pw.println("  nend=$(echo \"$(($i * $Nsimrun))\")");
            pw.println("  echo \"$nstart $nend\"");
            pw.println("  for j in $(cat " + listFileName + " | sed -n $nstart,${nend}p)");
            pw.println("  do");
            pw.println("    cd " + enterFolder + "$j");
            pw.println("    " + programString + " < " + header + "_" + mode + ".inf > " + fileNameRoot + ".log &");
            pw.println("    cd " + exitFolder);
            pw.println("  done");
            pw.println("  wait");
            pw.println("done");
            pw.println();
            pw.println("end=$(date +'%s')");
            pw.println("echo \"end  : $(date -d \"@${end}\" +'%Y-%m-%d %H:%M:%S (%:z)')\"");
            pw.println("elapsed=$(echo \"$end - $start\" | bc)");
            pw.println("sec=$((elapsed%60)) ; min=$(((elapsed%3600)/60)) ; hrs=$((elapsed/3600))");
            pw.println("timestamp=$(printf \"%d:%02d:%02d\" \"$hrs\" \"$min\" \"$sec\")");
            pw.println("echo \"Finished in $timestamp\"");
        }
    }

    static enum DSMType {
        SYNTHETIC,
        FP, BP,
        I1D, TI1D
    }
}
