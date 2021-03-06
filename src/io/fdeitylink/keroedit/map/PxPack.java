/*
 * TODO:
 * Ensure all 2D array arguments have rows with equal lengths
 * Allow null arguments for string setters? (instead of setting to null, set to "")
 * toBuff() methods in Head, TileLayer, and Entity that return ByteBuffer (to make save() easier)
 */

package io.fdeitylink.keroedit.map;

import java.util.ArrayList;
import java.util.Arrays;

import java.text.MessageFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.nio.channels.SeekableByteChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.IOException;
import java.text.ParseException;

import javafx.scene.paint.Color;

import io.fdeitylink.util.NullArgumentException;

import io.fdeitylink.util.UtilsKt;

import io.fdeitylink.keroedit.Messages;

import io.fdeitylink.keroedit.gamedata.GameData;

/**
 * Object for storing information about a PXPACK map file
 */
public final class PxPack {
    public static final int NUM_LAYERS = 3;

    private Path path;

    private final Head head;
    private final TileLayer[] tileLayers;
    private final ArrayList <Entity> entities;

    /**
     * Constructs a PxPackMap object and parses a given PXPACK mapFile
     * to initialize the fields of the object
     *
     * @param inPath A File object pointing to a PXPACK map file
     *
     * @throws IOException if there was an error reading the PXPACK file
     * @throws ParseException if the mapFile format was somehow incorrect
     */
    public PxPack(final Path inPath) throws IOException, ParseException {
        path = NullArgumentException.Companion.requireNonNull(inPath, "PxPack", "inPath").toAbsolutePath();

        if (!inPath.toString().endsWith(GameData.mapExtension)) {
            throw new IllegalArgumentException("File " + inPath.toAbsolutePath() +
                                               " does not end with extension " + GameData.mapExtension);
        }

        //TODO: Test this
        if (!Files.exists(inPath)) {
            /*
             * TODO:
             * Set inPath to an internal default PXPACK file and parse that?
             * Clone internal file to outside?
             */
            head = new Head("", new String[]{"", "", "", ""}, "", new byte[]{0, 0, 0, 0, 0}, Color.BLACK,
                            new String[]{"mpt00", "", ""}, new byte[]{2, 2, 2}, new byte[]{0, 0, 1});
            /*
             * TODO:
             * All images named must exist
             * Only first tileset is required
             * Use most common values for defaults
             * Is spritesheet required? (I don't think so...)
             */

            tileLayers = new TileLayer[NUM_LAYERS];
            for (int i = 0; i < tileLayers.length; ++i) {
                tileLayers[i] = new TileLayer();
            }

            entities = new ArrayList <>();
            return;
        }

        try (SeekableByteChannel chan = Files.newByteChannel(inPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(Head.HEADER_STRING.length());
            chan.read(buf);

            if (!(new String(buf.array(), "SJIS").equals(Head.HEADER_STRING))) {
                throw new ParseException(MessageFormat.format(Messages.INSTANCE.get("PxPack.INVALID_HEADER"),
                                                              inPath.getFileName()), (int)chan.position());
            }

            final String description = readString(chan, Head.DESCRIPTION_MAX_LEN, "description");

            final String[] mapnames = new String[Head.NUM_REF_MAPS];
            for (int i = 0; i < mapnames.length; ++i) {
                mapnames[i] = readString(chan, Head.FILENAME_MAX_LEN, "map name");
            }

            final String spritesheetName = readString(chan, Head.FILENAME_MAX_LEN, "spritesheet name");

            buf = ByteBuffer.allocate(8);
            chan.read(buf);
            buf.flip();

            final byte[] data = Arrays.copyOf(buf.array(), 5);
            buf.position(buf.position() + 5);

            final int red = buf.get() & 0xFF;
            final int green = buf.get() & 0xFF;
            final int blue = buf.get() & 0xFF;
            final Color bgColor = Color.rgb(red, green, blue);

            final String[] tilesetNames = new String[NUM_LAYERS];
            final byte[] visibilityTypes = new byte[NUM_LAYERS];
            final byte[] scrollTypes = new byte[NUM_LAYERS];
            for (int i = 0; i < tilesetNames.length; ++i) {
                tilesetNames[i] = readString(chan, Head.FILENAME_MAX_LEN, "tileset name");

                if (0 == i && tilesetNames[i].isEmpty()) {
                    throw new ParseException(MessageFormat.format(Messages.INSTANCE.get("PxPack.MISSING_FIRST_TILESET"),
                                                                  inPath.getFileName()), (int)chan.position());
                }

                buf = ByteBuffer.allocate(2);
                chan.read(buf);
                buf.flip();

                visibilityTypes[i] = buf.get();
                scrollTypes[i] = buf.get();

                /*
                 * First byte is a sort of visibility toggle
                 *  - 0 -> invisible
                 *  - 2 -> visible
                 *  - 1 or >= 3 -> pulls wrong tiles from same tileset (offsets?)
                 *  - > 32 -> game crashes
                 *
                 *  Second byte is scroll type (scroll.txt)
                 */
            }

            head = new Head(description, mapnames, spritesheetName, data, bgColor, tilesetNames, visibilityTypes, scrollTypes);

            tileLayers = new TileLayer[NUM_LAYERS];

            for (int i = 0; i < tileLayers.length; ++i) {
                buf = ByteBuffer.allocate(TileLayer.HEADER_STRING.length());
                chan.read(buf);
                if (!(new String(buf.array()).equals(TileLayer.HEADER_STRING))) {
                    throw new ParseException(MessageFormat.format(Messages.INSTANCE.get("PxPack.INVALID_LAYER_HEADER"),
                                                                  i, inPath.getFileName()), (int)chan.position());
                }

                buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();

                final int width = buf.getShort() & 0xFFFF;
                final int height = buf.getShort() & 0xFFFF;
                if (width * height > 0) {
                    //TODO: Find if it is ever not 0 (might've already checked but make sure)
                    chan.position(chan.position() + 1); //skip a byte (always 0)

                    buf = ByteBuffer.allocate(width * height);
                    chan.read(buf);
                    buf.flip();

                    final int[][] tiles = new int[height][width];
                    for (int y = 0; y < tiles.length; ++y) {
                        for (int x = 0; x < tiles[y].length; ++x) {
                            tiles[y][x] = buf.get() & 0xFF;
                        }
                    }
                    tileLayers[i] = new TileLayer(tiles);
                }
                else {
                    tileLayers[i] = new TileLayer();
                }
            }

            buf = ByteBuffer.allocate(2);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            chan.read(buf);
            buf.flip();

            final int numEntities = buf.getShort();

            entities = new ArrayList <>(numEntities);

            for (int i = 0; i < numEntities; ++i) {
                buf = ByteBuffer.allocate(9);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                chan.read(buf);
                buf.flip();

                final byte flag = buf.get();

                final int type = buf.get() & 0xFF;

                final byte unknownByte = buf.get(); //index from tileset? subtype?

                final int x = buf.getShort() & 0xFFFF;
                final int y = buf.getShort() & 0xFFFF;

                final byte[] entityData = new byte[2];
                buf.get(entityData);

                final String name = readString(chan, Entity.NAME_MAX_LEN, "entity name");

                entities.add(new Entity(flag, type, unknownByte, x, y, entityData, name));
            }
        }
    }

    /**
     * Saves the PXPACK file represented by this object
     *
     * @throws IOException if there was an error saving the PXPACK file
     */
    public void save() throws IOException {
        try (SeekableByteChannel chan = Files.newByteChannel(path, StandardOpenOption.WRITE,
                                                             StandardOpenOption.TRUNCATE_EXISTING,
                                                             StandardOpenOption.CREATE)) {
            ByteBuffer buf = ByteBuffer.wrap(Head.HEADER_STRING.getBytes("SJIS"));
            chan.write(buf);

            writeString(chan, head.getDescription());

            for (final String map : head.getMapNames()) {
                writeString(chan, map);
            }

            writeString(chan, head.getSpritesheetName());

            buf = ByteBuffer.wrap(head.getData());
            chan.write(buf);

            buf = ByteBuffer.wrap(new byte[]{(byte)(head.getBgColor().getRed() * 255),
                                             (byte)(head.getBgColor().getGreen() * 255),
                                             (byte)(head.getBgColor().getBlue() * 255)});
            chan.write(buf);

            final String[] tilesetNames = head.getTilesetNames();
            final byte[] visibilityTypes = head.getVisibilityTypes();
            final byte[] scrollTypes = head.getScrollTypes();
            for (int i = 0; i < tilesetNames.length; ++i) {
                writeString(chan, tilesetNames[i]);

                buf = ByteBuffer.allocate(2);
                buf.put(visibilityTypes[i]);
                buf.put(scrollTypes[i]);
                buf.flip();

                chan.write(buf);
            }

            for (final TileLayer layer : tileLayers) {
                buf = ByteBuffer.wrap(TileLayer.HEADER_STRING.getBytes("SJIS"));
                chan.write(buf);

                final int[][] layerData = layer.getTiles();
                if (null == layerData) {
                    buf = ByteBuffer.allocate(4);
                    buf.putShort((short)0).putShort((short)0);
                    buf.flip();

                    chan.write(buf);
                }
                else {
                    short width = (short)layerData[0].length;
                    short height = (short)layerData.length;

                    buf = ByteBuffer.allocate(5);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    buf.putShort(width).putShort(height).put((byte)0);
                    buf.flip();

                    chan.write(buf);

                    buf = ByteBuffer.allocate((width & 0xFFFF) * (height & 0xFFFF)); //& 0xFFFF treats as unsigned when converted to int
                    for (final int[] row : layerData) {
                        for (final int tile : row) {
                            buf.put((byte)tile);
                        }
                    }
                    buf.flip();
                    chan.write(buf);
                }
            }

            buf = ByteBuffer.allocate(2);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putShort((short)entities.size());
            buf.flip();

            chan.write(buf);

            for (final Entity e : entities) {
                buf = ByteBuffer.allocate(9);
                buf.order(ByteOrder.LITTLE_ENDIAN);

                buf.put(e.getFlag());

                buf.put((byte)e.getType());

                buf.put(e.getUnknownByte());

                buf.putShort((short)e.getX());
                buf.putShort((short)e.getY());

                buf.put(e.getData());

                buf.flip();
                chan.write(buf);

                writeString(chan, e.getName());
            }
        }
    }

    public String getName() {
        return UtilsKt.baseFilename(path, GameData.mapExtension);
    }

    public Path getPath() {
        return path;
    }

    public void rename(final String newName) throws IOException {
        NullArgumentException.Companion.requireNonNull(newName, "rename", "newName");
        if (newName.length() > Head.FILENAME_MAX_LEN) {
            throw new IllegalArgumentException("Attempt to rename PxPack when arg has length " +
                                               newName.length() + " when max length is " + Head.FILENAME_MAX_LEN +
                                               " (newName: " + newName + ')');
        }

        path = Files.move(path, path.resolveSibling(newName + GameData.mapExtension));
    }

    public Head getHead() {
        return head;
    }

    public TileLayer[] getTileLayers() {
        //shallow copies elements, so elements are same, array reference is different
        return Arrays.copyOf(tileLayers, tileLayers.length);
    }

    public ArrayList <Entity> getEntities() {
        /*
         * TODO:
         * Disallow null elements
         * Maybe return an unmodifiable list and provide set/addEntity() methods
         * Cap size at 0xFFFF
         */
        return entities;
    }

    /**
     * Reads a string from a PXPACK file tied to a given FileChannel
     *
     * @param chan The {@code SeekableByteChannel} object to read from
     * @param maxLen The maximum length for the string being read
     * @param type A {@code String} denoting the type of the string being read (what it is for).
     * Used to provide a descriptive exception message if the read string is of invalid length
     * or if it contains spaces.
     *
     * @return The string that was read
     *
     * @throws IOException if there was an error reading the string from the PXPACK file
     * @throws ParseException if the string was too long (as per {@code maxLen} or contained spaces
     */
    private String readString(final SeekableByteChannel chan, final int maxLen, final String type)
            throws IOException, ParseException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        chan.read(buf);
        buf.flip();

        final int strLen = buf.get() & 0xFF;
        if (maxLen < strLen) {
            throw new ParseException(MessageFormat.format(Messages.INSTANCE.get("PxPack.ReadString.INVALID_LEN"),
                                                          type, maxLen, strLen), (int)chan.position());
        }

        buf = ByteBuffer.allocate(strLen);
        chan.read(buf);

        final String str = new String(buf.array(), "SJIS");
        if (!"description".equals(type) && str.contains(" ")) {
            throw new ParseException(MessageFormat.format(Messages.INSTANCE.get("PxPack.ReadString.CONTAINS_SPACE"),
                                                          type), (int)chan.position());
        }

        return str;
    }

