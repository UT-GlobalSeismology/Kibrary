package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * indicators for comment lines, for files with only numbers as information
     */
    private static final char[] COMMENT_OUT_FLAG_WITH_C = {'c', 'C', '!', '#'};
    /**
     * indicators for comment lines, for files with alphabet information
     */
    private static final char[] COMMENT_OUT_FLAG_NO_C = {'!', '#'};

    /**
     * indicators for comment lines
     */
    private final char[] commentOutFlag;
    /**
     * lines in the given file
     */
    private final List<String> lines;
    /**
     * the number of total lines
     */
    private final int linesNum;
    /**
     * the number of lines already read
     */
    private int readlineNum = 0;

    /**
     * @param args [information file name]
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 1) {
            InformationFileReader ifr = new InformationFileReader(Paths.get(args[0]), true);
            String line;
            while (null != (line = ifr.next())) System.out.println(line);
        }
    }

    /**
     * Reads in an information file.
     * @param informationPath (Path) file to read
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
     *
     * @return the next line after the line already read. Returns null if all lines are already read.
     */
    public String next() {
        if (readlineNum == linesNum) return null;
        String line;
        for (; ; )
            if (!isComment(line = lines.get(readlineNum++))) return line;
    }

    /**
     * Checks whether a line is a comment (or blank) line.
     *
     * @param line to check
     * @return if the input line is comment line or not
     */
    private boolean isComment(String line) {
        if ((line = line.trim()).isEmpty()) return true;
        for (char flag : commentOutFlag)
            if (line.charAt(0) == flag) return true;
        return false;
    }

    /**
     * reset the number of lines already read
     */
    public void reset() {
        readlineNum = 0;
    }

    /**
     * Whether there are lines left to read
     *
     * @return if there are lines to read
     */
    public boolean hasNext() {
        return readlineNum < linesNum;
    }

    /**
     * @return (int) the number of non-comment lines in the file
     */
    public int getNumLines() {
        return linesNum;
    }

    /**
     * Get all non-comment lines. The spaces at the beginning and end of each line are trimmed.
     * @return String[] made of non comment lines
     */
    public String[] getNonCommentLines() {
        return lines.toArray(new String[lines.size()]);
    }

}
