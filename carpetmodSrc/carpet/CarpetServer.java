package carpet;

import java.util.List;
import java.util.Random;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import carpet.carpetclient.CarpetClientServer;
import carpet.helpers.TickSpeed;
import carpet.logging.LoggerRegistry;
import carpet.utils.HUDController;
import carpet.utils.PluginChannelTracker;
import carpet.utils.TickingArea;
import narcolepticfrog.rsmm.events.PlayerConnectionEventDispatcher;
import narcolepticfrog.rsmm.events.ServerPacketEventDispatcher;
import narcolepticfrog.rsmm.events.TickStartEventDispatcher;
import narcolepticfrog.rsmm.server.RSMMServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketKeepAlive;
import net.minecraft.server.MinecraftServer;

public class CarpetServer // static for now - easier to handle all around the code, its one anyways
{
    public static final Random rand = new Random((int)((2>>16)*Math.random()));
    private static CarpetClientServer CCServer;
    public static RSMMServer rsmmServer;
    public static MinecraftServer minecraft_server;
    
    public static final int VANILLA_CHECK_PING_ID = -999;
    
    public static void init(MinecraftServer server) //aka constructor of this static singleton class
    {
        CCServer = new CarpetClientServer(server);
        rsmmServer = new RSMMServer(server);
        CarpetServer.minecraft_server = server;
    }
    public static void onServerLoaded(MinecraftServer server)
    {
        CarpetSettings.apply_settings_from_conf(server);
        CarpetSettings.reload_all_statics();
        LoggerRegistry.initLoggers(server);
    }
    public static void onLoadAllWorlds(MinecraftServer server)
    {
        TickingArea.loadConfig(server);
    }
    public static void onWorldsSaved(MinecraftServer server)
    {
        TickingArea.saveConfig(server);
    }

    public static void tick(MinecraftServer server)
    {
        TickSpeed.tick(server);
        if (CarpetSettings.redstoneMultimeter)
        {
            TickStartEventDispatcher.dispatchEvent(server.getTickCounter());
        }
        HUDController.update_hud(server);
    }
    public static void playerConnected(EntityPlayerMP player)
    {
        if (CarpetSettings.redstoneMultimeter)
            PlayerConnectionEventDispatcher.dispatchPlayerConnectEvent(player);
        LoggerRegistry.playerConnected(player);
        CCServer.onPlayerConnect(player);
        
        // If we receive the response packet without having received the response to the
        // preceding REGISTER packets, we know for sure that we have a vanilla client connected
        player.connection.sendPacket(new SPacketKeepAlive(VANILLA_CHECK_PING_ID));
    }
    public static void receiveVanillaCheck(EntityPlayerMP player)
    {
        // At this point we know for certain whether the client is vanilla or not, do
        // the initialization stuff that depends on that here
        player.getRecipeBook().init(player);
    }

    public static void playerDisconnected(EntityPlayerMP player)
    {
        if (CarpetSettings.redstoneMultimeter) // optionally send anyways (Frog's decision) decision
        {
            PlayerConnectionEventDispatcher.dispatchPlayerDisconnectEvent(player);
        }
        PluginChannelTracker.unregisterAll(player);
        LoggerRegistry.playerDisconnected(player);
        CCServer.onPlayerDisconnect(player);
    }

    //network stuffs
    public static void customPacket(EntityPlayerMP playerEntity, String packet_id, CPacketCustomPayload packetIn)
    {
        if ("REGISTER".equals(packet_id)) {
            List<String> channels = getChannels(packetIn.getBufferData());
            for (String channel : channels) {
                PluginChannelTracker.register(playerEntity, channel);
            }
            if (CarpetSettings.redstoneMultimeter)
                ServerPacketEventDispatcher.dispatchChannelRegister(playerEntity, channels);
            CCServer.onChannelRegister(playerEntity, channels);
        } else if ("UNREGISTER".equals(packet_id)) {
            List<String> channels = getChannels(packetIn.getBufferData());
            for (String channel : channels) {
                PluginChannelTracker.unregister(playerEntity, channel);
            }
            if (CarpetSettings.redstoneMultimeter)
                ServerPacketEventDispatcher.dispatchChannelUnregister(playerEntity, channels);
            CCServer.onChannelUnregister(playerEntity, channels);
        } else {
            if (CarpetSettings.redstoneMultimeter)
                ServerPacketEventDispatcher.dispatchCustomPayload(playerEntity, packet_id, packetIn.getBufferData());
            CCServer.onCustomPayload(playerEntity, packet_id, packetIn.getBufferData());
        }
    }

    private static List<String> getChannels(PacketBuffer buff) {
        buff.resetReaderIndex();
        byte[] bytes = new byte[buff.readableBytes()];
        buff.readBytes(bytes);
        String channelString = new String(bytes, Charsets.UTF_8);
        return Lists.newArrayList(channelString.split("\u0000"));
        //return channels;
    }
    
    public static Random setRandomSeed(int p_72843_1_, int p_72843_2_, int p_72843_3_)
    {
        long i = (long)p_72843_1_ * 341873128712L + (long)p_72843_2_ * 132897987541L + CCServer.getMinecraftServer().worlds[0].getWorldInfo().getSeed() + (long)p_72843_3_;
        rand.setSeed(i);
        return rand;
    }
}