    private void writeString(final SeekableByteChannel chan, final String str) throws IOException {
        //TODO: test with 0 len str
        final byte[] strAsBytes = str.getBytes("SJIS");
        final ByteBuffer buf = ByteBuffer.allocate(1 + strAsBytes.length);

        buf.put((byte)strAsBytes.length);
        buf.put(strAsBytes);
        buf.flip();

        chan.write(buf);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();

        result.append("Name: ").append(path.getFileName()).append('\n');

        result.append(head).append('\n');

        for (final TileLayer layer : tileLayers) {
            result.append(layer).append('\n');
        }
        for (final Entity entity : entities) {
            result.append(entity).append('\n');
        }

        return result.toString();
    }

    public final class Head {
        //TODO: Static inner class to store tileset name, visibility type, and scroll type?

        public static final int NUM_REF_MAPS = 4;
        public static final int DESCRIPTION_MAX_LEN = 31;
        public static final int FILENAME_MAX_LEN = 15;

        static final String HEADER_STRING = "PXPACK121127a**\0";

        private String description;
        private final String[] mapnames;
        private String spritesheetName;

        private final byte[] data;
        private Color bgColor;

        private final String[] tilesetNames;
        private final byte[] visibilityTypes;
        private final byte[] scrollTypes;

        Head(final String description, final String[] mapnames, final String spritesheetName,
             final byte[] data, final Color bgColor, final String[] tilesetNames,
             final byte[] visibilityTypes, final byte[] scrollTypes) {
            if (NUM_REF_MAPS != mapnames.length) {
                throw new IllegalArgumentException("Attempt to set mapnames[] when arg has length " + mapnames.length +
                                                   " when length of " + NUM_REF_MAPS + " is expected " +
                                                   "(mapnames: " + Arrays.toString(mapnames) + ')');
            }
            if (5 != data.length) {
                throw new IllegalArgumentException("Attempt to set data[] when arg has length " + data.length +
                                                   " when length of 5 is expected " +
                                                   "(data: " + Arrays.toString(data) + ')');
            }
            if (NUM_LAYERS != tilesetNames.length) {
                throw new IllegalArgumentException("Attempt to set tilesetNames[] when arg has length " + tilesetNames.length +
                                                   " when length of " + NUM_LAYERS + " is expected " +
                                                   "(tilesetNames: " + Arrays.toString(tilesetNames) + ')');
            }
            if (NUM_LAYERS != visibilityTypes.length) {
                throw new IllegalArgumentException("Attempt to set visibilityTypes[] when arg has length " + visibilityTypes.length +
                                                   " when length of " + NUM_LAYERS + " is expected " +
                                                   "(visibilityTypes: " + Arrays.toString(visibilityTypes) + ')');
            }
            if (NUM_LAYERS != scrollTypes.length) {
                throw new IllegalArgumentException("Attempt to set scrollTypes[] when arg has length " + scrollTypes.length +
                                                   " when length of " + NUM_LAYERS + " is expected " +
                                                   "(scrollTypes: " + Arrays.toString(scrollTypes) + ')');
            }

            //TODO: check if these method calls are bad practice
            setDescription(description);

            this.mapnames = new String[NUM_REF_MAPS];
            for (int i = 0; i < this.mapnames.length; ++i) {
                setMapname(i, mapnames[i]);
            }

            setSpritesheetName(spritesheetName);

            this.data = new byte[5];
            for (int i = 0; i < this.data.length; ++i) {
                setData(i, data[i]);
            }

            setBgColor(bgColor);

            this.tilesetNames = new String[NUM_LAYERS];
            for (int i = 0; i < this.tilesetNames.length; ++i) {
                setTilesetName(i, tilesetNames[i]);
            }

            this.visibilityTypes = new byte[NUM_LAYERS];
            for (int i = 0; i < this.visibilityTypes.length; ++i) {
                setVisibilityType(i, visibilityTypes[i]);
            }

            this.scrollTypes = new byte[NUM_LAYERS];
            for (int i = 0; i < this.scrollTypes.length; ++i) {
                setScrollType(i, scrollTypes[i]);
            }
        }

