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
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.DataEntry;
import io.github.kensuke1984.kibrary.util.data.DataEntryListFile;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Class to create a list of networks that are used in certain sets of data.
 * To be used for creating a table to put in Supplementary Materials of a paper.
 * <p>
 * A {@link DataEntryListFile} shall be given as the set of data for input.
 * Paths of {@link DataLobby} folders must be set to look up the network descriptions in stationXML files.
 *
 * @author otsuru
 * @since 2023/1/10
 */
public class NetworkLookup extends Operation {

    private static final int MAX_NUM = 10;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
    /**
     * Whether to append date string at end of output file names.
     */
    private boolean appendFileDate;

    /**
     * Path of a data entry file.
     */
    private Path dataEntryPath;
    /**
     * Paths of data lobby folders to collect network names from.
     */
    private List<Path> lobbyPaths = new ArrayList<>();

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##(boolean) Whether to append date string at end of output file names. (true)");
            pw.println("#appendFileDate false");
            pw.println("##Path of a data entry list file, must be set.");
            pw.println("#dataEntryPath dataEntry.lst");
            pw.println("##########From here on, list up paths of data lobby folders containing stationXML files.");
            pw.println("##########  Up to " + MAX_NUM + " folders can be managed. Any index may be left blank.");
            for (int i = 1; i <= MAX_NUM; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " folder.");
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
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        appendFileDate = property.parseBoolean("appendFileDate", "true");

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

       // search for network names (descriptions) for each event
       // Note that when event is different, the same network code can correspond to different network names.
       System.err.println("Looking up networks ...");
       Set<Network> networkDataSet = new HashSet<>();
       int nEventsDone = 0;
       for (GlobalCMTID eventID : eventSet) {

           // collect all network codes existing for this event
           Set<String> networkCodes = entrySet.stream().filter(entry -> entry.getEvent().equals(eventID))
                   .map(entry -> entry.getObserver().getNetwork()).collect(Collectors.toSet());

           // search up information for each network code
           for (String networkCode : networkCodes) {
               Network networkInformation = lookupNetworkInformation(networkCode, eventID);
               if (networkInformation != null) {
                   networkDataSet.add(networkInformation);
               } else {
                   System.err.println("!! No description found for " + networkCode + " in " + eventID);
               }
           }

           nEventsDone++;
           if (nEventsDone % 100 == 0)
               System.err.print("\r " + MathAid.ceil(100.0 * nEventsDone / eventSet.size()) + "% of events done");
       }
       System.err.println("\r Finished handling all events.");

       // output
       Path outputPath = DatasetAid.generateOutputFilePath(workPath, "network", fileTag, appendFileDate, null, ".txt");
       System.err.println("Outputting in " + outputPath);
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
           pw.println("# network|description|DOI");
           networkDataSet.stream().sorted().forEach(pw::println);
       }
   }

    /**
     * Searches stationXML files for the given network and event until a network description and DOI is found.
     * Event folders for this event in all dataLobby folders will be searched for.
     * @param networkCode (String) Network ID to find information for.
     * @param event (GlobalCMTID) The event for which the network has observed.
     * @return ({@link Network}) Information of network. If description is not found, null.
     * @throws IOException
     */
    private Network lookupNetworkInformation(String networkCode, GlobalCMTID event) throws IOException {
       String description = null;
       String doi = null;

       // search event folder in all dataLobby folders
       for (Path lobbyPath : lobbyPaths) {
           Path eventPath = lobbyPath.resolve(event.toString());
           if (!Files.exists(eventPath)) {
               continue;
           }

           // search stationXML file of the specified network in eventFolder/station/
           List<Path> stationXMLPaths;
           // CAUTION: Files.list() must be in try-with-resources.
           try (Stream<Path> stream = Files.list(eventPath.resolve("station"))) {
               stationXMLPaths = stream.filter(p -> p.getFileName().toString().startsWith("station." + networkCode + "."))
                       .collect(Collectors.toList());
           }
           if (stationXMLPaths.size() == 0) {
               continue;
           }

           // find network description and DOI in one of the xml files
           for (Path xmlPath : stationXMLPaths) {
               StationXmlFile stationInfo = new StationXmlFile(xmlPath);
               if (!stationInfo.readStationXml()) {
                   // if the read fails, skip the SAC file
                   // exception log is written inside the method
                   continue;
               }
               if (!StringUtils.isEmpty(stationInfo.getNetworkDescription())) {
                   description = stationInfo.getNetworkDescription();
               }
               if (!StringUtils.isEmpty(stationInfo.getDOI())) {
                   doi = stationInfo.getDOI();
               }

               // stop searching when both description and DOI are found
               if (description != null && doi != null) break;
           }
           if (description != null && doi != null) break;
       }

       // return network information; null when description is unknown
       if (description != null) return new Network(networkCode, description, doi);
       else return null;
   }

    /**
     * A class to store information of network code, description, and DOI.
     * This class is needed because Map<>() does not work;
     * there may be networks with same code but different description,
     * or those with different codes but same description.
     * {@link #equals(Object)} is set to return true when the code, description, and DOI are the same.
     * {@link #compareTo(Network)} is set to sort by code, then by description.
     */
    private class Network implements Comparable<Network> {
        private String code;
        private String description;
        private String doi;

        public Network(String code, String description, String doi) {
            this.code = code;
            this.description = description;
            this.doi = doi;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((code == null) ? 0 : code.hashCode());
            result = prime * result + ((description == null) ? 0 : description.hashCode());
            result = prime * result + ((doi == null) ? 0 : doi.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Network other = (Network) obj;

            if (code == null) {
                if (other.code != null)
                    return false;
            } else if (!code.equals(other.code))
                return false;

            if (description == null) {
                if (other.description != null)
                    return false;
            } else if (!description.equals(other.description))
                return false;

            if (doi == null) {
                if (other.doi != null)
                    return false;
            } else if (!doi.equals(other.doi))
                return false;

            return true;
        }

        @Override
        public int compareTo(Network o) {
            int tmp = code.compareTo(o.code);
            if (tmp != 0) return tmp;
            return description.compareTo(o.description);
        }

        @Override
        public String toString() {
            return code + "|" + description + "|" + (doi != null ? doi : "") ;
        }
    }
}
