package io.github.kensuke1984.kibrary.inv_new;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.Dvector;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * @author otsuru
 * @since 2022/7/4
 */
public class InversionArranger extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * Path of the output folder
     */
    private Path outPath;

    /**
     * path of basic ID file
     */
    protected Path basicIDPath;
    /**
     * path of waveform data
     */
    protected Path basicPath;
    /**
     * path of partial ID file
     */
    protected Path partialIDPath;
    /**
     * path of partial data
     */
    protected Path partialPath;
    /**
     * Path of unknown parameter file
     */
    protected Path unknownParameterListPath;

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
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Path of a basic ID file, must be set");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be set");
            pw.println("#basicPath actual.dat");
            pw.println("##Path of a spcAmpID file");
            pw.println("#spcAmpIDPath ");
            pw.println("##Path of a spcAmp file");
            pw.println("#spcAmpPath ");
            pw.println("##Path of a partial ID file, must be set");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be set");
            pw.println("#partialPath partial.dat");
            pw.println("##Path of a partial spc id file");
            pw.println("#partialSpcIDPath ");
            pw.println("##Path of a partial spc waveform file");
            pw.println("#partialSpcPath ");
            pw.println("##Path of an unknown parameter list file, must be set");
            pw.println("#unknownParameterListPath unknowns.lst");
            pw.println("##Names of inverse methods, listed using spaces, from {CG,SVD,LSM,NNLS,BCGS,FCG,FCGD,NCG,CCG} (CG)");
            pw.println("#inverseMethods ");
        }
        System.err.println(outPath + " is created.");
    }

    public InversionArranger(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);
        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        unknownParameterListPath = property.parsePath("unknownParameterListPath", null, true, workPath);
    }

    @Override
    public void run() throws IOException {
        BasicID[] basicIds = BasicIDFile.read(basicIDPath, basicPath);
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterListPath);

        // set Dvector
        System.err.println("Creating D vector");
        Dvector dVector =  null;


        // create AtA and Atd
        PartialID[] partialIDs = PartialIDFile.read(partialIDPath, partialPath);
    }

}