        public String getDescription() {
            return description;
        }

        public String[] getMapNames() {
            return Arrays.copyOf(mapnames, mapnames.length);
        }

        public String getSpritesheetName() {
            return spritesheetName;
        }

        public byte[] getData() {
            return Arrays.copyOf(data, data.length);
        }

        public Color getBgColor() {
            return bgColor;
        }

        public String[] getTilesetNames() {
            return Arrays.copyOf(tilesetNames, tilesetNames.length);
        }

        public byte[] getVisibilityTypes() {
            return Arrays.copyOf(visibilityTypes, visibilityTypes.length);
        }

        public byte[] getScrollTypes() {
            return Arrays.copyOf(scrollTypes, scrollTypes.length);
        }

        public void setDescription(final String description) {
            NullArgumentException.Companion.requireNonNull(description, "setDescription", "description");
            if (description.length() > DESCRIPTION_MAX_LEN) {
                throw new IllegalArgumentException("Attempt to set description when arg has length " +
                                                   description.length() + " when max length is " + DESCRIPTION_MAX_LEN +
                                                   " (description: " + description + ')');
            }
            this.description = description;
        }

        public void setMapname(final int index, final String mapname) {
            NullArgumentException.Companion.requireNonNull(mapname, "setMapname", "mapname");
            if (mapname.length() > FILENAME_MAX_LEN) {
                throw new IllegalArgumentException("Attempt to set mapname when arg has length " +
                                                   mapname.length() + " when max length is " + FILENAME_MAX_LEN +
                                                   " (mapname: " + mapname + ')');
            }
            if (mapname.contains(" ")) {
                throw new IllegalArgumentException("Attempt to set mapname when arg has spaces when spaces are not allowed " +
                                                   "(mapname: " + mapname + ')');
            }
            if (0 > index || mapnames.length <= index) {
                throw new ArrayIndexOutOfBoundsException("Attempt to set mapname when index arg is out of bounds " +
                                                         "(index: " + index + ')');
            }
            this.mapnames[index] = mapname;
        }

