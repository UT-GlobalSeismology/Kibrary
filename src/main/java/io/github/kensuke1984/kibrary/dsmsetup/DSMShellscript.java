package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Class for automatically generating shellscript files to execute TIPSV and TISH.
 *
 * @author otsuru
 * @since 2021/12/23
 */
class DSMShellscript {

    private Path workPath;
    private boolean mpi;
    /**
     * Number of blocks of {nSimRun} events to operate for
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
     * Number of events that will be processed = nBlock * nSimRun.
     * If MPI is not used, nCore is ignored.
     *
     * @param workPath
     * @param mpi (boolean) Whether to use MPI
     * @param nEvents (int) Number of events to be processed
     */
    public DSMShellscript(Path workPath, boolean mpi, int nEvents) {
        this.workPath = workPath;
        this.mpi = mpi;

        int nThreads = Runtime.getRuntime().availableProcessors();
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

        // Number of events that will be processed = nBlock * nSimRun.
        nBlock = nEvents / nSimRun + 1;
    }


    /**
     * @throws IOException
     */
    public void writePSV() throws IOException {
        Path psvPath = workPath.resolve("runDSM_psv.sh");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(psvPath))) {
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
            pw.println("  for j in $(for k in ./*[A-Z]; do echo $k; done | sed -n $nstart,${nend}p)");
            pw.println("  do");
            pw.println("    cd $j");
            if (mpi) {
                pw.println("    mpirun -n $Ncore $(which mpi-tipsv) < PREM_PSV.inf > runPSV.log &");
            } else {
                pw.println("    tipsv < PREM_PSV.inf > runPSV.log &");
            }
            pw.println("    cd ../");
            pw.println("  done");
            pw.println("  wait");
            pw.println("done");

        }
    }
    /**
     * @throws IOException
     */
    public void writeSH() throws IOException {
        Path shPath = workPath.resolve("runDSM_sh.sh");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(shPath))) {
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
            pw.println("  for j in $(for k in ./*[A-Z]; do echo $k; done | sed -n $nstart,${nend}p)");
            pw.println("  do");
            pw.println("    cd $j");
            if (mpi) {
                pw.println("    mpirun -n $Ncore $(which mpi-tish) < PREM_SH.inf > runSH.log &");
            } else {
                pw.println("    tish < PREM_SH.inf > runSH.log &");
            }
            pw.println("    cd ../");
            pw.println("  done");
            pw.println("  wait");
            pw.println("done");

        }
    }
}
