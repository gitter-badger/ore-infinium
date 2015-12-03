package com.ore.infinium.systems;

import com.artemis.*;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntMap;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.ore.infinium.Inventory;
import com.ore.infinium.Network;
import com.ore.infinium.OreClient;
import com.ore.infinium.OreWorld;
import com.ore.infinium.components.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ***************************************************************************
 * Copyright (C) 2015 by Shaun Reich <sreich02@gmail.com>
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ***************************************************************************
 */

/**
 * Handles the network side of things, for the client
 */
@Wire
public class NetworkClientSystem extends BaseSystem {

    private ComponentMapper<PlayerComponent> playerMapper;
    private ComponentMapper<SpriteComponent> spriteMapper;
    private ComponentMapper<ControllableComponent> controlMapper;
    private ComponentMapper<ItemComponent> itemMapper;
    private ComponentMapper<VelocityComponent> velocityMapper;
    private ComponentMapper<JumpComponent> jumpMapper;

    private ConcurrentLinkedQueue<Object> m_netQueue = new ConcurrentLinkedQueue<>();

    private OreWorld m_world;

    private Client m_clientKryo;

    /**
     * the network id is a special id that is used to refer to an entity across
     * the network, for this client. Basically it is so the client knows what
     * entity id the server is talking about..as the client and server ECS engines
     * will have totally different sets of entitiy id's.
     * <p>
     * So, server sends over what its internal entity id is for an entity to spawn,
     * as well as for referring to future ones, and we make a map of <server entity, client entity>,
     * since we normally receive a server entity id, and we must determine what *our* (clients) entity
     * id is, so we can do things like move it around, perform actions etc on it.
     */
    // the internal (client) entity for the network(server's) entity ID
    private IntMap<Integer> m_entityForNetworkId = new IntMap<>(500);
    // map to reverse lookup, the long (server) entity ID for the given Entity
    /**
     * <client entity, server entity>
     */
    private IntMap<Integer> m_networkIdForEntityId = new IntMap<>(500);

    public NetworkClientSystem(OreWorld world) {
        m_world = world;
    }

