package io.github.kensuke1984.kibrary.source;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACExtension;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAid;
import io.github.kensuke1984.kibrary.util.spc.SPC_SAC;

/**
 * Operation to convolve source time functions on timeseries data stored as SAC files.
 * <p>
 * Waveforms in time domain are converted to frequency domain using FFT, convolved STFs, then converted back to time domain.
 * <p>
 * The (&omega; - i &omega;<sub>i</sub>)-domain used in DSM can be used instead of the regular &omega;-domain.
 * This will ensure that the resulting waveforms are identical to those directly convolved in {@link SPC_SAC}.
 *
 * @author otsuru
 * @since 2024/11/18
 */
public class SourceTimeFunctionConvolver extends Operation {

    private static final double TAPER_LENGTH_PERCENT = 5.0;

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
     * Path of the output folder.
     */
    private Path outPath;
    /**
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * The root folder containing event folders which have synthetic SAC files to be filtered.
     */
    private Path synPath;
    /**
     * Sampling frequency of input SAC files [Hz].
     */
    private double sacSamplingHz;

    /**
     * Source time function. {0: none, 1: boxcar, 2: triangle, 3: asymmetric triangle, 4: auto}
     */
    private SourceTimeFunctionType sourceTimeFunctionType;
    /**
     * Folder containing user-defined source time functions.
     */
    private Path userSourceTimeFunctionPath;
    /**
     * Catalog containing source time function durations.
     */
    private Path sourceTimeFunctionCatalogPath;
    /**
     * SAC files with NPTS over this value will be slimmed.
     */
    private int npts;
    /**
     * Number of steps in frequency domain (counting only positive frequency part) during computation.
     */
    private int np;
    /**
     * Artificial damping to apply upon Fourier transform. This is used to match the procedures of DSM.
     */
    private double artificialDamping = 1.e-2;

