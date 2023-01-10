package io.github.kensuke1984.kibrary.entrance;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * @author otsuru
 * @since 2023/1/10
 */
public class NetworkLookup extends Operation {

    private static final int MAX_NUM = 10;

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;

    /**
     * Path of a data entry file
     */
    private Path dataEntryPath;
    /**
     * Paths of data lobby files to collect network names from
     */
    private List<Path> lobbyPaths = new ArrayList<>();


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
            pw.println("##Path of a working directory. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##Path of a data entry list file, must be set");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##########From here on, list up paths of data lobby folders containing stationXML files.");
            pw.println("##########  Up to " + MAX_NUM + " folders can be managed. Any index may be left blank.");
            for (int i = 1; i <= MAX_NUM; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " folder");
                pw.println("#lobbyPath" + i + " ");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public NetworkLookup(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));

        dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);

        for (int i = 1; i <= MAX_NUM; i++) {
            String lobbyKey = "lobbyPath" + i;
            if (property.containsKey(lobbyKey)) {
                lobbyPaths.add(property.parsePath(lobbyKey, null, true, workPath));
            }
        }
    }

   @Override
   public void run() throws IOException {
       // read input data entry file
       Set<DataEntry> entrySet = DataEntryListFile.readAsSet(dataEntryPath);
       Set<GlobalCMTID> eventSet = entrySet.stream().map(DataEntry::getEvent).collect(Collectors.toSet());

       // search for network names
       Set<String> networkDataLines = new HashSet<>();
       for (GlobalCMTID event : eventSet) {
           Set<DataEntry> correspondingEntrySet = entrySet.stream().filter(entry -> entry.getEvent().equals(event))
                   .collect(Collectors.toSet());
           Set<String> networks = correspondingEntrySet.stream().map(entry -> entry.getObserver().getNetwork())
                   .collect(Collectors.toSet());

           for (String network : networks) {
               String networkDescription = lookupDescription(network, event);
               if (networkDescription != null) {
                   networkDataLines.add(network + "|" + networkDescription);
               } else {
                   System.err.println("No stationXML files found for " + network + " " + event);
               }
           }
       }

       // output
       Path outputPath = workPath.resolve("network" + GadgetAid.getTemporaryString() + ".txt");
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
           pw.println("# network|description");
           networkDataLines.stream().sorted().forEach(pw::println);
       }
   }

    /**
     * Searches stationXML files for the given network and event until a network description is found.
     * @param network (String) Network ID to find description for
     * @param event (GlobalCMTID) The event for which the network has observed
     * @return (String) Description of network. If none is found, null.
     * @throws IOException
     */
    private String lookupDescription(String network, GlobalCMTID event) throws IOException {
       String description = null;

       // search event folder in lobbies
       for (Path lobbyPath : lobbyPaths) {
           Path eventPath = lobbyPath.resolve(event.toString());
           if (!Files.exists(eventPath)) {
               continue;
           }

           // search stationXML file of the specified network in eventFolder/station/
           List<Path> stationXMLPaths = Files.list(eventPath.resolve("station"))
                   .filter(p -> p.getFileName().toString().startsWith("station." + network + ".")).collect(Collectors.toList());
           if (stationXMLPaths.size() == 0) {
               continue;
           }

           // find network description in one of the xml files
           for (Path xmlPath : stationXMLPaths) {
               StationXmlFile stationInfo = new StationXmlFile(xmlPath);
               if (!stationInfo.readStationXml()) {
                   // if the read fails, skip the SAC file
                   // exception log is written inside the method
                   continue;
               }
               if (!StringUtils.isEmpty(stationInfo.getNetworkDescription())) {
                   description = stationInfo.getNetworkDescription();
                   break;
               }
           }
           if (description != null) break;
       }
       return description;
   }

}