    public void connect(String ip) {
        //= new Client(16384, 8192, new JsonSerialization());
        m_clientKryo = new Client(8192, Network.bufferObjectSize);
        m_clientKryo.start();

        Network.register(m_clientKryo);

        m_clientKryo.addListener(new ClientListener(this));
        m_clientKryo.setKeepAliveTCP(999999);

        new Thread("kryonet connection client thread") {
            public void run() {
                try {
                    m_clientKryo.connect(99999999 /*HACK, debug*/, "127.0.0.1", Network.port);
                    // Server communication after connection can go here, or in Listener#connected().

                    Network.InitialClientData initialClientData = new Network.InitialClientData();

                    initialClientData.playerName = "testplayername";

                    //TODO generate some random thing
                    initialClientData.playerUUID = UUID.randomUUID().toString();
                    initialClientData.versionMajor = ORE_VERSION_MAJOR;
                    initialClientData.versionMinor = ORE_VERSION_MINOR;
                    initialClientData.versionRevision = ORE_VERSION_REVISION;

                    m_clientKryo.sendTCP(initialClientData);
                } catch (IOException ex) {

                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        }.start();

    }

    @Override
    protected void processSystem() {
        processNetworkQueue();
    }

    private void processNetworkQueue() {
        for (Object object = m_netQueue.poll(); object != null; object = m_netQueue.poll()) {
            if (object instanceof Network.PlayerSpawnedFromServer) {

                Network.PlayerSpawnedFromServer spawn = (Network.PlayerSpawnedFromServer) object;

                if (m_mainPlayerEntity == OreWorld.ENTITY_INVALID) {

                    m_world = new OreWorld(this, null);

                    m_mainPlayerEntity = createPlayer(spawn.playerName, m_clientKryo.getID());
                    SpriteComponent spriteComp = spriteMapper.get(m_mainPlayerEntity);

                    spriteComp.sprite.setPosition(spawn.pos.pos.x, spawn.pos.pos.y);
                    m_world.addPlayer(m_mainPlayerEntity);
                    m_world.initClient(m_mainPlayerEntity);

                    AspectSubscriptionManager aspectSubscriptionManager =
                            m_world.m_artemisWorld.getAspectSubscriptionManager();
                    EntitySubscription subscription = aspectSubscriptionManager.get(Aspect.all());
                    subscription.addSubscriptionListener(new ClientEntitySubscriptionListener());
                } else {
                    //FIXME cover other players joining case
                    throw new RuntimeException("fixme, other players joining not yet implemented");
                }
            } else if (object instanceof Network.KickReason) {
                Network.KickReason reason = (Network.KickReason) object;
            } else if (object instanceof Network.BlockRegion) {
                Network.BlockRegion region = (Network.BlockRegion) object;
                m_world.loadBlockRegion(region);
            } else if (object instanceof Network.SparseBlockUpdate) {
                Network.SparseBlockUpdate update = (Network.SparseBlockUpdate) object;
                m_world.loadSparseBlockUpdate(update);
            } else if (object instanceof Network.LoadedViewportMovedFromServer) {
                Network.LoadedViewportMovedFromServer v = (Network.LoadedViewportMovedFromServer) object;
                PlayerComponent c = playerMapper.get(m_mainPlayerEntity);
                c.loadedViewport.rect = v.rect;
            } else if (object instanceof Network.PlayerSpawnHotbarInventoryItemFromServer) {
                Network.PlayerSpawnHotbarInventoryItemFromServer spawn =
                        (Network.PlayerSpawnHotbarInventoryItemFromServer) object;

                //HACK spawn.id, sprite!!
                int e = m_world.m_artemisWorld.create();
                for (Component c : spawn.components) {
                    e.add(c);
                }

                SpriteComponent spriteComponent = spriteMapper.create(e);
                spriteComponent.textureName = spawn.textureName;
                spriteComponent.sprite.setSize(spawn.size.size.x, spawn.size.size.y);

                TextureRegion textureRegion;
                if (blockMapper.has(e) == false) {
                    textureRegion = m_world.m_atlas.findRegion(spriteComponent.textureName);
                } else {
                    textureRegion = m_world.m_tileRenderer.m_blockAtlas.findRegion(spriteComponent.textureName);
                }

                ToolComponent toolComponent = toolMapper.get(e);

                ItemComponent itemComponent = itemMapper.get(e);
                m_hotbarInventory.setSlot(itemComponent.inventoryIndex, e);

                //TODO i wonder if i can implement my own serializer (trivially!) and make it use the
                // entity/component pool. look into kryo itself, you can override creation (easily i hope), per class

            } else if (object instanceof Network.EntitySpawnFromServer) {
                //fixme this and hotbar code needs consolidation
                Network.EntitySpawnFromServer spawn = (Network.EntitySpawnFromServer) object;

                int e = m_world.m_artemisWorld.create();
                for (Component c : spawn.components) {
                    e.add(c);
                }

                //hack id..see above.
                SpriteComponent spriteComponent = m_world.engine.createComponent(SpriteComponent.class);
                spriteComponent.textureName = spawn.textureName;
                spriteComponent.sprite.setSize(spawn.size.size.x, spawn.size.size.y);
                spriteComponent.sprite.setPosition(spawn.pos.pos.x, spawn.pos.pos.y);

                TextureRegion textureRegion;
                if (blockMapper.has(e) == false) {
                    textureRegion = m_world.m_atlas.findRegion(spriteComponent.textureName);
                } else {
                    textureRegion = m_world.m_tileRenderer.m_blockAtlas.findRegion(spriteComponent.textureName);
                }

                spriteComponent.sprite.setRegion(textureRegion);

                m_networkIdForEntityId.put(e, spawn.id);
                m_entityForNetworkId.put(spawn.id, e);
            } else if (object instanceof Network.ChatMessageFromServer) {
                Network.ChatMessageFromServer data = (Network.ChatMessageFromServer) object;
                m_chat.addChatLine(data.timestamp, data.playerName, data.message, data.sender);
            } else if (object instanceof Network.EntityMovedFromServer) {
                Network.EntityMovedFromServer data = (Network.EntityMovedFromServer) object;
                int entity = m_entityForNetworkId.get(data.id);
                assert entity != OreWorld.ENTITY_INVALID;

                SpriteComponent spriteComponent = spriteMapper.get(entity);
                spriteComponent.sprite.setPosition(data.position.x, data.position.y);
            } else {
                if (!(object instanceof FrameworkMessage.KeepAlive)) {
                    Gdx.app.log("client network", "unhandled network receiving class");
                    assert false;
                }
            }

            // if (object instanceof ChatMessage) {
            //         ChatMessage chatMessage = (ChatMessage)object;
            //         chatFrame.addMessage(chatMessage.text);
            //         return;
            // }
        }
    }

    public void sendInventoryMove(Inventory.InventoryType sourceInventoryType, byte sourceIndex,
                                  Inventory.InventoryType destInventoryType, byte destIndex) {
        Network.PlayerMoveInventoryItemFromClient inventoryItemFromClient =
                new Network.PlayerMoveInventoryItemFromClient();
        inventoryItemFromClient.sourceType = sourceInventoryType;
        inventoryItemFromClient.sourceIndex = sourceIndex;
        inventoryItemFromClient.destType = destInventoryType;
        inventoryItemFromClient.destIndex = destIndex;

        m_clientKryo.sendTCP(inventoryItemFromClient);
    }

    /**
     * Send the command indicating (main) player moved to position
     */
    public void sendPlayerMoved() {
        SpriteComponent sprite = spriteMapper.get(m_mainPlayerEntity);

        Network.PlayerMoveFromClient move = new Network.PlayerMoveFromClient();
        move.position = new Vector2(sprite.sprite.getX(), sprite.sprite.getY());

        m_clientKryo.sendTCP(move);
    }

    public void sendChatMessage(String message) {
        Network.ChatMessageFromClient chatMessageFromClient = new Network.ChatMessageFromClient();
        chatMessageFromClient.message = message;

        m_clientKryo.sendTCP(chatMessageFromClient);
    }

    private void sendHotbarEquipped(byte index) {
        Network.PlayerEquipHotbarIndexFromClient playerEquipHotbarIndexFromClient =
                new Network.PlayerEquipHotbarIndexFromClient();
        playerEquipHotbarIndexFromClient.index = index;

        m_clientKryo.sendTCP(playerEquipHotbarIndexFromClient);
    }

    public void sendBlockPick(int x, int y) {
        Network.BlockPickFromClient blockPickFromClient = new Network.BlockPickFromClient();
        blockPickFromClient.x = x;
        blockPickFromClient.y = y;
        m_clientKryo.sendTCP(blockPickFromClient);
    }

    public void sendBlockPlace(int x, int y) {
        Network.BlockPlaceFromClient blockPlaceFromClient = new Network.BlockPlaceFromClient();
        blockPlaceFromClient.x = x;
        blockPlaceFromClient.y = y;
        m_clientKryo.sendTCP(blockPlaceFromClient);
    }

    public void sendItemPlace(float x, float y) {
        Network.ItemPlaceFromClient itemPlace = new Network.ItemPlaceFromClient();
        itemPlace.x = x;
        itemPlace.y = y;

        m_clientKryo.sendTCP(itemPlace);
    }

    class ClientListener extends Listener {
        private OreClient m_client;

        ClientListener(OreClient client) {
            m_client = client;

        }

        public void connected(Connection connection) {
            connection.setTimeout(999999999);
        }

        //FIXME: do sanity checking (null etc) on both client, server
        public void received(Connection connection, Object object) {
            m_netQueue.add(object);
        }

        public void disconnected(Connection connection) {
        }
    }

}
