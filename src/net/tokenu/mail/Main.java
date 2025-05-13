package net.tokenu.mail;

import commons.*;
import net.tokenu.mail.service.Microsoft;
import net.tokenu.mail.ui.EmailClientGUI;

public class Main {
    public static RandomPickerType<String> proxies;
    static {
        try {
            proxies = RandomPickerType.create(FileUtil.readAllLines("proxies.txt"));
            if (proxies.isEmpty()) {
                LogUtil.error("No proxies found, it's recommended to use proxies.");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        System.out.printf("Trying to start program with Java %s%s%n%n", System.getProperty("java.version"), ConsoleColors.RESET);
        if (!System.getProperty("java.version").contains("1.8.0")){
            System.out.println(
                    "Recommend Java version is 1.8.0" +
                            ConsoleColors.RED+"\n Using any other version may not work , please download from https://www.java.com/en/download/\n"+ConsoleColors.RESET);
            return;
        }

        // Check if command-line arguments are provided
        if (args.length > 0 && args[0].equalsIgnoreCase("--cli")) {
            // Run the command-line version
            try {
                Microsoft.main(new String[0]);
            } catch (Exception e) {
                System.err.println("Error running command-line version: " + e.getMessage());
                ThrowableUtil.println(e);
            }
        } else {
            // Run the GUI version
            EmailClientGUI.main(args);
        }
    }
}