        public void setSpritesheetName(final String spritesheetName) {
            NullArgumentException.Companion.requireNonNull(spritesheetName, "setSpritesheetName", "spritesheetName");
            if (spritesheetName.length() > FILENAME_MAX_LEN) {
                throw new IllegalArgumentException("Attempt to set spritesheetName when arg has length " +
                                                   spritesheetName.length() + " when max length is " + FILENAME_MAX_LEN +
                                                   " (spritesheetName: " + spritesheetName + ')');
            }
            if (spritesheetName.contains(" ")) {
                throw new IllegalArgumentException("Attempt to set spritesheetName when arg has spaces when spaces are not allowed " +
                                                   "(spritesheetName: " + spritesheetName + ')');
            }
            this.spritesheetName = spritesheetName;
        }

        public void setData(final int index, final byte unknownByte) {
            if (0 > index || data.length <= index) {
                throw new ArrayIndexOutOfBoundsException("Attempt to set data when index arg is out of bounds " +
                                                         "(index: " + index + ')');
            }

            //TODO: validate (how?)
            data[index] = unknownByte;
        }

        public void setBgColor(final Color color) {
            NullArgumentException.Companion.requireNonNull(color, "setBgColor", "color");
            //TODO: verify kero blaster doesn't support bg opacity
            if (!color.isOpaque()) {
                throw new IllegalArgumentException("Attempt to set background color to non-opaque color " +
                                                   "(color: " + color + ')');
            }
            bgColor = color;
        }

