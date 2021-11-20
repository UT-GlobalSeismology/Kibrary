package io.github.kensuke1984.kibrary.dsmsetup;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;

/**
 * Class for creating input files for TIPSV and TISH
 *
 */
public class SyntheticDSMInputFile extends DSMInputHeader {

    protected final PolynomialStructure structure;

    protected final String output;

    /**
     * <b>unmodifiable</b>
     */
    protected final Set<Observer> observers;

    protected final GlobalCMTAccess event;

    /**
     * @param structure of velocity
     * @param event     {@link GlobalCMTID}
     * @param observers observer information
     * @param outputDir name of outputDir (relative PATH)
     * @param tlen      TLEN[s]
     * @param np        NP
     */
    public SyntheticDSMInputFile(PolynomialStructure structure, GlobalCMTAccess event, Set<Observer> observers, String outputDir,
                            double tlen, int np) {
        super(tlen, np);
        this.structure = structure;
        this.event = event;
        this.observers = Collections.unmodifiableSet(new HashSet<>(observers));
        this.output = outputDir;
    }

    /**
     * Creates a file for tipsv
     *
     * @param psvPath Path of an write file
     * @param options for write
     * @throws IOException if an I/O error occurs
     * @author Kensuke Konishi
     * @author anselme change station string to NAME_NETWORK
     */
    public void writePSV(Path psvPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(psvPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toPSVlines();
            Arrays.stream(structurePart).forEach(pw::println);

            FullPosition eventLocation = event.getCmtLocation();
            // source
            pw.println("c parameter for the source");
            pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude() +
                    " r0(km), lat, lon (deg)");
            pw.println(Arrays.stream(event.getCmt().getDSMmt()).mapToObj(Double::toString)
                    .collect(Collectors.joining(" ")) + " Moment Tensor (1.e25 dyne cm)");

            // station
            pw.println("c parameter for the station");
            pw.println("c the number of stations");
            pw.println(observers.size() + " nsta");
            pw.println("c latitude longitude (deg)");

            observers.stream().sorted().map(Observer::getPosition)
                    .forEach(p -> pw.println(p.getLatitude() + " " + p.getLongitude()));

            // write
            pw.println("c parameter for the write file");
            observers.stream().sorted()
                    .forEach(s -> pw.println(output + "/" + SPCFileName.generate(s, event.getGlobalCMTID(), "PSV")));
            pw.println("end");

        }
    }

    /**
     * Creates a file for tish
     *
     * @param outPath write path
     * @param options for write
     * @throws IOException if an I/O error occurs
     * @author Kensuke Konishi
     * @author anselme change station string to NAME_NETWORK
     */
    public void writeSH(Path outPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            // header
            String[] header = outputDSMHeader();
            Arrays.stream(header).forEach(pw::println);

            // structure
            String[] structurePart = structure.toSHlines();
            Arrays.stream(structurePart).forEach(pw::println);
            FullPosition eventLocation = event.getCmtLocation();
            // source
            pw.println("c parameter for the source");
            pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude() +
                    " r0(km), lat, lon (deg)");
            pw.println(Arrays.stream(event.getCmt().getDSMmt()).mapToObj(Double::toString)
                    .collect(Collectors.joining(" ")) + " Moment Tensor (1.e25 dyne cm)");

            // station
            pw.println("c parameter for the station");
            pw.println("c the number of stations");
            pw.println(observers.size() + " nsta");
            pw.println("c latitude longitude (deg)");
            observers.stream().sorted().map(Observer::getPosition)
                    .forEach(p -> pw.println(p.getLatitude() + " " + p.getLongitude()));

            // write
            pw.println("c parameter for the write file");
            observers.stream().sorted()
                    .forEach(s -> pw.println(output + "/" + SPCFileName.generate(s, event.getGlobalCMTID(), "SH")));
            pw.println("end");
        }
    }

    public SyntheticDSMInputFile replaceStructure(PolynomialStructure structure) {
        return new SyntheticDSMInputFile(structure, event, observers, output, getTlen(), getNp());
    }

    public GlobalCMTAccess getGlobalCMTData() {
        return event;
    }

}
