package io.github.kensuke1984.kibrary.util.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.apache.commons.io.input.CloseShieldInputStream;

import io.github.kensuke1984.kibrary.util.DatasetUtils;
import io.github.kensuke1984.kibrary.util.GadgetUtils;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

/**
 * File containing information of observers.
 * <p>
 * Each line: station code, network code, latitude, longitude.
 *
 * @author Kensuke Konishi
 * @version 0.2.0.4
 */
public final class ObserverInformationFile {

    private ObserverInformationFile() {
    }

    /**
     * @param observerSet Set of observers
     * @param outPath     of write file
     * @param options     for write
     * @throws IOException if an I/O error occurs
     */
    public static void write(Set<Observer> observerSet, Path outPath, OpenOption... options) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            pw.println("# station network latitude longitude");
            observerSet.stream().sorted().forEach(observer -> {
                pw.println(observer.getPaddedInfoString());
            });
        }
    }

    /**
     * @param infoPath of station information file
     * @return (<b>unmodifiable</b>) Set of stations
     * @throws IOException if an I/O error occurs
     */
    public static Set<Observer> read(Path infoPath) throws IOException {
        Set<Observer> observerSet = new HashSet<>();
        try (BufferedReader br = Files.newBufferedReader(infoPath)) {
            br.lines().map(String::trim).filter(line -> !line.startsWith("#")).forEach(line -> {
                String[] parts = line.split("\\s+");
                HorizontalPosition hp = new HorizontalPosition(Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]));
                Observer observer = new Observer(parts[0], parts[1], hp);
                if (!observerSet.add(observer))
                    throw new RuntimeException("There is duplication of " + observer + " in " + infoPath + ".");
            });
        }

        // If there are observers with same name and different position, write them in standard output. TODO: should become unneeded?
        if (observerSet.size() != observerSet.stream().map(Observer::toString).distinct().count()){
            System.err.println("CAUTION!! Observers with same station and network but different positions detected!");
            Map<String, List<Observer>> nameToObserver = new HashMap<>();
            observerSet.forEach(sta -> {
                if (nameToObserver.containsKey(sta.toString())) {
                    List<Observer> tmp = nameToObserver.get(sta.toString());
                    tmp.add(sta);
                    nameToObserver.put(sta.toString(), tmp);
                }
                else {
                    List<Observer> tmp = new ArrayList<>();
                    tmp.add(sta);
                    nameToObserver.put(sta.toString(), tmp);
                }
            });
            nameToObserver.forEach((name, obs) -> {
                if (obs.size() > 1) {
                    obs.stream().forEach(s -> System.out.println(s + " " + s.getPosition()));
                }
            });
        }

        return Collections.unmodifiableSet(observerSet);
    }

    /**
     * Reads observer information from SAC files in event directories under a working directory,
     * and creates an observer information file under the working directory.
     *
     * @param args [working directory to collect observers from]
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        if (0 < args.length) {
            String path = args[0];
            if (!path.startsWith("/"))
                path = System.getProperty("user.dir") + "/" + path;
            Path f = Paths.get(path);
            if (Files.exists(f) && Files.isDirectory(f))
                createObserverInformationFile(f);
            else
                System.err.println(f + " does not exist or is not a directory.");
        } else {
            Path workPath;
            String path = "";
            do {
                try {
                    path = JOptionPane.showInputDialog("Working folder?", path);
                } catch (Exception e) {
                    System.err.println("Working folder?");
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(CloseShieldInputStream.wrap(System.in)))) {
                        path = br.readLine().trim();
                        if (!path.startsWith("/"))
                            path = System.getProperty("user.dir") + "/" + path;
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        throw new RuntimeException();
                    }
                }
                if (path == null || path.isEmpty()) return;
                workPath = Paths.get(path);
                if (!Files.isDirectory(workPath)) continue;
            } while (!Files.exists(workPath) || !Files.isDirectory(workPath));
            createObserverInformationFile(workPath);
        }

    }

    /**
     * ワーキングディレクトリ下のイベントフォルダ群からステーション情報を抽出して書き込む。
     *
     * @param workPath under which this looks for event folders and stations under
     *                 the folders
     * @param options  for write
     * @throws IOException if an I/O error occurs
     */
    public static void createObserverInformationFile(Path workPath, OpenOption... options) throws IOException {
        Path outPath = workPath.resolve("observer" + GadgetUtils.getTemporaryString() + ".inf");

        Set<SACFileName> sacNameSet = DatasetUtils.sacFileNameSet(workPath);
        Set<Observer> observerSet = sacNameSet.stream().filter(sacname -> sacname.getComponent().equals(SACComponent.T)).map(sacName -> {
            try {
                return sacName.readHeader();
            } catch (Exception e) {
                System.err.println(sacName + " is an invalid SAC file.");
                return null;
            }
        }).filter(Objects::nonNull).map(Observer::of).collect(Collectors.toSet());

        // If there are observers with same name and different position, write them in standard output. TODO: should become unneeded?
        if (observerSet.size() != observerSet.stream().map(Observer::toString).distinct().count()) {
            System.err.println("CAUTION!! Observers with same station and network but different positions detected!");
            Map<String, List<Observer>> nameToObserver = new HashMap<>();
            observerSet.forEach(sta -> {
                if (nameToObserver.containsKey(sta.toString())) {
                    List<Observer> tmp = nameToObserver.get(sta.toString());
                    tmp.add(sta);
                    nameToObserver.put(sta.toString(), tmp);
                }
                else {
                    List<Observer> tmp = new ArrayList<>();
                    tmp.add(sta);
                    nameToObserver.put(sta.toString(), tmp);
                }
            });
            nameToObserver.forEach((name, obs) -> {
                if (obs.size() > 1) {
                    obs.stream().forEach(s -> System.out.println(s + " " + s.getPosition()));
                }
            });
        }

        write(observerSet, outPath, options);
    }

}