        public void setTilesetName(final int index, final String tilesetName) {
            NullArgumentException.Companion.requireNonNull(tilesetName, "setTilesetName", "tilesetName");
            //only first is required
            if (0 == index && tilesetName.isEmpty()) {
                throw new IllegalArgumentException("Attempt to set first tilesetName to empty string");
            }
            if (tilesetName.length() > FILENAME_MAX_LEN) {
                throw new IllegalArgumentException("Attempt to set tilesetName when arg has length " +
                                                   tilesetName.length() + " when max length is " + FILENAME_MAX_LEN +
                                                   " (tilesetName: " + tilesetName + ')');
            }
            if (tilesetName.contains(" ")) {
                throw new IllegalArgumentException("Attempt to set tilesetName when arg has spaces when spaces are not allowed " +
                                                   "(tilesetName: " + tilesetName + ')');
            }
            if (0 > index || tilesetNames.length <= index) {
                throw new ArrayIndexOutOfBoundsException("Attempt to set tilesetName when index arg is out of bounds " +
                                                         "(index: " + index + ')');
            }
            this.tilesetNames[index] = tilesetName;
        }

        public void setVisibilityType(final int index, final byte visibilityType) {
            if (0 > visibilityType || 32 < visibilityType) {
                throw new IllegalArgumentException("Attempt to set visibilityType to value outside range 0 - 32" +
                                                   "(visibilityType: " + visibilityType + ')');
            }
            if (0 > index || visibilityTypes.length <= index) {
                throw new ArrayIndexOutOfBoundsException("Attempt to set visibilityType when index arg is out of bounds " +
                                                         "(index: " + index + ')');
            }
            visibilityTypes[index] = visibilityType;
        }

