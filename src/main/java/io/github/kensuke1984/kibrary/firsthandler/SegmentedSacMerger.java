package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.util.FileAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * Merging of SAC files
 *
 * @author Kensuke Konishi
 */
class SegmentedSacMerger {

    /**
     * Path of event directory in which to merge files
     */
    private Path eventPath;

    /**
     * trash box for uneven files that are merged already マージに成功したファイルの行き先
     */
    private Path doneMergePath;

    /**
     * box for files that cannot be merged マージできなかったファイルの行き先
     */
    private Path unMergedPath;
    /**
     * SacFileNameのリスト
     */
    private SacFileName[] sacFileNameList;
    private Set<SegmentedSacGroup> sacGroupSet = new HashSet<>();

    private PrintWriter eliminatedWriter;

    /**
     *
     * @param eventPath (Path) segmented SAC をmergeする作業フォルダ
     * @param doneMergePath
     * @param unMergedPath
     * @throws IOException
     */
    SegmentedSacMerger(Path eventPath, Path doneMergePath, Path unMergedPath, PrintWriter eliminatedWriter) throws IOException {
        this.eventPath = eventPath;
        this.doneMergePath = doneMergePath;
        this.unMergedPath = unMergedPath;
        this.eliminatedWriter = eliminatedWriter;
        listUpSacFiles();
    }

    /**
     * Obtains all files with the suffix .SET under the working directory,
     * and groups up files by {@link #createGroups(SacFileName[])}.
     * @throws IOException
     */
    private void listUpSacFiles() throws IOException {
        // System.out.println("Listing up sac files");

        try (Stream<Path> sacFileStream = Files.list(eventPath)) {
            sacFileNameList =
                    sacFileStream.map(path -> path.getFileName().toString()).filter(path -> path.endsWith(".SET"))
                            .map(SacFileName::new).toArray(SacFileName[]::new);
        }

        // SacGroupをつくる
        createGroups(sacFileNameList);

    }

    /**
     * Groups up SAC files when the result of {@link SacFileName#isRelated(SacFileName)} is true.
     * @param names (SACFileName[]) List of SAC files to be sorted into groups.
     */
    private void createGroups(SacFileName[] names) {
        for (SacFileName name : names)
            // 既存のグループに振り分けられなかったら新しいグループを作る
            if (sacGroupSet.stream().noneMatch(group -> group.add(name))) sacGroupSet.add(new SegmentedSacGroup(eventPath, name));
    }

    /**
     * すべての {@link #sacGroupSet}をmergeする。失敗した場合、ファイルはゴミ箱へ。
     */
    void merge() {
        sacGroupSet.forEach(group -> {
            try {
                if (!group.merge()) {
                    GadgetAid.dualPrintln(eliminatedWriter, "!! failed to merge : " + eventPath.getFileName() + " - " + group.getRootSacFileName());
                    group.move(unMergedPath);
                }
            } catch (IOException e) {
                // suppress IOException here so that we can output the group.getRootSacFileName()
                GadgetAid.dualPrintln(eliminatedWriter, "!! failed to merge : " + eventPath.getFileName() + " - " + group.getRootSacFileName());
                e.printStackTrace();
                group.move(unMergedPath);
            }
        });
    }

    /**
     * {@link #eventPath}内の {@link #sacFileNameList}のすべてを {@link #doneMergePath}
     * にすてる
     */
    void move() {
        Arrays.stream(sacFileNameList).map(Object::toString).map(eventPath::resolve).filter(Files::exists)
                .forEach(path -> {
                    try {
                        FileAid.moveToDirectory(path, doneMergePath, true);
                    } catch (IOException e) {
                        // checked exceptions cannot be thrown here, so wrap it in unchecked exception
                        throw new UncheckedIOException(e);
                    }
                });

    }

}
