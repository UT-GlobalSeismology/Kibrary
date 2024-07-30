package io.github.kensuke1984.kibrary.util;

import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import org.apache.commons.io.IOUtils;

/**
 * Utilities to handle files.
 *
 * @since 2021/11/21 - created when Utilities.java was split up.
 */
public final class FileAid {
    private FileAid() {}

    /**
     * Given a Path, this method extracts the root of the name of the file.
     * The path of parent folders and the extension of the file is removed.
     * @param filePath (Path) The file to extract name root
     * @return (String) File name root
     *
     * @author otsuru
     * @since 2023/1/16
     */
    public static String extractNameRoot(Path filePath) {
        String fileName = filePath.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    /**
     * Moves a given file to a specified directory.
     * @param srcPath       {@link Path} of the file to be moved
     * @param destDirectory {@link Path} of the destination directory
     * @param createDestDir If {@code true} create the destination directory, otherwise if
     *                      {@code false} throw an IOException
     * @param options       for copying
     * @throws IOException if an I/O error occurs
     */
    public static void moveToDirectory(Path srcPath, Path destDirectory, boolean createDestDir, CopyOption... options)
            throws IOException {
        if (createDestDir)
            Files.createDirectories(destDirectory);
        Files.move(srcPath, destDirectory.resolve(srcPath.getFileName()), options);
    }

    /**
     * @param targetPath    even if a target path is a relative path, the symlink is for its absolute path.
     * @param destDirectory in which the symlink is created.
     * @param createDestDir if this value is true and the destDirectory does not exist, this method creates the directory.
     * @param options       for copying
     * @throws IOException if any
     */
    public static void createLinkInDirectory(Path targetPath, Path destDirectory, boolean createDestDir,
                                             CopyOption... options) throws IOException {
        System.out.println(destDirectory.resolve(targetPath.getFileName()));
        if (createDestDir) Files.createDirectories(destDirectory);
        Files.createSymbolicLink(destDirectory.resolve(targetPath.getFileName()), targetPath.toAbsolutePath());
    }

    public static void deleteOnExit(Path path) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }));
    }

    /**
     * Extract a zipfile into outRoot
     *
     * @param zipPath path of a zip file to extract
     * @param outRoot path of a target path (folder)
     * @param options options for writing
     */
    public static void extractZip(Path zipPath, Path outRoot, OpenOption... options) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipPath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = outRoot.resolve(entry.getName());
                try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
                        Files.newOutputStream(outPath, options))) {
                    if (IOUtils.copy(zis, bufferedOutputStream) < 0)
                        throw new RuntimeException("Zip file could not be extracted without errors.");
                }
            }
        }
    }

    /**
     * @param url URL of the file to download
     * @return path of the downloaded file (dl......tmp in TEMP)
     * @throws IOException if any
     */
    public static Path download(URL url) throws IOException {
        Path out = Files.createTempFile("dl", "tmp");
        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
                FileOutputStream fos = new FileOutputStream(out.toFile()); FileChannel fc = fos.getChannel()) {
            fc.transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        return out;
    }

    /**
     * @param url       URL of the file to download
     * @param outPath   path of the downloaded file
     * @param overwrite if this is false and the file of outPath exists, the download is cancelled.
     * @throws IOException if any
     */
    public static void download(URL url, Path outPath, boolean overwrite) throws IOException {
        if (!overwrite && Files.exists(outPath)) throw new FileAlreadyExistsException(outPath + " already exists.");
        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
        long fileSize = httpConnection.getContentLength();
        JProgressBar bar = null;
        JFrame frame = null;
        if (0 < fileSize && !GraphicsEnvironment.isHeadless()) {
            bar = new JProgressBar(0, (int) fileSize);
            frame = new JFrame("Downloading a file");
            frame.setResizable(false);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setSize(300, 70);
            frame.add(bar);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(url.openStream());
                FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
            byte[] buf = new byte[8192];
            int x = 0;
            int downloaded = 0;
            while (0 <= (x = bufferedInputStream.read(buf))) {
                fos.write(buf, 0, x);
                if (Objects.nonNull(bar)) bar.setValue((downloaded += x));
            }
        } finally {
            if (Objects.nonNull(frame)) frame.dispose();
        }
    }

}
