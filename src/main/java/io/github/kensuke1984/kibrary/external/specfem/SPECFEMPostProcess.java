package io.github.kensuke1984.kibrary.external.specfem;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;

/**
 * Class to handle files produced by SPECFEM.
 *
 * @author Anselme
 * @since a long time ago
 * @version 2024/11/4 Renamed from specfem.ProcessResult to external.specfem.SPECFEMPostProcess
 */
public class SPECFEMPostProcess {

    /**
     * Create scripts to post-process results of SPECFEM.
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
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        // settings
        OptionGroup inputOption = new OptionGroup();
        inputOption.setRequired(true);
        inputOption.addOption(Option.builder("1").longOpt("single")
                .desc("Run in a folder that contains output of SPECFEM3D_GLOBE for 1 event.").build());
        inputOption.addOption(Option.builder("2").longOpt("multiple")
                .desc("Run in a folder that contains multiple event folders output by SPECFEM3D_GLOBE.").build());
        options.addOptionGroup(inputOption);

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        boolean multiple;
        if (cmdLine.hasOption("1")) {
            multiple = false;
        } else if (cmdLine.hasOption("2")) {
            multiple = false;
        } else {
            throw new IllegalArgumentException("Either -1 or -2 must be specified.");
        }

        Path rootPath = Paths.get(".");
        createSACScript(rootPath, multiple);
    }

    private static void createSACScript(Path root, boolean multiple) throws IOException {
        Path sacmacroFile = root.resolve("process.sacm");
        Path sacShell = root.resolve("runsacm.sh");
        Path shellScript = root.resolve("runpostprocess.sh");

        try (PrintWriter pw = new PrintWriter(sacmacroFile.toFile())) {
            pw.println("SETBB dir $1");
            pw.println("DO file WILD %dir%/*sac");
            pw.println("r $file");
            pw.println("interpolate delta 0.05");
            pw.println("if &1,KCMPNM EQ \"MXT\"");
            pw.println("SETBB COMP \"T\"");
            pw.println("elseif &1,KCMPNM EQ \"MXR\"");
            pw.println("SETBB COMP \"R\"");
            pw.println("elseif &1,KCMPNM EQ \"MXZ\"");
            pw.println("SETBB COMP \"Z\"");
            pw.println("endif");
            pw.println("chnhdr KCMPNM %COMP");
            pw.println("dif five");
            pw.println("mul -1");
            pw.println("w %dir%/&1,KSTNM&_&1,KNETWK&.&1,KEVNM&.%COMP%s");
            pw.println("ENDDO");
        }

        try (PrintWriter pw = new PrintWriter(sacShell.toFile())) {
            pw.println("sac <<EOF");
            pw.println("macro process.sacm $1");
            pw.println("quit");
            pw.println("EOF");
        }

        if (multiple) {
            try (PrintWriter pw = new PrintWriter(shellScript.toFile())) {
                pw.println("#!/bin/sh");
                pw.println("#Run in a folder that contains multiple event folders output by SPECFEM3D_GLOBE.");
                pw.println("export SAC_DISPLAY_COPYRIGHT=0");
                pw.println();
                pw.println("for i in ./run00*/OUTPUT_FILES");
                pw.println("do");
                pw.println("  echo $i");
                pw.println("  sh runsacm.sh $i");
                pw.println("done");
                pw.println();
                pw.println("for i in run00*");
                pw.println("do");
                pw.println("  cd $i");
                pw.println("  dir=$(awk 'NR==2 {print $3}' DATA/CMTSOLUTION)");
                pw.println("  echo $dir");
                pw.println("  mkdir $dir");
                pw.println("  mv OUTPUT_FILES/*s $dir");
                pw.println("  cd ..");
                pw.println("done");
            }
        } else {
            try (PrintWriter pw = new PrintWriter(shellScript.toFile())) {
                pw.println("#!/bin/sh");
                pw.println("#Run in a folder that contains output of SPECFEM3D_GLOBE for 1 event.");
                pw.println("export SAC_DISPLAY_COPYRIGHT=0");
                pw.println();
                pw.println("sh runsacm.sh OUTPUT_FILES");
                pw.println();
                pw.println("dir=$(awk 'NR==2 {print $3}' DATA/CMTSOLUTION)");
                pw.println("echo $dir");
                pw.println("mkdir $dir");
                pw.println("mv OUTPUT_FILES/*s $dir");
            }
        }
    }

}
