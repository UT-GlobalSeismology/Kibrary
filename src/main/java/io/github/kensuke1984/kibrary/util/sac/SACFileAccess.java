package io.github.kensuke1984.kibrary.util.sac;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * SAC file (SAC: Seismic analysis code). Binary format.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @see <a href=http://ds.iris.edu/ds/nodes/dmc/forms/sac/>SAC</a>
 */
public interface SACFileAccess extends SACHeaderAccess {

    /**
     * @param sacFileName name of an write file
     * @param options     open options for outputting
     * @throws IOException if an I/O error occurs
     */
    default void writeSAC(SACFileName sacFileName, OpenOption... options) throws IOException {
        writeSAC(sacFileName.toPath(), options);
    }

    /**
     * @param outPath {@link Path} to write this SacFile
     * @param options {@link OpenOption} for outputting
     * @throws IOException if an I/O error occurs
     */
    default void writeSAC(Path outPath, OpenOption... options) throws IOException {
        try (SACOutputStream stream = new SACOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outPath, options)))) {
            stream.writeSACDouble(getValue(SACHeaderEnum.DELTA)); // 0
            stream.writeSACDouble(getValue(SACHeaderEnum.DEPMIN)); // 1
            stream.writeSACDouble(getValue(SACHeaderEnum.DEPMAX)); // 2
            stream.writeSACDouble(getValue(SACHeaderEnum.SCALE)); // 3
            stream.writeSACDouble(getValue(SACHeaderEnum.ODELTA)); // 4
            stream.writeSACDouble(getValue(SACHeaderEnum.B)); // 5
            stream.writeSACDouble(getValue(SACHeaderEnum.E)); // 6
            stream.writeSACDouble(getValue(SACHeaderEnum.O)); // 7
            stream.writeSACDouble(getValue(SACHeaderEnum.A)); // 8
            stream.writeSACDouble(getValue(SACHeaderEnum.num9)); // 9
            stream.writeSACDouble(getValue(SACHeaderEnum.T0)); // 10
            stream.writeSACDouble(getValue(SACHeaderEnum.T1)); // 11
            stream.writeSACDouble(getValue(SACHeaderEnum.T2)); // 12
            stream.writeSACDouble(getValue(SACHeaderEnum.T3)); // 13
            stream.writeSACDouble(getValue(SACHeaderEnum.T4)); // 14
            stream.writeSACDouble(getValue(SACHeaderEnum.T5)); // 15
            stream.writeSACDouble(getValue(SACHeaderEnum.T6)); // 16
            stream.writeSACDouble(getValue(SACHeaderEnum.T7)); // 17
            stream.writeSACDouble(getValue(SACHeaderEnum.T8)); // 18
            stream.writeSACDouble(getValue(SACHeaderEnum.T9)); // 19
            stream.writeSACDouble(getValue(SACHeaderEnum.F)); // 20
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP0)); // 21
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP1)); // 22
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP2)); // 23
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP3)); // 24
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP4)); // 25
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP5)); // 26
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP6)); // 27
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP7)); // 28
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP8)); // 29
            stream.writeSACDouble(getValue(SACHeaderEnum.RESP9)); // 30
            stream.writeSACDouble(getValue(SACHeaderEnum.STLA)); // 31
            stream.writeSACDouble(getValue(SACHeaderEnum.STLO)); // 32
            stream.writeSACDouble(getValue(SACHeaderEnum.STEL)); // 33
            stream.writeSACDouble(getValue(SACHeaderEnum.STDP)); // 34
            stream.writeSACDouble(getValue(SACHeaderEnum.EVLA)); // 35
            stream.writeSACDouble(getValue(SACHeaderEnum.EVLO)); // 36
            stream.writeSACDouble(getValue(SACHeaderEnum.EVEL)); // 37
            stream.writeSACDouble(getValue(SACHeaderEnum.EVDP)); // 38
            stream.writeSACDouble(getValue(SACHeaderEnum.MAG)); // 39
            stream.writeSACDouble(getValue(SACHeaderEnum.USER0)); // 40
            stream.writeSACDouble(getValue(SACHeaderEnum.USER1)); // 41
            stream.writeSACDouble(getValue(SACHeaderEnum.USER2)); // 42
            stream.writeSACDouble(getValue(SACHeaderEnum.USER3)); // 43
            stream.writeSACDouble(getValue(SACHeaderEnum.USER4)); // 44
            stream.writeSACDouble(getValue(SACHeaderEnum.USER5)); // 45
            stream.writeSACDouble(getValue(SACHeaderEnum.USER6)); // 46
            stream.writeSACDouble(getValue(SACHeaderEnum.USER7)); // 47
            stream.writeSACDouble(getValue(SACHeaderEnum.USER8)); // 48
            stream.writeSACDouble(getValue(SACHeaderEnum.USER9)); // 49
            stream.writeSACDouble(getValue(SACHeaderEnum.DIST)); // 50
            stream.writeSACDouble(getValue(SACHeaderEnum.AZ)); // 51
            stream.writeSACDouble(getValue(SACHeaderEnum.BAZ)); // 52
            stream.writeSACDouble(getValue(SACHeaderEnum.GCARC)); // 53
            stream.writeSACDouble(getValue(SACHeaderEnum.num54)); // 54
            stream.writeSACDouble(getValue(SACHeaderEnum.num55)); // 55
            stream.writeSACDouble(getValue(SACHeaderEnum.DEPMEN)); // 56
            stream.writeSACDouble(getValue(SACHeaderEnum.CMPAZ)); // 57
            stream.writeSACDouble(getValue(SACHeaderEnum.CMPINC)); // 58
            stream.writeSACDouble(getValue(SACHeaderEnum.XMINIMUM)); // 59
            stream.writeSACDouble(getValue(SACHeaderEnum.XMAXIMUM)); // 60
            stream.writeSACDouble(getValue(SACHeaderEnum.YMINIMUM)); // 61
            stream.writeSACDouble(getValue(SACHeaderEnum.YMAXIMUM)); // 62
            stream.writeSACDouble(getValue(SACHeaderEnum.num63)); // 63
            stream.writeSACDouble(getValue(SACHeaderEnum.num64)); // 64
            stream.writeSACDouble(getValue(SACHeaderEnum.num65)); // 65
            stream.writeSACDouble(getValue(SACHeaderEnum.num66)); // 66
            stream.writeSACDouble(getValue(SACHeaderEnum.num67)); // 67
            stream.writeSACDouble(getValue(SACHeaderEnum.num68)); // 68
            stream.writeSACDouble(getValue(SACHeaderEnum.num69)); // 69
            // int
            stream.writeSACInt(getInt(SACHeaderEnum.NZYEAR)); // 70
            stream.writeSACInt(getInt(SACHeaderEnum.NZJDAY)); // 71
            stream.writeSACInt(getInt(SACHeaderEnum.NZHOUR)); // 72
            stream.writeSACInt(getInt(SACHeaderEnum.NZMIN)); // 73
            stream.writeSACInt(getInt(SACHeaderEnum.NZSEC)); // 74
            stream.writeSACInt(getInt(SACHeaderEnum.NZMSEC)); // 75
            stream.writeSACInt(getInt(SACHeaderEnum.NVHDR)); // 76
            stream.writeSACInt(getInt(SACHeaderEnum.NORID)); // 77
            stream.writeSACInt(getInt(SACHeaderEnum.NEVID)); // 78
            stream.writeSACInt(getInt(SACHeaderEnum.NPTS)); // 79
            stream.writeSACInt(getInt(SACHeaderEnum.num80)); // 80
            stream.writeSACInt(getInt(SACHeaderEnum.NWFID)); // 81
            stream.writeSACInt(getInt(SACHeaderEnum.NXSIZE)); // 82
            stream.writeSACInt(getInt(SACHeaderEnum.NYSIZE)); // 83
            stream.writeSACInt(getInt(SACHeaderEnum.num84)); // 84
            // enumerized
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IFTYPE)); // 85
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IDEP)); // 86
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IZTYPE)); // 87
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num88)); // 88
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IINST)); // 89
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.ISTREG)); // 90
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IEVREG)); // 91
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IEVTYP)); // 92
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IQUAL)); // 93
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.ISYNTH)); // 94
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IMAGTYP)); // 95
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.IMAGSRC)); // 96
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num97)); // 97
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num98)); // 98
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num99)); // 99
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num100)); // 100
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num101)); // 101
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num102)); // 102
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num103)); // 103
            stream.writeSACInt(getSACEnumerated(SACHeaderEnum.num104)); // 104
            // / BOOLEAN
            stream.writeSACBoolean(getBoolean(SACHeaderEnum.LEVEN)); // 105
            stream.writeSACBoolean(getBoolean(SACHeaderEnum.LPSPOL)); // 106
            stream.writeSACBoolean(getBoolean(SACHeaderEnum.LOVROK)); // 107
            stream.writeSACBoolean(getBoolean(SACHeaderEnum.LCALDA)); // 108
            stream.writeSACBoolean(getBoolean(SACHeaderEnum.num109)); // 109
            // / String
            stream.writeSACString(getSACString(SACHeaderEnum.KSTNM), 8); // 110-111
            stream.writeSACString(getSACString(SACHeaderEnum.KEVNM), 16); // 112-115
            stream.writeSACString(getSACString(SACHeaderEnum.KHOLE), 8); // 116-117
            stream.writeSACString(getSACString(SACHeaderEnum.KO), 8); // 118-119
            stream.writeSACString(getSACString(SACHeaderEnum.KA), 8); // 120-121
            stream.writeSACString(getSACString(SACHeaderEnum.KT0), 8); // 122-123
            stream.writeSACString(getSACString(SACHeaderEnum.KT1), 8); // 124-125
            stream.writeSACString(getSACString(SACHeaderEnum.KT2), 8); // 126-127
            stream.writeSACString(getSACString(SACHeaderEnum.KT3), 8); // 128-129
            stream.writeSACString(getSACString(SACHeaderEnum.KT4), 8); // 130-131
            stream.writeSACString(getSACString(SACHeaderEnum.KT5), 8); // 132-133
            stream.writeSACString(getSACString(SACHeaderEnum.KT6), 8); // 134-135
            stream.writeSACString(getSACString(SACHeaderEnum.KT7), 8); // 136-137
            stream.writeSACString(getSACString(SACHeaderEnum.KT8), 8); // 138-139
            stream.writeSACString(getSACString(SACHeaderEnum.KT9), 8); // 140-141
            stream.writeSACString(getSACString(SACHeaderEnum.KF), 8); // 142-143
            stream.writeSACString(getSACString(SACHeaderEnum.KUSER0), 8); // 144-145
            stream.writeSACString(getSACString(SACHeaderEnum.KUSER1), 8); // 146-147
            stream.writeSACString(getSACString(SACHeaderEnum.KUSER2), 8); // 148-149
            stream.writeSACString(getSACString(SACHeaderEnum.KCMPNM), 8); // 150-151
            stream.writeSACString(getSACString(SACHeaderEnum.KNETWK), 8); // 152-153
            stream.writeSACString(getSACString(SACHeaderEnum.KDATRD), 8); // 154-155
            stream.writeSACString(getSACString(SACHeaderEnum.KINST), 8); // 156-157
            double[] waveData = getData();
            for (double data : waveData) stream.writeSACDouble(data);
        }
    }

    /**
     * DELTA will be rounded off to 4 decimal values
     * <p>
     * This creation considers about the value B. If B is not integer multiple
     * of DELTA. Then B1 (DELTA*N) &lt; B &lt; B2 (DELTA*(N+1)) B1 is used
     * instead. (int)(B/delta)*delta
     *
     * @return {@link Trace} of time and waveform
     */
    default Trace createTrace() {
        double delta = getValue(SACHeaderEnum.DELTA);
        double b = (int) (getValue(SACHeaderEnum.B) / delta) * delta;
        int npts = getInt(SACHeaderEnum.NPTS);
        double[] timeAxis =
                IntStream.range(0, npts).parallel().mapToDouble(i -> Precision.round(i * delta + b, 4)).toArray();
        return new Trace(timeAxis, getData());
    }

    @Override
    default SACFileAccess withEventLocation(FullPosition eventLocation) {
        return (SACFileAccess) SACHeaderAccess.super.withEventLocation(eventLocation);
    }

    @Override
    default SACFileAccess withEventTime(LocalDateTime eventDateTime) {
        return (SACFileAccess) SACHeaderAccess.super.withEventTime(eventDateTime);
    }

    @Override
    default SACFileAccess withObserver(Observer observer) {
        return (SACFileAccess) SACHeaderAccess.super.withObserver(observer);
    }

    @Override
    default SACFileAccess withTimeMarker(SACHeaderEnum marker, double time) {
        return (SACFileAccess) SACHeaderAccess.super.withTimeMarker(marker, time);
    }

    @Override
    SACFileAccess withBoolean(SACHeaderEnum sacHeaderEnum, boolean bool);

    @Override
    SACFileAccess withInt(SACHeaderEnum sacHeaderEnum, int value);

    @Override
    SACFileAccess withSACEnumerated(SACHeaderEnum sacHeaderEnum, int value);

    @Override
    SACFileAccess withValue(SACHeaderEnum sacHeaderEnum, double value);

    @Override
    SACFileAccess withSACString(SACHeaderEnum sacHeaderEnum, String string);

    SACFileAccess applyButterworthFilter(ButterworthFilter filter);

    double[] getData();

    /**
     * DEEP copy input sacData on the sacData of this.
     *
     * @param waveData must have the same length as NPTS in this SacFile
     * @return {@link SACFileAccess} with the sacData
     * @throws IllegalStateException if NPTS is invalid or not set.
     */
    SACFileAccess setSACData(double[] waveData);


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * SAC files in binary format will be read and output in ascii format.
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
        // input
        options.addOption(Option.builder("s").longOpt("sac").hasArg().argName("sacFile")
                .desc("Path of input SAC file.").build());
        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Path of output file. When not set, output is same as input with extension changed to '.txt'.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        // set input file path
        SACFileName sacName = new SACFileName(cmdLine.getOptionValue("s"));
        SACFileAccess sacData = sacName.read();
        Trace sacTrace = sacData.createTrace();

        // set output
        Path outputPath;
        if (cmdLine.hasOption("o")) {
            outputPath = Paths.get(cmdLine.getOptionValue("o"));
        } else {
            // set the output file name the same as the input, but with extension changed to "txt"
            String fileName = sacName.toPath().getFileName().toString();
            outputPath = Paths.get(fileName + ".txt");
        }

        // output
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < sacTrace.getLength(); i++) {
                pw.println(sacTrace.getXAt(i) + " " + sacTrace.getYAt(i));
            }
        }
    }
}
