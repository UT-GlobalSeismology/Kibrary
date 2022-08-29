package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.spc.SPCMode;
import io.github.kensuke1984.kibrary.util.spc.SPCType;

/**
 * Class for automatically generating shellscript files to execute TIPSV and TISH.
 *
 * @author otsuru
 * @since 2021/12/23
 */
class DSMShellscript {

    private Path workPath;
    /**
     * Information file name is header_[psv,sh].inf
     */
    private String header;
    private boolean mpi;
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
     *
     * @param workPath
     * @param mpi (boolean) Whether to use MPI
     * @param nSources (int) Number of sources to be processed
     */
    public DSMShellscript(Path workPath, boolean mpi, int nSources, String header) {
        this.workPath = workPath;
        this.mpi = mpi;
        this.header = header;

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

        // Number of cores that will run simultaneously = nSimRun * nCore. Remainders will not be used.
        nSimRun = nThreads / nCore;

        // Number of sources that will be processed = nBlock * nSimRun.
        nBlock = nSources / nSimRun + ((nSources % nSimRun == 0) ? 0 : 1);
    }

    /**
     * Writes a shellscript file to execute DSM.
     * @param type ({@link SPCType}) SYNTHETIC, PF, or PB
     * @param mode ({@link SPCMode}) PSV or SH
     * @throws IOException
     *
     * @author otsuru
     * @since 2022/2/5
     */
    public void write(SPCType type, SPCMode mode) throws IOException {
        String fileNameRoot;
        String enterFolder;
        String exitFolder;
        String programName;
        String mvString;

        switch (type) {
        case SYNTHETIC:
            fileNameRoot = "runDSM_" + mode;
            enterFolder = "./*[A-Z]";
            exitFolder = "../";
            programName = (mode == SPCMode.PSV ? "tipsv" : "tish");
            mvString = null;
            break;
        case PF:
            fileNameRoot = "runFP_" + mode;
            enterFolder = "./FPqueue/*[A-Z]";
            exitFolder = "../../";
            programName = (mode == SPCMode.PSV ? "psvfp" : "shfp");
            mvString = "mv $j FPinfo";
            break;
        case PB:
            fileNameRoot = "runBP_" + mode;
            enterFolder = "./BPqueue/[M-Q]*";
            exitFolder = "../../";
            programName = (mode == SPCMode.PSV ? "psvbp" : "shbp");
            mvString = "mv $j BPinfo";
            break;
        default:
            throw new IllegalArgumentException("This SPCType is not supported yet.");
        }

        String programString;
        if (mpi) programString = "mpirun -n $Ncore $(which mpi-" + programName + ")";
        else programString = programName;

        Path shellPath = workPath.resolve(fileNameRoot + ".sh");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(shellPath))) {
            pw.println("#!/bin/sh");
            pw.println("Nblock=" + nBlock);
            pw.println("Nsimrun=" + nSimRun);
            pw.println("Ncore=" + nCore);
            pw.println();
            pw.println("for i in $(seq 1 $Nblock)");
            pw.println("do");
            pw.println("  nstart=$(echo \"$(( ($i-1) * $Nsimrun + 1))\")");
            pw.println("  nend=$(echo \"$(($i * $Nsimrun))\")");
            pw.println("  echo \"$nstart $nend\"");
            pw.println("  for j in $(for k in " + enterFolder + "; do echo $k; done | sed -n $nstart,${nend}p)");
            pw.println("  do");
            pw.println("    cd $j");
            pw.println("    " + programString + " < " + header + "_" + mode + ".inf > " + fileNameRoot + ".log &");
            pw.println("    cd " + exitFolder);
            pw.println("  done");
            pw.println("  wait");
            pw.println("done");

            if (mvString != null) {
                pw.println("  ");
                pw.println("for j in " + enterFolder);
                pw.println("do");
                pw.println("  " + mvString);
                pw.println("done");
            }
        }
    }


}
