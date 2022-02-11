package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reader for files which contains c # ! etc for comment lines.
 *
 * @author Kensuke Konishi
 * @since version 0.0.2.3
 * @version 2022/2/11 moved from dsmsetup to util
 */
public class InformationFileReader {
    /**
     * indicators for comment lines
     */
    private static final char[] COMMENT_OUT_FLAG = {'c', 'C', '!', '#'};

    /**
     * lines in the given file
     */
    private List<String> lines;
    /**
     * the number of total lines
     */
    private int linesNum;
    /**
     * the number of lines already read
     */
    private int readlineNum;

    /**
     * @param args [information file name]
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 1) {
            InformationFileReader ifr = new InformationFileReader(Paths.get(args[0]));
            String line;
            while (null != (line = ifr.next())) System.out.println(line);
        }
    }

    public InformationFileReader(Path informationPath) throws IOException {
        lines = Files.readAllLines(informationPath).stream().filter(line -> !isComment(line)).map(String::trim).collect(Collectors.toList());
        linesNum = lines.size();
    }

    public InformationFileReader(List<String> lines) {
        this.lines = lines.stream().filter(line -> !isComment(line)).map(String::trim).collect(Collectors.toList());
        linesNum = this.lines.size();
    }

    /**
     * Read the next line, and return it after trimming.
     * Comment lines (c C # ! etc.) and blank lines will be skipped.
     *
     * @return the next line after the line already read. Returns null if all lines are already read.
     */
    public String next() {
        if (readlineNum == linesNum) return null;
        String line;
        for (; ; )
            if (!isComment(line = lines.get(readlineNum++).trim())) return line;
    }

    /**
     * Checks whether a line is a comment line.
     *
     * @param line to check
     * @return if the input line is comment line or not
     */
    private static boolean isComment(String line) {
        if ((line = line.trim()).isEmpty()) return true;
        for (char flag : COMMENT_OUT_FLAG)
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
        return readlineNum == linesNum;
    }

    /**
     * Get all non-comment lines.
     * @return String[] made of non comment lines
     */
    public String[] getNonCommentLines() {
        return lines.stream().filter(line -> !isComment(line)).map(String::trim).toArray(String[]::new);
    }

}
