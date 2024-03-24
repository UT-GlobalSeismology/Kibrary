package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reader for files which contains "#", "!" (and "c", "C", if alphabets should not exist) for comment lines.
 *
 * @author Kensuke Konishi
 * @since version 0.0.2.3
 * @version 2022/2/11 moved from dsmsetup to util
 */
public class InformationFileReader {
    /**
     * Indicators for comment lines, for files with only numbers as information.
     */
    private static final char[] COMMENT_OUT_FLAG_WITH_C = {'c', 'C', '!', '#'};
    /**
     * Indicators for comment lines, for files with alphabet information.
     */
    private static final char[] COMMENT_OUT_FLAG_NO_C = {'!', '#'};

    /**
     * Indicators for comment lines.
     */
    private final char[] commentOutFlag;
    /**
     * Lines in the given file.
     */
    private final List<String> lines;
    /**
     * The total number of lines.
     */
    private final int linesNum;
    /**
     * The number of lines already read.
     */
    private int readlineNum = 0;

    /**
     * Reads in an information file.
     * @param informationPath (Path) file to read.
     * @param includesAlphabet (boolean) whether the information part includes alphabets.
     *      If false, "c" and "C" will be regarded as comments.
     * @throws IOException
     */
    public InformationFileReader(Path informationPath, boolean includesAlphabet) throws IOException {
        if (includesAlphabet) commentOutFlag = COMMENT_OUT_FLAG_NO_C;
        else commentOutFlag = COMMENT_OUT_FLAG_WITH_C;

        lines = Files.readAllLines(informationPath).stream().filter(line -> !isComment(line)).map(String::trim).collect(Collectors.toList());
        linesNum = lines.size();
    }

    public InformationFileReader(List<String> lines, boolean includesAlphabet) {
        if (includesAlphabet) commentOutFlag = COMMENT_OUT_FLAG_NO_C;
        else commentOutFlag = COMMENT_OUT_FLAG_WITH_C;

        this.lines = lines.stream().filter(line -> !isComment(line)).map(String::trim).collect(Collectors.toList());
        linesNum = this.lines.size();
    }

    /**
     * Read the next non-comment line, and return it after trimming.
     * Comment lines ("#", "!" (and "c", "C", if alphabets should not exist)) and blank lines will be skipped.
     * @return (String) The line just after the line already read. Returns null if all lines are already read.
     */
    public String next() {
        if (readlineNum == linesNum) return null;
        String line;
        for (; ; )
            if (!isComment(line = lines.get(readlineNum++))) return line;
    }

    /**
     * Check whether a line is a comment (or blank) line.
     * @param line (String) Line to check.
     * @return (boolean) Whether the input line is a comment line.
     */
    private boolean isComment(String line) {
        if ((line = line.trim()).isEmpty()) return true;
        for (char flag : commentOutFlag)
            if (line.charAt(0) == flag) return true;
        return false;
    }

    /**
     * Reset the number of lines already read.
     */
    public void reset() {
        readlineNum = 0;
    }

    /**
     * Check whether there are lines left to read.
     * @return (boolean) Whether there are lines left to read.
     */
    public boolean hasNext() {
        return readlineNum < linesNum;
    }

    /**
     * Get the number of non-comment lines in the file.
     * @return (int) The number of non-comment lines in the file.
     */
    public int getNumLines() {
        return linesNum;
    }

    /**
     * Get all non-comment lines. The spaces at the beginning and end of each line are trimmed.
     * @return (String[]) Non-comment lines.
     */
    public String[] getNonCommentLines() {
        return lines.toArray(new String[lines.size()]);
    }

}
