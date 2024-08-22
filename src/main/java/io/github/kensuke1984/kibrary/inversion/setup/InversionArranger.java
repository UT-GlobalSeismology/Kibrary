package io.github.kensuke1984.kibrary.inversion.setup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.WeightingHandler;
import io.github.kensuke1984.kibrary.math.MatrixFile;
import io.github.kensuke1984.kibrary.math.VectorFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Operation for for assembling A<sup>T</sup>A and A<sup>T</sup>d.
 *
 * @author otsuru
 * @since 2022/7/4 created based on part of inversion.LetMeInvert
 */
public class InversionArranger extends Operation {

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;

    /**
     * Basic waveform folder.
     */
    private Path basicPath;
    /**
     * Partial waveform folder.
     */
    private Path partialPath;
    /**
     * Unknown parameter file.
     */
    private Path unknownParameterPath;

    private Path weightingPropertiesPath;

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##Path of a basic waveform folder, must be set.");
            pw.println("#basicPath actual");
            pw.println("##Path of a partial waveform folder, must be set.");
            pw.println("#partialPath partial");
            pw.println("##Path of an unknown parameter list file, must be set.");
            pw.println("#unknownParameterPath unknowns.lst");
            pw.println("##Path of a weighting properties file, must be set.");
            pw.println("#weightingPropertiesPath weighting.properties");
        }
        System.err.println(outPath + " is created.");
    }

    public InversionArranger(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        basicPath = property.parsePath("basicPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        unknownParameterPath = property.parsePath("unknownParameterPath", null, true, workPath);
        weightingPropertiesPath = property.parsePath("weightingPropertiesPath", null, true, workPath);
    }

    @Override
    public void run() throws IOException {

        // read input
        WeightingHandler weightingHandler = new WeightingHandler(weightingPropertiesPath);
        List<UnknownParameter> unknowns = UnknownParameterFile.read(unknownParameterPath);
        List<BasicID> basicIDs = BasicIDFile.read(basicPath, true);
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true);

        // assemble matrices
        MatrixAssembly assembler = new MatrixAssembly(basicIDs, partialIDs, unknowns, weightingHandler);
        RealMatrix ata = assembler.getAta();
        RealVector atd = assembler.getAtd();
        double numIndependent = assembler.getNumIndependent();
        double dNorm = assembler.getD().getNorm();
        double obsNorm = assembler.getObs().getNorm();

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "inversion", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        MatrixFile.write(ata, outPath.resolve("ata.lst"));
        VectorFile.write(atd, outPath.resolve("atd.lst"));
        MatrixAssembly.writeDInfo(numIndependent, dNorm, obsNorm, outPath.resolve("dInfo.inf"));
        UnknownParameterFile.write(unknowns, outPath.resolve("unknowns.lst"));
    }

}
