package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.util.DatasetUtils;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetUtils;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

/**
 * Creates record section.
 * <p>
 * Waveform data for each observer must be written in txt files under event directories.
 * Output pdf files will be created under each of the existing event directories.
 *
 * @author otsuru
 * @since 2021/12/11
 */
public class RecordSectionCreater implements Operation {

    private final Properties property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * {@link Path} of a basic ID file
     */
    private Path basicIDPath;
    /**
     * components to be included in the dataset
     */
    private Set<SACComponent> components;

    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Property.generatePath(RecordSectionCreater.class);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan RecordSectionCreater");
            pw.println("##Path of a working directory. This must contain event directories with waveform txt files. (.)");
            pw.println("#workPath");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components");
            pw.println("##Path of a basic ID file, must be defined");
            pw.println("#basicIDPath actualID.dat");

//            pw.println("##(boolean) Whether to export individual files for each component (true)");
//            pw.println("#splitComponents");
        }
        System.err.println(outPath + " is created.");
    }

    public RecordSectionCreater(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", ".");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("basicIDPath"))
            throw new IllegalArgumentException("There is no information about basicIDPath.");
//        if (!property.containsKey("splitComponents")) property.setProperty("splitComponents", "true");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        basicIDPath = getPath("basicIDPath");
        if (!Files.exists(basicIDPath))
            throw new NoSuchFileException("The basic ID file " + basicIDPath + " does not exist");

//        splitComponents = Boolean.parseBoolean(property.getProperty("splitComponents"));
    }

   /**
    *
    * @param args [a property file name]
    * @throws Exception if any
    */
   public static void main(String[] args) throws IOException {
       RecordSectionCreater rsc = new RecordSectionCreater(Property.parse(args));
       long startTime = System.nanoTime();
       System.err.println(RecordSectionCreater.class.getName() + " is operating.");
       rsc.run();
       System.err.println(RecordSectionCreater.class.getName() + " finished in "
               + GadgetUtils.toTimeString(System.nanoTime() - startTime));
   }

   @Override
   public void run() throws IOException {
       Set<EventFolder> eventDirs = DatasetUtils.eventFolderSet(workPath);
       if (!DatasetUtils.checkEventNum(eventDirs.size())) {
           return;
       }

       BasicID[] ids = BasicIDFile.read(basicIDPath);

       for (EventFolder eventDir : eventDirs) {
           for (SACComponent component : components) {
               BasicID[] useIds = Arrays.stream(ids).filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID())
                       && id.getSacComponent().equals(component))
                       .sorted(Comparator.comparing(BasicID::getObserver))
                       .collect(Collectors.toList()).toArray(new BasicID[0]);

               String fileNameRoot = "profile_" + eventDir.toString() + "_" + component.toString();
               createRecordSection(eventDir, useIds, fileNameRoot);
           }
       }
   }

    private static void createRecordSection(EventFolder eventDir, BasicID[] ids, String fileNameRoot) throws IOException {
        if (ids.length == 0) {
            return;
        }

        List<BasicID> obsList = new ArrayList<>();
        List<BasicID> synList = new ArrayList<>();
        BasicIDFile.pairUp(ids, obsList, synList);

        GnuplotFile gnuplot = new GnuplotFile(eventDir.toPath().resolve(fileNameRoot + ".plt"));

        GnuplotLineAppearance obsAppearance = new GnuplotLineAppearance(1, GnuplotColorName.black, 1);
        GnuplotLineAppearance synAppearance = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);

        gnuplot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
        gnuplot.setMargin(15, 15);
        gnuplot.setFont("Arial", 10, 8, 8, 8, 8);
        gnuplot.unsetKey();

        gnuplot.setTitle(eventDir.toString());
        gnuplot.setXlabel("Time aligned on S-wave arrival (s)"); //TODO
        gnuplot.setYlabel("Distance (deg)");

        for (BasicID obsID : obsList) {
            double distance = obsID.getGlobalCMTID().getEvent().getCmtLocation()
                    .getEpicentralDistance(obsID.getObserver().getPosition()) * 180. / Math.PI;
            double maxObs = 1.0;

            String filename = obsID.getObserver() + "." + obsID.getGlobalCMTID() + "." + obsID.getSacComponent() + ".txt";
            gnuplot.addLabel(obsID.getObserver().toString(), "graph", 1, "first", distance);
            gnuplot.addLine(filename, String.format("3:($2/%.3e+%.2f) ", maxObs, distance), obsAppearance, "observed");
            gnuplot.addLine(filename, String.format("3:($4/%.3e+%.2f) ", maxObs, distance), synAppearance, "synthetic");
        }

        gnuplot.write();
        if (!gnuplot.execute(eventDir.toPath())) System.err.println("gnuplot failed!!");

    }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

    @Override
    public Path getWorkPath() {
        return workPath;
    }

}