    private SourceTimeFunctionHandler stfHandler;
    /**
     * Number of processed event folders
     */
    private AtomicInteger processedFolders = new AtomicInteger();

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##SacComponents to be applied the filter, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a root folder containing synthetic dataset. (.)");
            pw.println("#synPath ");
            pw.println("##(double) Sampling frequency of input SAC files [Hz]. Files with a different value will be ignored. (20)");
            pw.println("#sacSamplingHz ");
            pw.println("##Path of folder containing source time functions. If not set, the following sourceTimeFunctionType will be used.");
            pw.println("#userSourceTimeFunctionPath ");
            pw.println("##Type of source time function, from {0:none, 1:boxcar, 2:triangle, 3:asymmetricTriangle, 4:auto}. (0)");
            pw.println("##  When 'auto' is selected, the function specified in the GCMT catalog will be used.");
            pw.println("#sourceTimeFunctionType ");
            pw.println("##Path of a catalog to set source time function durations. If unneeded, leave this unset.");
            pw.println("#sourceTimeFunctionCatalogPath ");
            pw.println("##NPTS, only if you want to slim SAC files down to that specific number, must be a power of 2.");
            pw.println("##  Otherwise, SAC files are slimmed to the largest power of 2 that does not exceed its length.");
            pw.println("#npts ");
            pw.println("##Number of steps in frequency domain during convolution, only if specifying it, must be a power of 2.");
            pw.println("##  Otherwise, (npts of waveform)/2 will be used.");
            pw.println("#np ");
            pw.println("##Artificial damping to apply upon Fourier transform, used to match the procedures of DSM. (0.01)");
            pw.println("#artificialDamping 0");
        }
        System.err.println(outPath + " is created.");
    }

    public SourceTimeFunctionConvolver(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        synPath = property.parsePath("synPath", ".", true, workPath);
        sacSamplingHz = property.parseDouble("sacSamplingHz", "20");

        if (property.containsKey("userSourceTimeFunctionPath")) {
            userSourceTimeFunctionPath = property.parsePath("userSourceTimeFunctionPath", null, true, workPath);
        } else {
            sourceTimeFunctionType = SourceTimeFunctionType.ofNumber(property.parseInt("sourceTimeFunctionType", "0"));
        }
        if (property.containsKey("sourceTimeFunctionCatalogPath")) {
            sourceTimeFunctionCatalogPath = property.parsePath("sourceTimeFunctionCatalogPath", null, true, workPath);
        }
        npts = property.parseInt("npts", String.valueOf(Integer.highestOneBit(Integer.MAX_VALUE)));
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("npts must be a power of 2.");
        np = property.parseInt("np", String.valueOf(Integer.highestOneBit(Integer.MAX_VALUE)));
        if (np != Integer.highestOneBit(np)) throw new IllegalArgumentException("np must be a power of 2.");
        artificialDamping = property.parseDouble("artificialDamping", "0.01");
    }

    @Override
    public void run() throws IOException {
        stfHandler = new SourceTimeFunctionHandler(sourceTimeFunctionType,
                sourceTimeFunctionCatalogPath, userSourceTimeFunctionPath, null);

        Set<EventFolder> eventDirs = new HashSet<>();
        eventDirs.addAll(Files.exists(synPath) ? DatasetAid.eventFolderSet(synPath) : Collections.emptySet());
        int totalNum = eventDirs.size();
        System.err.println("Number of events in synDir: " + totalNum);
        if (totalNum == 0) {
            System.err.println("No events found.");
            return;
        }

        outPath = DatasetAid.createOutputFolder(workPath, "convolved", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        eventDirs.stream().map(this::process).forEach(es::execute);
        es.shutdown();
        System.err.println("Convolving STFs to SAC files ...");
        while (!es.isTerminated()) {
            System.err.print("\r " + MathAid.ceil(100.0 * processedFolders.get() / eventDirs.size()) + "% of events done");
            ThreadAid.sleep(100);
        }
        System.err.println("\r Finished handling all events.");
    }

    private Runnable process(EventFolder eventDir) {
        return () -> {
            try {
                Set<SACFileName> sacNameSet = eventDir.sacFileSet();
                sacNameSet.removeIf(s -> !judgeSAC(s));

                // escape if the event folder was blank. The 'finally' will be executed, so count will be incremented.
                if(sacNameSet.size() == 0) {
                    return;
                }

                Files.createDirectories(outPath.resolve(eventDir.getName()));
                sacNameSet.forEach(this::convolveAndOut);
            } catch (Exception e) {
                // if an exception is thrown, ignore that event folder and finish up the rest
                System.err.println("Error on " + eventDir);
                e.printStackTrace();
            } finally {
                processedFolders.incrementAndGet();
            }
        };
    }

    private boolean judgeSAC(SACFileName sacName) {
        if (!components.contains(sacName.getComponent())) return false;
        try {
            SACHeaderAccess sacHeader = sacName.readHeader();
            double delta = MathAid.roundForPrecision(1.0 / sacSamplingHz);
            if (sacHeader.getValue(SACHeaderEnum.DELTA) != delta) {
                System.err.println("! Sampling frequency is not " + sacSamplingHz + ", skipping: " + sacName.toString());
                return false;
            }
            return true;
        } catch (IOException e) {
            System.err.println("!! Failed to read header of " + sacName.toString() + ", skipping.");
            return false;
        }
    }

    private void convolveAndOut(SACFileName name) {
        try {
            SACFileAccess sacFile = name.read();
            int sacNpts = sacFile.getInt(SACHeaderEnum.NPTS);
            double delta = sacFile.getValue(SACHeaderEnum.DELTA);

            // cut length to a power of 2
            int finalNpts = (npts < sacNpts) ? npts : Integer.highestOneBit(sacNpts);
            sacFile = sacFile.cut(finalNpts);

            // get waveform data and taper
            double[] waveData = sacFile.createTrace().taper(TAPER_LENGTH_PERCENT).getY();
            Complex[] complexWave = Arrays.stream(waveData).mapToObj(Complex::new).toArray(Complex[]::new);

            // FFT to frequency domain
            double tlen = finalNpts * delta;
            double samplingHz = 1 / delta;
            double omegaI = -Math.log(artificialDamping) / tlen;
            int npToUse = (np < Integer.highestOneBit(Integer.MAX_VALUE)) ? np : finalNpts / 2;
            complexWave = SPCFileAid.convertToFrequencyDomain(complexWave, npToUse, samplingHz, omegaI);

            // set up STF
            SourceTimeFunction sourceTimeFunction = stfHandler.createSourceTimeFunction(npToUse, tlen, samplingHz, sacFile.getGlobalCMTID());
            if (sourceTimeFunction == null) throw new IllegalStateException("No STF created.");

            // convolve STF
            complexWave = sourceTimeFunction.convolve(complexWave, false);

            // FFT back to time domain
            complexWave = SPCFileAid.convertToTimeDomain(complexWave, npToUse, finalNpts, samplingHz, omegaI);
            waveData = Arrays.stream(complexWave).mapToDouble(Complex::getReal).toArray();

            // set new waveform
            sacFile = sacFile.setSACData(waveData);

            // write SAC file. If there are SAC files with the same name, this throws an exception
            SACExtension ext = SACExtension.valueOfConvolutedSynthetic(name.getComponent());
            String outSacName = SACFileName.generate(name, ext);
            Path outSacPath = outPath.resolve(name.getGlobalCMTID().toString()).resolve(outSacName);
            sacFile.writeSAC(outSacPath, StandardOpenOption.CREATE_NEW);

        } catch (Exception e) {
            // if an exception is thrown, move on to the next SAC file
            System.err.println("Error on " + name.getPath());
            e.printStackTrace();
        }
    }

}