        public void setScrollType(final int index, final byte scrollType) {
            if (0 > scrollType || 9 < scrollType) {
                throw new IllegalArgumentException("Attempt to set scrollType to value outside range 0 - 9 " +
                                                   "(scrollType: " + scrollType + ')');
            }
            if (0 > index || scrollTypes.length <= index) {
                throw new ArrayIndexOutOfBoundsException("Attempt to set scrollType when index arg is out of bounds " +
                                                         "(index: " + index + ')');
            }
            scrollTypes[index] = scrollType;
        }

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder();

            result.append(String.format("Description: %s\n", description));

            for (int i = 0; i < mapnames.length; ++i) {
                result.append(String.format("Mapname %d: %s\n", i, mapnames[i]));
            }

            result.append(String.format("Spritesheet Name: %s\n", spritesheetName));

            for (int i = 0; i < data.length; ++i) {
                result.append(String.format("Byte %d: %02X", i, data[i]));
            }

            result.append(String.format("Background Color: %s\n", bgColor));

            for (int i = 0; i < tilesetNames.length; ++i) {
                result.append(String.format("Tileset Name %d: %s\n", i, tilesetNames[i]));
            }

            return result.toString();
        }
    }

    public final class TileLayer {
        static final String HEADER_STRING = "pxMAP01\0";

        private int[][] tiles;

        TileLayer() {
            tiles = null;
        }

        TileLayer(final int[][] tiles) {
            if (null != tiles && 0 < tiles.length) {
                final int height = tiles.length;
                final int width = tiles[0].length;
                if (0xFFFF < width || 0xFFFF < height) {
                    throw new IllegalArgumentException("Attempt to create tile layer with dimensions greater than max of 65,535 " +
                                                       "(width: " + width + ", height: " + height + ')');
                }

                this.tiles = new int[height][width];
                for (int y = 0; y < height; ++y) {
                    System.arraycopy(tiles[y], 0, this.tiles[y], 0, width);
                }
            }
        }

        /**
         * Resizes the layer's dimensions
         *
         * @param width The new width for the layer
         * @param height The new height for the layer
         */
        public void resize(final int width, final int height) {
            if (0 > width || 0 > height) {
                throw new IllegalArgumentException("Attempt to resize tile layer to have negative dimensions " +
                                                   "(width: " + width + ", height: " + height + ')');
            }
            if (0xFFFF < width || 0xFFFF < height) {
                throw new IllegalArgumentException("Attempt to resize tile layer to have dimensions greater than max of 65,535 " +
                                                   "(width: " + width + ", height: " + height + ')');
            }

            if (null == tiles) {
                tiles = new int[height][width];
                return;
            }

            if (width == tiles[0].length && height == tiles.length) {
                return;
            }

            if (0 == width || 0 == height) {
                tiles = null;
                return;
            }

            final int[][] oldTiles = new int[tiles.length][tiles[0].length];
            for (int y = 0; y < tiles.length; ++y) {
                System.arraycopy(tiles[y], 0, oldTiles[y], 0, tiles[y].length);
            }

            tiles = new int[height][width];

            //loop & copy for each index in smaller dimension
            int yBound = height < oldTiles.length ? height : oldTiles.length;
            int xBound = width < oldTiles[0].length ? width : oldTiles[0].length;

            for (int y = 0; y < yBound; ++y) {
                System.arraycopy(oldTiles[y], 0, tiles[y], 0, xBound);
            }
        }

        public int[][] getTiles() {
            if (null != tiles) {
                final int[][] tilesCopy = new int[tiles.length][tiles[0].length];
                for (int y = 0; y < tiles.length; ++y) {
                    System.arraycopy(tiles[y], 0, tilesCopy[y], 0, tiles[y].length);
                }
                return tilesCopy;
            }
            return null;
        }

        public void setTile(final int x, final int y, final int tile) {
            if (0 > tile || 0xFF < tile) {
                throw new IllegalArgumentException("Attempt to set tile at (" + x + ", " + y + ") " +
                                                   "to value outside range 0 - 255 (tile: " + tile + ')');
            }
            tiles[y][x] = tile;
        }

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder();

            if (null == tiles) {
                result.append("Width: 00\n")
                      .append("Height: 00\n");
            }
            else {
                result.append(String.format("\tWidth: %02X\n", tiles[0].length))
                      .append(String.format("\tHeight: %02X\n", tiles.length));
                for (final int[] row : tiles) {
                    result.append('\t');
                    for (final int tile : row) {
                        result.append(String.format("%02X ", tile));
                    }
                    result.append('\n');
                }
            }
            return result.toString();
        }
    }

    public final class Entity {
        public static final int NAME_MAX_LEN = 15;

        private static final int NUM_TYPES = 175;

        private int type;
        private byte flag;
        private byte unknownByte;
        private int x, y;
        private final byte[] data;
        private String name;

        Entity(final byte flag, final int type, final byte unknownByte, final int x, final int y,
               final byte[] data, final String name) {
            if (data.length != 2) {
                throw new IllegalArgumentException("Attempt to assign data[] when arg has length " + data.length +
                                                   " when length of 2 is expected");
            }

            setFlag(flag);
            setType(type);
            setUnknownByte(unknownByte);
            setCoordinates(x, y);

            this.data = new byte[2];
            for (int i = 0; i < this.data.length; ++i) {
                setData(i, data[i]);
            }
            setName(name);
        }

        public byte getFlag() {
            return flag;
        }

        public int getType() {
            return type;
        }

        public byte getUnknownByte() {
            return unknownByte;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public byte[] getData() {
            return Arrays.copyOf(data, data.length);
        }

        public String getName() {
            return name;
        }

        public void setFlag(final byte flag) {
            this.flag = flag;
        }

        public void setType(final int type) {
            if (0 > type || 0xFF < type/* || NUM_TYPES <= type*/) {
                //an entity type in 00title is 177 but unittype.txt only has 175 entities...
                /*throw new IllegalArgumentException("Attempt to set type to value outside range 0 - " + (NUM_TYPES - 1) +
                                                   " (type: " + type + ')');*/
                throw new IllegalArgumentException("Attempt to set type to value outside range 0 - 255 " +
                                                   "(type: " + type + ')');
            }
            this.type = type;
        }

        public void setUnknownByte(final byte unknownByte) {
            this.unknownByte = unknownByte;
        }

        public void setX(final int x) {
            if (0 > x || 0xFFFF < x) {
                throw new IllegalArgumentException("Attempt to set x coordinate to value outside range 0 - 65,535 " +
                                                   "(x: " + x + ')');
            }
            this.x = x;
        }

        public void setY(final int y) {
            if (0 > y || 0xFFFF < y) {
                throw new IllegalArgumentException("Attempt to set y coordinate to value outside range 0 - 65,535 " +
                                                   "(y: " + y + ')');
            }
            this.y = y;
        }

        public void setCoordinates(final int x, final int y) {
            setX(x);
            setY(y);
        }

        public void setData(final int index, final byte data) {
            if (0 > index || this.data.length <= index) {
                throw new ArrayIndexOutOfBoundsException("Attempt to set data when index arg is out of bounds " +
                                                         "(index: " + index + ')');
            }
            this.data[index] = data;
        }

        public void setName(final String entityName) {
            NullArgumentException.Companion.requireNonNull(entityName, "setName", "entityName");
            if (entityName.length() > NAME_MAX_LEN) {
                throw new IllegalArgumentException("Attempt to set entityName when arg has length " +
                                                   entityName.length() + " when max length is " + NAME_MAX_LEN +
                                                   " (entityName: " + entityName + ')');
            }
            if (entityName.contains(" ")) {
                throw new IllegalArgumentException("Attempt to set entityName when arg has spaces when spaces are not allowed");
            }
            this.name = entityName;
        }

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder();

            result.append(String.format("Flag: %02X\n", flag))
                  .append(String.format("Type: %02X\n", type))
                  .append(String.format("Unknown Byte: %02X\n", unknownByte))

                  .append(String.format("X: %02X\n", x))
                  .append(String.format("Y: %02X\n", y));

            for (int i = 0; i < data.length; ++i) {
                result.append(String.format("Data %d: %02X\n", i, data[i]));
            }

            result.append(String.format("Name: %s\n", name));

            return result.toString();
        }
    }
}