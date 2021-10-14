package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import io.github.kensuke1984.kibrary.util.Utilities;

/**
 * Merging of SAC files
 */
class SegmentedSacMerger {

    /**
     * 作業フォルダ
     */
    private Path workPath;

    /**
     * trash box for uneven files that are merged already マージに成功したファイルの行き先
     */
    private Path unevenBoxPath;

    /**
     * box for files that cannot be merged マージできなかったファイルの行き先
     */
    private Path notMergedBoxPath;
    /**
     * SacFileNameのリスト
     */
    private SACFileName[] sacFileNameList;
    private Set<SacGroup> sacGroupSet = new HashSet<>();

    /**
     * Uneven Sacをmergeする作業フォルダ
     *
     * @param workPath
     */
    SegmentedSacMerger(Path workPath, Path doneMergePath, Path unMergedPath) throws IOException {
        this.workPath = workPath;
        unevenBoxPath = doneMergePath;
        notMergedBoxPath = unMergedPath;
        listUpSacFiles();
    }

    /**
     * Obtains all files with the suffix .SET under the working directory,
     * and groups up files by {@link #createGroups(SACFileName[])}.
     * @throws IOException
     */
    private void listUpSacFiles() throws IOException {
        // System.out.println("Listing up sac files");

        try (Stream<Path> sacFileStream = Files.list(workPath)) {
            sacFileNameList =
                    sacFileStream.map(path -> path.getFileName().toString()).filter(path -> path.endsWith(".SET"))
                            .map(SACFileName::new).toArray(SACFileName[]::new);
        }

        // SacGroupをつくる
        createGroups(sacFileNameList);

    }

    /**
     * Groups up SAC files when the result of {@link SACFileName#isRelated(SACFileName)} is true.
     * @param names (SACFileName[]) List of SAC files to be sorted into groups.
     */
    private void createGroups(SACFileName[] names) {
        for (SACFileName name : names)
            // 既存のグループに振り分けられなかったら新しいグループを作る
            if (sacGroupSet.stream().noneMatch(group -> group.add(name))) sacGroupSet.add(new SacGroup(workPath, name));
    }

    /**
     * すべての {@link #sacGroupSet}をmergeする。失敗した場合、ファイルはゴミ箱へ。
     */
    void merge() {
        sacGroupSet.forEach(group -> {
            try {
                if (!group.merge()) {
                    System.err.println("!! failed to merge : " + workPath.getFileName() + " - " + group.getRootSacFileName());
                    group.move(notMergedBoxPath);
                }
            } catch (Exception e) {
                System.err.println("!! failed to merge : " + workPath.getFileName() + " - " + group.getRootSacFileName());
                group.move(notMergedBoxPath);
            }
        });
    }

    /**
     * {@link #workPath}内の {@link #sacFileNameList}のすべてを {@link #unevenBoxPath}
     * にすてる
     */
    void move() {
        Arrays.stream(sacFileNameList).map(Object::toString).map(workPath::resolve).filter(Files::exists)
                .forEach(path -> {
                    try {
                        Utilities.moveToDirectory(path, unevenBoxPath, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

    }

}
