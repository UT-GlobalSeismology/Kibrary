package io.github.kensuke1984.kibrary;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

/**
 * Runtime environment
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class Environment {
    private Environment() {}

    public static final Path KIBRARY_HOME;
    public static final Path KIBRARY_SHARE;
    public static final Path KIBRARY_BIN;
    private static final Path PROPERTY_PATH;
    private static final Properties PROPERTY = new Properties();
    private static final Properties DEFAULT_PROPERTY = new Properties();

    static {
        String home = System.getenv("KIBRARY_HOME");
        if (Objects.isNull(home)) {
            if (Objects.nonNull(home = System.getenv("APPDATA"))) {
                KIBRARY_HOME = Paths.get(home).resolve("Kibrary");
            } else {
                KIBRARY_HOME = Paths.get(System.getProperty("user.home")).resolve("Kibrary");
            }
        } else {
            KIBRARY_HOME = Paths.get(home);
        }
        KIBRARY_BIN = KIBRARY_HOME.resolve("bin");
        KIBRARY_SHARE = KIBRARY_HOME.resolve("share");
        try {
            Files.createDirectories(KIBRARY_HOME);
            Files.createDirectories(KIBRARY_BIN);
            Files.createDirectories(KIBRARY_SHARE);
        } catch (IOException io) {
            throw new RuntimeException("Set a proper KIBRARY_HOME.");
        }
        PROPERTY_PATH = KIBRARY_HOME.resolve(".property");
    }

    static {
        setDefaultProperty();
        if (Files.exists(PROPERTY_PATH)) {
            try (BufferedReader br = Files.newBufferedReader(PROPERTY_PATH)) {
                PROPERTY.load(br);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(100);
            }
        }
        copyDefault();
    }

    private static void setDefaultProperty() {
        DEFAULT_PROPERTY.setProperty("userName", System.getProperty("user.name"));
        DEFAULT_PROPERTY.setProperty("email", "hoge@foo.com");
        DEFAULT_PROPERTY.setProperty("institute", "University of Hoge");
        DEFAULT_PROPERTY.setProperty("mail", "0-0-0 Hoge, Foo, Tokyo, " + System.getProperty("user.country"));
        DEFAULT_PROPERTY.setProperty("phone", "01-2345-6789");
        DEFAULT_PROPERTY.setProperty("fax", "09-8765-4321");
    }

    private static void copyDefault() {
        boolean added = false;
        for (Object o : DEFAULT_PROPERTY.keySet()) {
            if (!PROPERTY.containsKey(o)) {
                PROPERTY.setProperty((String) o, DEFAULT_PROPERTY.getProperty((String) o));
                added = true;
            }
        }
        if (added) {
            try (BufferedWriter bw = Files.newBufferedWriter(PROPERTY_PATH)) {
                PROPERTY.store(bw, "This file is including default values, please check it out.");
                System.err.println(PROPERTY_PATH + " is newly created.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getFax() {
        return PROPERTY.getProperty("fax");
    }

    public static String getPhone() {
        return PROPERTY.getProperty("phone");
    }

    public static String getMail() {
        return PROPERTY.getProperty("mail");
    }

    public static String getEmail() {
        return PROPERTY.getProperty("email");
    }

    public static String getGmail() {
        return PROPERTY.getProperty("gmail");
    }

    public static String getUserName() {
        return PROPERTY.getProperty("userName");
    }

    public static String getInstitute() {
        return PROPERTY.getProperty("institute");
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Shows environment information.
     * @param args will be ignored
     */
    public static void main(String[] args) {
        run(null);
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        return null;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     */
    public static void run(CommandLine cmdLine) {
//      System.getProperties().keySet().forEach(System.out::println);
        System.out.println("OS: " + System.getProperty("os.name"));
        System.out.println("User name: " + getUserName());
        System.out.println("Language: " + System.getProperty("user.language"));
        System.out.println("Time zone: " + System.getProperty("user.timezone"));
        System.out.println("Home directory: " + System.getProperty("user.home"));
        System.out.println("Java Specification version: " + System.getProperty("java.specification.version"));
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java Virtual version: " + System.getProperty("java.vm.version"));
        System.out.println("Java Runtime version: " + System.getProperty("java.runtime.version"));
        System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Max memory [GB]: " + Runtime.getRuntime().maxMemory() / 1000000000.0);
    }
}
