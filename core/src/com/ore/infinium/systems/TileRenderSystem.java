package com.ore.infinium.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntMap;
import com.ore.infinium.Block;
import com.ore.infinium.OreWorld;
import com.ore.infinium.components.*;

/**
 * ***************************************************************************
 * Copyright (C) 2014, 2015 by Shaun Reich <sreich02@gmail.com>
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
@Wire
public class TileRenderSystem extends BaseSystem implements RenderSystemMarker {
    public static int tilesInViewCountDebug;

    public TextureAtlas m_blockAtlas;
    public TextureAtlas m_tilesAtlas;

    private OrthographicCamera m_camera;

    private SpriteBatch m_batch;

    private ComponentMapper<PlayerComponent> playerMapper;
    private ComponentMapper<SpriteComponent> spriteMapper;
    private ComponentMapper<ControllableComponent> controlMapper;
    private ComponentMapper<ItemComponent> itemMapper;
    private ComponentMapper<VelocityComponent> velocityMapper;
    private ComponentMapper<JumpComponent> jumpMapper;

    // <byte mesh type, string texture name>
    public IntMap<String> dirtBlockMeshes;
    public IntMap<String> stoneBlockMeshes;
    public IntMap<String> grassBlockMeshes;

    public TileRenderer(OrthographicCamera camera, OreWorld world) {
        m_camera = camera;
        m_world = world;
        m_batch = new SpriteBatch(5000);

        m_blockAtlas = new TextureAtlas(Gdx.files.internal("packed/blocks.atlas"));
        m_tilesAtlas = new TextureAtlas(Gdx.files.internal("packed/tiles.atlas"));
        for (TextureRegion region : m_tilesAtlas.getRegions()) {
            //fixme: honestly idk why we need to flip each one..but we do.
            region.flip(false, true);
        }

        //dirt 16 and beyond are transition things.
        final int dirtMax = 25;
        dirtBlockMeshes = new IntMap<>(dirtMax);
        for (int i = 0; i <= dirtMax; ++i) {
            String formatted = String.format("dirt-%02d", i);
            dirtBlockMeshes.put(i, formatted);
        }

        //18+ are transition helpers
        final int grassMax = 31;
        grassBlockMeshes = new IntMap<>(grassMax);
        for (int i = 0; i <= grassMax; ++i) {
            String formatted = String.format("grass-%02d", i);
            grassBlockMeshes.put(i, formatted);
        }

        final int stoneMax = 30;
        stoneBlockMeshes = new IntMap<>(stoneMax);
        for (int i = 0; i <= stoneMax; ++i) {
            String formatted = String.format("stone-%02d", i);
            stoneBlockMeshes.put(i, formatted);
        }
    }

    @Override
    protected void processSystem() {
        render(world.getDelta());
    }

    public void render(float elapsed) {
        if (m_world.m_mainPlayerEntity == OreWorld.ENTITY_INVALID) {
            return;
        }

        if (!m_world.m_client.m_renderTiles) {
            return;
        }

        tilesInViewCountDebug = 0;

        m_batch.setProjectionMatrix(m_camera.combined);
        SpriteComponent sprite = spriteMapper.get(m_world.m_mainPlayerEntity);

        Vector3 playerPosition = new Vector3(sprite.sprite.getX(), sprite.sprite.getY(),
                                             0); //new Vector3(100, 200, 0);//positionComponent->position();
        int tilesBeforeX = (int) (playerPosition.x / OreWorld.BLOCK_SIZE);
        int tilesBeforeY = (int) (playerPosition.y / OreWorld.BLOCK_SIZE);

        // determine what the size of the tiles are but convert that to our zoom level
        final Vector3 tileSize = new Vector3(OreWorld.BLOCK_SIZE, OreWorld.BLOCK_SIZE, 0);
        tileSize.mul(m_camera.combined);

        final int tilesInView =
                (int) (m_camera.viewportHeight / OreWorld.BLOCK_SIZE * m_camera.zoom);//m_camera.project(tileSize);
        final int startX = Math.max(tilesBeforeX - (tilesInView) - 2, 0);
        final int startY = Math.max(tilesBeforeY - (tilesInView) - 2, 0);
        final int endX = Math.min(tilesBeforeX + (tilesInView) + 2, OreWorld.WORLD_SIZE_X);
        final int endY = Math.min(tilesBeforeY + (tilesInView) + 2, OreWorld.WORLD_SIZE_Y);

      /*
      if (Math.abs(startX) != startX) {
          //qCDebug(ORE_TILE_RENDERER) << "FIXME, WENT INTO NEGATIVE COLUMN!!";
          throw new IndexOutOfBoundsException("went into negative world column");
      } else if (Math.abs(startY) != startY) {
          throw new IndexOutOfBoundsException("went into negative world row");
      }
      */

        m_batch.begin();

        TextureAtlas.AtlasRegion region;
        String textureName = "";

        //fixme all instances of findRegion need to be replaced with cached
        //versions. they're allegedly quite slow
        for (int x = startX; x < endX; ++x) {
            for (int y = startY; y < endY; ++y) {
                ++tilesInViewDebug;

                Block block = m_world.blockAt(x, y);

                float tileX = OreWorld.BLOCK_SIZE * (float) x;
                float tileY = OreWorld.BLOCK_SIZE * (float) y;

                boolean drawWallTile = false;

                //String textureName = World.blockTypes.get(block.type).textureName;
                if (block.type == Block.BlockType.DirtBlockType) {

                    if (block.hasFlag(Block.BlockFlags.GrassBlock)) {
                        textureName = grassBlockMeshes.get(block.meshType);
                        assert textureName != null : "block mesh lookup failure";
                    } else {
                        textureName = dirtBlockMeshes.get(block.meshType);
                        assert textureName != null : "block mesh lookup failure type: " + block.meshType;
                    }
                } else if (block.type == Block.BlockType.StoneBlockType) {
                    textureName = stoneBlockMeshes.get(block.meshType);
                    assert textureName != null : "block mesh lookup failure type: " + block.meshType;

                } else if (block.type == Block.BlockType.NullBlockType) {
                    if (block.wallType == Block.WallType.NullWallType) {
                        //we can skip a draw call iff the wall, and block is null
                        continue;
                    } else {
                        drawWallTile = true;
                    }
                } else {
                    assert false : "unhandled block";
                }

                if (drawWallTile) {
                    m_batch.setColor(0.5f, 0.5f, 0.5f, 1);
                }

                //either we draw the wall tile, or the foreground tile. never both (yet? there might be *some*
                // scenarios..)
                if (!drawWallTile) {
                    region = m_tilesAtlas.findRegion(textureName);

                    m_batch.draw(region, tileX, tileY, OreWorld.BLOCK_SIZE, OreWorld.BLOCK_SIZE);

                } else {
                    //draw walls
                    //hack of course, for wall drawing
                    textureName = dirtBlockMeshes.get(0);
                    assert textureName != null : "block mesh lookup failure type: " + block.meshType;

                    region = m_tilesAtlas.findRegion(textureName);
                    m_batch.draw(region, tileX, tileY, OreWorld.BLOCK_SIZE, OreWorld.BLOCK_SIZE);

                }

                if (drawWallTile) {
                    m_batch.setColor(1, 1, 1, 1);
                }
            }
        }

        tilesInViewCountDebug = tilesInViewDebug;
        m_batch.end();
    }

}
