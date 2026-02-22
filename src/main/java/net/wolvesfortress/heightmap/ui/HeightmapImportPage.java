package net.wolvesfortress.heightmap;

import com.hypixel.hytale.builtin.buildertools.BlockColorIndex;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.utils.PasteToolUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.common.util.StringUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserConfig;
import com.hypixel.hytale.server.core.ui.browser.FileBrowserEventData;
import com.hypixel.hytale.server.core.ui.browser.ServerFileBrowser;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

/**
 * HeightmapImportPage
 *
 * <p>Imports a grayscale or floating-point heightmap image and places blocks to form
 * a terrain profile inside the builder clipboard, ready to paste.
 *
 * <h3>Supported formats</h3>
 * <ul>
 *   <li>8-bit grayscale or RGB (PNG, BMP, JPEG – any format Java ImageIO reads)</li>
 *   <li>16-bit grayscale PNG (sampled via red-channel high byte)</li>
 *   <li>Raw 32-bit float little-endian binary ({@code .f32}) – single-channel, width×height</li>
 *   <li>Raw 16-bit float little-endian binary ({@code .f16}) – single-channel, width×height</li>
 * </ul>
 *
 * <h3>Import modes</h3>
 * <ul>
 *   <li>{@code HEIGHTMAP} – stacked column of blocks, one per unit of height</li>
 *   <li>{@code SURFACE} – single block per (X,Z) column at the derived Y level (hollow)</li>
 *   <li>{@code COLORMAP} – flat image-to-block colour match, like ImageImportPage (ignores height)</li>
 *   <li>{@code NORMALMAP} – derives a surface shape from a normal-map image</li>
 * </ul>
 */
public class HeightmapImportPage extends InteractiveCustomUIPage<HeightmapImportPage.PageData> {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String ASSET_PACK_SUB_PATH = "Server/Imports/Heightmaps";
    private static final int DEFAULT_HEIGHT_SCALE = 32;
    private static final int MIN_HEIGHT = 1;
    private static final int MAX_HEIGHT = 320;
    private static final int DEFAULT_MAX_SIZE = 256;
    private static final int MAX_MAX_SIZE = 1024;

    // ── State ──────────────────────────────────────────────────────────────────
    @Nonnull  private String heightmapPath  = "";
    @Nonnull  private String colormapPath   = "";
    private int  heightScale = DEFAULT_HEIGHT_SCALE;
    private int  maxSize     = DEFAULT_MAX_SIZE;
    @Nonnull  private String blockPattern   = "Rock_Stone";
    @Nonnull  private String importModeStr  = "heightmap";
    @Nonnull  private ImportMode importMode = ImportMode.HEIGHTMAP;
    @Nonnull  private String channelStr     = "luminance";
    @Nonnull  private Channel channel       = Channel.LUMINANCE;
    @Nonnull  private String originStr      = "bottom_center";
    @Nonnull  private Origin origin         = Origin.BOTTOM_CENTER;
    private boolean invertHeight  = false;
    private boolean smooth        = false;

    @Nullable private String  statusMessage;
    @Nullable private String  previewInfo;   // e.g. "4096×4096 → 256×32×256 (~524k blocks)"
    private boolean isError      = false;
    private boolean isProcessing = false;

    /** Which browser is currently open: 0 = none, 1 = heightmap, 2 = colormap */
    private int activeBrowser = 0;

    @Nonnull private final ServerFileBrowser heightmapBrowser;
    @Nonnull private final ServerFileBrowser colormapBrowser;

    // ── Construction ───────────────────────────────────────────────────────────

    public HeightmapImportPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);

        FileBrowserConfig hmConfig = FileBrowserConfig.builder()
                .listElementId("#BrowserPage #FileList")
                .searchInputId("#BrowserPage #SearchInput")
                .currentPathId("#BrowserPage #CurrentPath")
                .allowedExtensions(".png", ".bmp", ".jpg", ".jpeg", ".tga", ".f32", ".f16")
                .enableRootSelector(false)
                .enableSearch(true)
                .enableDirectoryNav(true)
                .maxResults(50)
                .assetPackMode(true, ASSET_PACK_SUB_PATH)
                .build();
        this.heightmapBrowser = new ServerFileBrowser(hmConfig);

        FileBrowserConfig cmConfig = FileBrowserConfig.builder()
                .listElementId("#ColormapBrowserPage #FileList")
                .searchInputId("#ColormapBrowserPage #SearchInput")
                .currentPathId("#ColormapBrowserPage #CurrentPath")
                .allowedExtensions(".png", ".bmp", ".jpg", ".jpeg")
                .enableRootSelector(false)
                .enableSearch(true)
                .enableDirectoryNav(true)
                .maxResults(50)
                .assetPackMode(true, ASSET_PACK_SUB_PATH)
                .build();
        this.colormapBrowser = new ServerFileBrowser(cmConfig);
    }

    // ── UI build ───────────────────────────────────────────────────────────────

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {

        cmd.append("Pages/HeightmapImportPage.ui");

        // Field values
        cmd.set("#HeightmapPath #Input.Value",  heightmapPath);
        cmd.set("#HeightScaleInput #Input.Value", heightScale);
        cmd.set("#MaxSizeInput #Input.Value",   maxSize);
        cmd.set("#BaseBlock #Input.Value",      blockPattern);
        cmd.set("#InvertContainer #InvertCheckbox #CheckBox.Value", invertHeight);
        cmd.set("#SmoothContainer #SmoothCheckbox #CheckBox.Value", smooth);

        // Import-mode dropdown
        List<DropdownEntryInfo> modeEntries = new ArrayList<>();
        modeEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.mode.heightmap"),  "heightmap"));
        modeEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.mode.surface"),    "surface"));
        modeEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.mode.colormap"),   "colormap"));
        modeEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.mode.normalmap"),  "normalmap"));
        cmd.set("#ImportModeInput #Input.Entries", modeEntries);
        cmd.set("#ImportModeInput #Input.Value",   importModeStr);

        // Channel dropdown
        List<DropdownEntryInfo> channelEntries = new ArrayList<>();
        channelEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.channel.luminance"), "luminance"));
        channelEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.channel.red"),       "red"));
        channelEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.channel.green"),     "green"));
        channelEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.channel.blue"),      "blue"));
        channelEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.heightmapImport.channel.alpha"),     "alpha"));
        cmd.set("#ChannelInput #Input.Entries", channelEntries);
        cmd.set("#ChannelInput #Input.Value",   channelStr);

        // Origin dropdown
        List<DropdownEntryInfo> originEntries = new ArrayList<>();
        originEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.origin.bottom_front_left"), "bottom_front_left"));
        originEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.origin.bottom_center"),     "bottom_center"));
        originEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.origin.center"),            "center"));
        originEntries.add(new DropdownEntryInfo(LocalizableString.fromMessageId("server.customUI.origin.top_center"),        "top_center"));
        cmd.set("#OriginInput #Input.Entries", originEntries);
        cmd.set("#OriginInput #Input.Value",   originStr);

        // Colormap section visibility
        boolean showColormap = importMode == ImportMode.COLORMAP || importMode == ImportMode.NORMALMAP;
        cmd.set("#ColormapPath.Visible", showColormap);
        cmd.set("#ColormapPath #Input.Value", colormapPath);

        updateStatus(cmd);
        updatePreview(cmd);

        // Event bindings – main form
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HeightmapPath #Input",  EventData.of("@HeightmapPath", "#HeightmapPath #Input.Value"),   false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HeightScaleInput #Input", EventData.of("@HeightScale",  "#HeightScaleInput #Input.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MaxSizeInput #Input",   EventData.of("@MaxSize",      "#MaxSizeInput #Input.Value"),       false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BaseBlock #Input",      EventData.of("@BlockPattern", "#BaseBlock #Input.Value"),          false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ImportModeInput #Input",EventData.of("@ImportMode",  "#ImportModeInput #Input.Value"),     false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ChannelInput #Input",   EventData.of("@Channel",     "#ChannelInput #Input.Value"),        false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#OriginInput #Input",    EventData.of("@Origin",      "#OriginInput #Input.Value"),         false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InvertContainer #InvertCheckbox #CheckBox", EventData.of("@Invert", "#InvertContainer #InvertCheckbox #CheckBox.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SmoothContainer #SmoothCheckbox #CheckBox", EventData.of("@Smooth", "#SmoothContainer #SmoothCheckbox #CheckBox.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ColormapPath #Input",   EventData.of("@ColormapPath","#ColormapPath #Input.Value"),        false);
        evt.addEventBinding(CustomUIEventBindingType.Activating,   "#ImportButton",          EventData.of("Import", "true"));
        evt.addEventBinding(CustomUIEventBindingType.Activating,   "#HeightmapPath #BrowseButton", EventData.of("Browse", "true"));
        evt.addEventBinding(CustomUIEventBindingType.Activating,   "#ColormapPath #BrowseButton",  EventData.of("BrowseColormap", "true"));

        // Page visibility
        cmd.set("#FormContainer.Visible",        activeBrowser == 0);
        cmd.set("#BrowserPage.Visible",          activeBrowser == 1);
        cmd.set("#ColormapBrowserPage.Visible",  activeBrowser == 2);

        if (activeBrowser == 1) buildHeightmapBrowserPage(cmd, evt);
        if (activeBrowser == 2) buildColormapBrowserPage(cmd, evt);
    }

    private void buildHeightmapBrowserPage(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        heightmapBrowser.buildSearchInput(cmd, evt);
        heightmapBrowser.buildCurrentPath(cmd);
        heightmapBrowser.buildFileList(cmd, evt);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BrowserPage #SelectButton", EventData.of("BrowserSelect",  "true"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BrowserPage #CancelButton", EventData.of("BrowserCancel",  "true"));
    }

    private void buildColormapBrowserPage(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        colormapBrowser.buildSearchInput(cmd, evt);
        colormapBrowser.buildCurrentPath(cmd);
        colormapBrowser.buildFileList(cmd, evt);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ColormapBrowserPage #SelectButton", EventData.of("ColormapBrowserSelect", "true"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ColormapBrowserPage #CancelButton", EventData.of("ColormapBrowserCancel", "true"));
    }

    private void updateStatus(@Nonnull UICommandBuilder cmd) {
        if (statusMessage != null) {
            cmd.set("#StatusText.Text",             statusMessage);
            cmd.set("#StatusText.Visible",          true);
            cmd.set("#StatusText.Style.TextColor",  isError ? "#e74c3c" : "#cfd8e3");
        } else {
            cmd.set("#StatusText.Visible", false);
        }
    }

    private void updatePreview(@Nonnull UICommandBuilder cmd) {
        if (previewInfo != null) {
            cmd.set("#PreviewInfo.Text",    previewInfo);
            cmd.set("#PreviewInfo.Visible", true);
        } else {
            cmd.set("#PreviewInfo.Visible", false);
        }
    }

    private void setError(@Nonnull String msg) {
        statusMessage  = msg;
        isError        = true;
        isProcessing   = false;
        rebuild();
    }

    private void setStatus(@Nonnull String msg) {
        statusMessage = msg;
        isError       = false;
        rebuild();
    }

    // ── Event handling ─────────────────────────────────────────────────────────

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {

        // ── Browser open ──────────────────────────────────────────────────
        if (Boolean.TRUE.equals(data.browse)) {
            activeBrowser = 1; rebuild(); return;
        }
        if (Boolean.TRUE.equals(data.browseColormap)) {
            activeBrowser = 2; rebuild(); return;
        }

        // ── Browser cancel ────────────────────────────────────────────────
        if (Boolean.TRUE.equals(data.browserCancel)) {
            activeBrowser = 0; rebuild(); return;
        }
        if (Boolean.TRUE.equals(data.colormapBrowserCancel)) {
            activeBrowser = 0; rebuild(); return;
        }

        // ── Browser select (close) ────────────────────────────────────────
        if (Boolean.TRUE.equals(data.browserSelect)) {
            activeBrowser = 0; rebuild(); return;
        }
        if (Boolean.TRUE.equals(data.colormapBrowserSelect)) {
            activeBrowser = 0; rebuild(); return;
        }

        // ── Browser file events ───────────────────────────────────────────
        if (activeBrowser == 1 && handleBrowserEvent(data, heightmapBrowser, true))  return;
        if (activeBrowser == 2 && handleBrowserEvent(data, colormapBrowser,  false)) return;

        // ── Main form field updates ───────────────────────────────────────
        boolean needsUpdate = false;

        if (data.heightmapPath != null) {
            heightmapPath = StringUtil.stripQuotes(data.heightmapPath.trim());
            statusMessage = null;
            previewInfo   = computePreviewInfo(heightmapPath);
            needsUpdate   = true;
        }
        if (data.colormapPath != null) {
            colormapPath = StringUtil.stripQuotes(data.colormapPath.trim());
            needsUpdate  = true;
        }
        if (data.heightScale != null) {
            heightScale  = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, data.heightScale));
            previewInfo  = computePreviewInfo(heightmapPath);
            needsUpdate  = true;
        }
        if (data.maxSize != null) {
            maxSize     = Math.max(1, Math.min(MAX_MAX_SIZE, data.maxSize));
            previewInfo = computePreviewInfo(heightmapPath);
            needsUpdate = true;
        }
        if (data.blockPattern != null) {
            blockPattern = data.blockPattern.trim();
            needsUpdate  = true;
        }
        if (data.importMode != null) {
            importModeStr = data.importMode.trim().toLowerCase();
            importMode = switch (importModeStr) {
                case "surface"   -> ImportMode.SURFACE;
                case "colormap"  -> ImportMode.COLORMAP;
                case "normalmap" -> ImportMode.NORMALMAP;
                default          -> ImportMode.HEIGHTMAP;
            };
            // Toggling mode may show/hide colormap path — full rebuild
            rebuild();
            return;
        }
        if (data.channel != null) {
            channelStr = data.channel.trim().toLowerCase();
            channel = switch (channelStr) {
                case "red"   -> Channel.RED;
                case "green" -> Channel.GREEN;
                case "blue"  -> Channel.BLUE;
                case "alpha" -> Channel.ALPHA;
                default      -> Channel.LUMINANCE;
            };
            needsUpdate = true;
        }
        if (data.origin != null) {
            originStr = data.origin.trim().toLowerCase();
            origin = switch (originStr) {
                case "bottom_front_left" -> Origin.BOTTOM_FRONT_LEFT;
                case "center"            -> Origin.CENTER;
                case "top_center"        -> Origin.TOP_CENTER;
                default                  -> Origin.BOTTOM_CENTER;
            };
            needsUpdate = true;
        }
        if (data.invert != null) {
            invertHeight = data.invert;
            needsUpdate  = true;
        }
        if (data.smooth != null) {
            smooth      = data.smooth;
            needsUpdate = true;
        }

        if (Boolean.TRUE.equals(data.doImport) && !isProcessing) {
            performImport(ref, store);
        } else if (needsUpdate) {
            sendUpdate();
        }
    }

    /**
     * Handles file/search events for whichever browser is open.
     * @param isHeightmap true = update {@code heightmapPath}, false = update {@code colormapPath}
     */
    private boolean handleBrowserEvent(@Nonnull PageData data,
                                       @Nonnull ServerFileBrowser browser,
                                       boolean isHeightmap) {
        if (data.searchQuery != null) {
            browser.setSearchQuery(data.searchQuery.trim().toLowerCase());
            rebuildBrowser(browser);
            return true;
        }
        if (data.file != null) {
            String fileName = data.file;
            if ("..".equals(fileName)) {
                browser.navigateUp();
                rebuildBrowser(browser);
                return true;
            }
            if (browser.handleEvent(FileBrowserEventData.file(fileName))) {
                rebuildBrowser(browser);
                return true;
            }
            String virtualPath = browser.getAssetPackCurrentPath().isEmpty()
                    ? fileName
                    : browser.getAssetPackCurrentPath() + "/" + fileName;
            Path resolved = browser.resolveAssetPackPath(virtualPath);
            if (resolved != null && Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
                if (isHeightmap) heightmapPath = resolved.toString();
                else             colormapPath  = resolved.toString();
                activeBrowser = 0;
                rebuild();
                return true;
            }
        }
        if (data.searchResult != null) {
            Path resolved = browser.resolveAssetPackPath(data.searchResult);
            if (resolved != null && Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
                if (isHeightmap) heightmapPath = resolved.toString();
                else             colormapPath  = resolved.toString();
                activeBrowser = 0;
                rebuild();
                return true;
            }
        }
        return false;
    }

    private void rebuildBrowser(@Nonnull ServerFileBrowser browser) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder   evt = new UIEventBuilder();
        browser.buildFileList(cmd, evt);
        browser.buildCurrentPath(cmd);
        sendUpdate(cmd, evt, false);
    }

    // ── Import logic ───────────────────────────────────────────────────────────

    private void performImport(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (heightmapPath.isEmpty()) {
            setError("Please enter a path to a heightmap file"); return;
        }
        Path path = Paths.get(heightmapPath);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            setError("File not found: " + heightmapPath); return;
        }

        List<WeightedBlock> blocks = parseBlockPattern(blockPattern);
        if (blocks == null) {
            setError("Invalid block pattern: " + blockPattern); return;
        }

        isProcessing = true;
        setStatus("Processing…");

        Player    playerComponent    = (Player)    store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        if (playerComponent == null || playerRefComponent == null) {
            setError("Player not found"); return;
        }

        // Capture final copies for the lambda
        final String    fHeightmapPath  = heightmapPath;
        final String    fColormapPath   = colormapPath;
        final int       fHeightScale    = heightScale;
        final int       fMaxSize        = maxSize;
        final ImportMode fMode          = importMode;
        final Channel   fChannel        = channel;
        final Origin    fOrigin         = origin;
        final boolean   fInvert         = invertHeight;
        final boolean   fSmooth         = smooth;

        BuilderToolsPlugin.addToQueue(playerComponent, playerRefComponent,
                (r, builderState, componentAccessor) -> {
                    try {
                        // 1) Load height values [0..1] for each (x,z) pixel
                        float[][] heights = loadHeightData(Paths.get(fHeightmapPath), fChannel, fInvert);
                        if (heights == null) {
                            setError("Unable to read heightmap (unsupported format or corrupted)."); return;
                        }

                        int rawW = heights[0].length;
                        int rawH = heights.length;   // z-axis

                        // 2) Downscale if necessary
                        float scaleXZ = 1.0f;
                        int W = rawW, H = rawH;
                        if (rawW > fMaxSize || rawH > fMaxSize) {
                            scaleXZ = (float) fMaxSize / Math.max(rawW, rawH);
                            W = Math.round(rawW * scaleXZ);
                            H = Math.round(rawH * scaleXZ);
                        }

                        // 3) Optional smooth pass (box-blur 3×3 on raw heights)
                        if (fSmooth) heights = boxBlur(heights);

                        // 4) Optional colormap for COLORMAP / NORMALMAP modes
                        BlockColorIndex colorIndex = BuilderToolsPlugin.get().getBlockColorIndex();
                        BufferedImage colormapImage = null;
                        if ((fMode == ImportMode.COLORMAP || fMode == ImportMode.NORMALMAP)
                                && !fColormapPath.isEmpty()) {
                            try { colormapImage = ImageIO.read(Paths.get(fColormapPath).toFile()); }
                            catch (Exception ignored) { /* fall through – use block pattern */ }
                        }

                        // 5) Build the block selection
                        Random random = new Random();
                        int totalBlocks = estimateBlockCount(fMode, W, H, fHeightScale);
                        BlockSelection selection = new BlockSelection(totalBlocks, 0);
                        selection.setPosition(0, 0, 0);

                        int sizeX = W;
                        int sizeY = (fMode == ImportMode.COLORMAP) ? 1 : fHeightScale;
                        int sizeZ = H;

                        int blockCount = 0;

                        for (int iz = 0; iz < H; iz++) {
                            for (int ix = 0; ix < W; ix++) {
                                // Sample from original array (nearest-neighbour)
                                int srcX = Math.min((int)(ix / scaleXZ), rawW - 1);
                                int srcZ = Math.min((int)(iz / scaleXZ), rawH - 1);
                                float hNorm = heights[srcZ][srcX];

                                int blockId = resolveBlockId(fMode, fColormapPath, colormapImage,
                                        colorIndex, blocks, random, srcX, srcZ, rawW, rawH, hNorm);
                                if (blockId <= 0) continue;

                                switch (fMode) {
                                    case HEIGHTMAP -> {
                                        // Stack of blocks from y=0 up to computed height
                                        int colH = Math.max(1, Math.round(hNorm * fHeightScale));
                                        for (int iy = 0; iy < colH; iy++) {
                                            selection.addBlockAtLocalPos(ix, iy, iz, blockId, 0, 0, 0);
                                            blockCount++;
                                        }
                                    }
                                    case SURFACE, NORMALMAP -> {
                                        // Single block at the surface height
                                        int iy = Math.max(0, Math.round(hNorm * fHeightScale) - 1);
                                        selection.addBlockAtLocalPos(ix, iy, iz, blockId, 0, 0, 0);
                                        blockCount++;
                                    }
                                    case COLORMAP -> {
                                        // Flat — height value not used
                                        int rgba  = colormapImage != null
                                                ? colormapImage.getRGB(Math.min(srcX, colormapImage.getWidth()-1),
                                                Math.min(srcZ, colormapImage.getHeight()-1))
                                                : 0;
                                        int alpha = (rgba >> 24) & 0xFF;
                                        if (colormapImage == null || alpha >= 128) {
                                            selection.addBlockAtLocalPos(ix, 0, iz, blockId, 0, 0, 0);
                                            blockCount++;
                                        }
                                    }
                                }
                            }
                        }

                        // 6) Apply origin offset
                        int offX = 0, offY = 0, offZ = 0;
                        switch (fOrigin) {
                            case BOTTOM_FRONT_LEFT -> { /* 0,0,0 */ }
                            case BOTTOM_CENTER     -> { offX = -sizeX / 2; offZ = -sizeZ / 2; }
                            case CENTER            -> { offX = -sizeX / 2; offY = -sizeY / 2; offZ = -sizeZ / 2; }
                            case TOP_CENTER        -> { offX = -sizeX / 2; offY = -sizeY;      offZ = -sizeZ / 2; }
                        }

                        selection.setSelectionArea(
                                new Vector3i(offX, offY, offZ),
                                new Vector3i(sizeX - 1 + offX, sizeY - 1 + offY, sizeZ - 1 + offZ));
                        builderState.setSelection(selection);
                        builderState.sendSelectionToClient();

                        isProcessing  = false;
                        statusMessage = String.format("Success! %d blocks copied to clipboard (%dx%dx%d)",
                                blockCount, sizeX, sizeY, sizeZ);

                        playerRefComponent.sendMessage(
                                Message.translation("server.heightmapMod.heightmapImport.success")
                                        .param("count",  blockCount)
                                        .param("width",  sizeX)
                                        .param("height", sizeY)
                                        .param("depth",  sizeZ));

                        playerComponent.getPageManager().setPage(r, store, Page.None);
                        PasteToolUtil.switchToPasteTool(playerComponent, playerRefComponent);

                    } catch (Exception e) {
                        ((HytaleLogger.Api) BuilderToolsPlugin.get().getLogger()
                                .at(Level.WARNING).withCause(e))
                                .log("Heightmap import error");
                        setError("Error: " + e.getMessage());
                    }
                });
    }

    // ── Preview computation ────────────────────────────────────────────────────

    /**
     * Quickly reads image dimensions without decoding all pixels, then computes
     * the effective structure size and estimated block count based on current settings.
     * Returns null if the path is empty or the file can't be read.
     */
    @Nullable
    private String computePreviewInfo(@Nonnull String path) {
        if (path.isEmpty()) return null;
        Path p = Paths.get(path);
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return null;

        int[] dims = readImageDimensions(p);
        if (dims == null) return null;

        int rawW = dims[0], rawH = dims[1];

        // Compute effective XZ after maxSize cap
        float scale = 1.0f;
        int effW = rawW, effH = rawH;
        if (rawW > maxSize || rawH > maxSize) {
            scale = (float) maxSize / Math.max(rawW, rawH);
            effW  = Math.round(rawW * scale);
            effH  = Math.round(rawH * scale);
        }

        // Estimate block count per mode
        long estBlocks = switch (importMode) {
            case HEIGHTMAP -> (long) effW * effH * (heightScale / 2); // avg ~50% fill
            case SURFACE, NORMALMAP -> (long) effW * effH;
            case COLORMAP -> (long) effW * effH;
        };

        String sizeLabel = switch (importMode) {
            case COLORMAP -> String.format("%d×1×%d", effW, effH);
            default       -> String.format("%d×%d×%d", effW, heightScale, effH);
        };

        String scaleNote = (scale < 1.0f)
                ? String.format(" (downscaled from %d×%d)", rawW, rawH)
                : "";

        return String.format("%s%s  ~%s blocks",
                sizeLabel, scaleNote, formatCount(estBlocks));
    }

    /** Fast dimension read via ImageIO reader — no pixel decoding. */
    @Nullable
    private static int[] readImageDimensions(@Nonnull Path path) {
        String name = path.getFileName().toString().toLowerCase();

        // Raw float files: square assumed, derive from byte count
        if (name.endsWith(".f32")) {
            try {
                long bytes = Files.size(path);
                int side = (int) Math.round(Math.sqrt(bytes / 4.0));
                if ((long) side * side * 4 == bytes) return new int[]{side, side};
            } catch (Exception ignored) {}
            return null;
        }
        if (name.endsWith(".f16")) {
            try {
                long bytes = Files.size(path);
                int side = (int) Math.round(Math.sqrt(bytes / 2.0));
                if ((long) side * side * 2 == bytes) return new int[]{side, side};
            } catch (Exception ignored) {}
            return null;
        }

        // Standard image formats — use ImageReader to avoid full decode
        try (var stream = javax.imageio.ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) return null;
            var readers = javax.imageio.ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatCount(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }

    // ── Height-data loading ────────────────────────────────────────────────────

    /**
     * Loads the file and returns a 2-D float array {@code [z][x]} with values normalised to [0,1].
     * Supports: standard images (8/16-bit), raw .f32 and .f16.
     */
    @Nullable
    private static float[][] loadHeightData(@Nonnull Path path, @Nonnull Channel channel, boolean invert) {
        String name = path.getFileName().toString().toLowerCase();

        float[][] result;
        if (name.endsWith(".f32")) {
            result = loadRawFloat32(path);
        } else if (name.endsWith(".f16")) {
            result = loadRawFloat16(path);
        } else {
            result = loadImageHeights(path, channel);
        }

        if (result == null) return null;
        if (invert) {
            for (float[] row : result)
                for (int i = 0; i < row.length; i++)
                    row[i] = 1.0f - row[i];
        }
        return result;
    }

    /** Read a standard image file and extract height from the chosen channel. */
    @Nullable
    private static float[][] loadImageHeights(@Nonnull Path path, @Nonnull Channel channel) {
        BufferedImage img;
        try { img = ImageIO.read(path.toFile()); } catch (Exception e) { return null; }
        if (img == null) return null;

        int W = img.getWidth(), H = img.getHeight();
        float[][] out = new float[H][W];

        boolean hasAlpha = img.getColorModel().hasAlpha();
        for (int z = 0; z < H; z++) {
            for (int x = 0; x < W; x++) {
                int rgba = img.getRGB(x, z);
                float v = switch (channel) {
                    case RED       -> ((rgba >> 16) & 0xFF) / 255.0f;
                    case GREEN     -> ((rgba >>  8) & 0xFF) / 255.0f;
                    case BLUE      -> ( rgba        & 0xFF) / 255.0f;
                    case ALPHA     -> hasAlpha ? ((rgba >> 24) & 0xFF) / 255.0f : 1.0f;
                    case LUMINANCE -> {
                        float r = ((rgba >> 16) & 0xFF) / 255.0f;
                        float g = ((rgba >>  8) & 0xFF) / 255.0f;
                        float b = ( rgba        & 0xFF) / 255.0f;
                        yield 0.2126f * r + 0.7152f * g + 0.0722f * b; // ITU-R BT.709
                    }
                };
                out[z][x] = Math.max(0.0f, Math.min(1.0f, v));
            }
        }
        return out;
    }

    /** Read a raw little-endian 32-bit float binary file (width×height float values). */
    @Nullable
    private static float[][] loadRawFloat32(@Nonnull Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            int count = bytes.length / 4;
            int side  = (int) Math.round(Math.sqrt(count));
            if (side * side != count) return null; // must be square for auto-detect

            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            float[] raw = new float[count];
            for (int i = 0; i < count; i++) {
                raw[i] = buf.getFloat();
                if (raw[i] < min) min = raw[i];
                if (raw[i] > max) max = raw[i];
            }
            float range = max - min;
            if (range == 0) range = 1;

            float[][] out = new float[side][side];
            for (int z = 0; z < side; z++)
                for (int x = 0; x < side; x++)
                    out[z][x] = (raw[z * side + x] - min) / range;
            return out;
        } catch (Exception e) { return null; }
    }

    /** Read a raw little-endian 16-bit float binary file (IEEE 754 half-precision). */
    @Nullable
    private static float[][] loadRawFloat16(@Nonnull Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            int count = bytes.length / 2;
            int side  = (int) Math.round(Math.sqrt(count));
            if (side * side != count) return null;

            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            float[] raw = new float[count];
            for (int i = 0; i < count; i++) {
                raw[i] = halfToFloat(buf.getShort() & 0xFFFF);
                if (raw[i] < min) min = raw[i];
                if (raw[i] > max) max = raw[i];
            }
            float range = max - min;
            if (range == 0) range = 1;

            float[][] out = new float[side][side];
            for (int z = 0; z < side; z++)
                for (int x = 0; x < side; x++)
                    out[z][x] = (raw[z * side + x] - min) / range;
            return out;
        } catch (Exception e) { return null; }
    }

    /** Convert IEEE 754 half-precision (16-bit) to single-precision float. */
    private static float halfToFloat(int half) {
        int sign     = (half >> 15) & 0x1;
        int exponent = (half >> 10) & 0x1F;
        int mantissa =  half        & 0x3FF;

        int f;
        if (exponent == 0) {
            if (mantissa == 0) { f = sign << 31; }
            else {
                while ((mantissa & 0x400) == 0) { mantissa <<= 1; exponent--; }
                exponent++;
                mantissa &= ~0x400;
                f = (sign << 31) | ((exponent + (127 - 15)) << 23) | (mantissa << 13);
            }
        } else if (exponent == 31) {
            f = mantissa == 0
                    ? (sign << 31) | 0x7F800000          // ±Inf
                    : (sign << 31) | 0x7FC00000 | (mantissa << 13); // NaN
        } else {
            f = (sign << 31) | ((exponent + (127 - 15)) << 23) | (mantissa << 13);
        }
        return Float.intBitsToFloat(f);
    }

    /** Simple 3×3 box-blur smoothing pass. */
    @Nonnull
    private static float[][] boxBlur(@Nonnull float[][] src) {
        int H = src.length, W = src[0].length;
        float[][] dst = new float[H][W];
        for (int z = 0; z < H; z++) {
            for (int x = 0; x < W; x++) {
                float sum = 0; int n = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nz = z + dz, nx = x + dx;
                        if (nz >= 0 && nz < H && nx >= 0 && nx < W) { sum += src[nz][nx]; n++; }
                    }
                }
                dst[z][x] = sum / n;
            }
        }
        return dst;
    }

    // ── Block-resolution helpers ───────────────────────────────────────────────

    private int resolveBlockId(@Nonnull ImportMode mode,
                               @Nonnull String cmPath,
                               @Nullable BufferedImage cmImage,
                               @Nonnull BlockColorIndex colorIndex,
                               @Nonnull List<WeightedBlock> blocks,
                               @Nonnull Random random,
                               int srcX, int srcZ, int rawW, int rawH,
                               float hNorm) {

        return switch (mode) {
            case COLORMAP, NORMALMAP -> {
                if (cmImage != null) {
                    int cx   = Math.min(srcX, cmImage.getWidth()  - 1);
                    int cz   = Math.min(srcZ, cmImage.getHeight() - 1);
                    int rgba = cmImage.getRGB(cx, cz);
                    int r    = (rgba >> 16) & 0xFF;
                    int g    = (rgba >>  8) & 0xFF;
                    int b    =  rgba        & 0xFF;
                    int id   = colorIndex.findClosestBlock(r, g, b);
                    yield (id > 0) ? id : selectRandomBlock(blocks, random);
                }
                yield selectRandomBlock(blocks, random);
            }
            default -> selectRandomBlock(blocks, random);
        };
    }

    @Nullable
    private static List<WeightedBlock> parseBlockPattern(@Nonnull String pattern) {
        List<WeightedBlock> result = new ArrayList<>();
        for (String part : pattern.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;

            int weight    = 100;
            String bName  = part;
            int pctIdx    = part.indexOf('%');
            if (pctIdx > 0) {
                try {
                    weight = Integer.parseInt(part.substring(0, pctIdx).trim());
                    bName  = part.substring(pctIdx + 1).trim();
                } catch (NumberFormatException e) { return null; }
            }
            int id = BlockType.getAssetMap().getIndex(bName);
            if (id == Integer.MIN_VALUE) return null;
            result.add(new WeightedBlock(id, weight));
        }
        return result.isEmpty() ? null : result;
    }

    private static int selectRandomBlock(@Nonnull List<WeightedBlock> blocks, @Nonnull Random rng) {
        if (blocks.size() == 1) return blocks.get(0).blockId();
        int total = blocks.stream().mapToInt(WeightedBlock::weight).sum();
        if (total <= 0) return blocks.get(0).blockId();
        int roll = rng.nextInt(total), cumulative = 0;
        for (WeightedBlock wb : blocks) {
            cumulative += wb.weight();
            if (roll < cumulative) return wb.blockId();
        }
        return blocks.get(0).blockId();
    }

    /** Rough upper-bound estimate for pre-allocating BlockSelection. */
    private static int estimateBlockCount(ImportMode mode, int W, int H, int maxY) {
        return switch (mode) {
            case HEIGHTMAP -> W * H * maxY;     // worst case
            case COLORMAP, SURFACE, NORMALMAP -> W * H;
        };
    }

    // ── Inner types ────────────────────────────────────────────────────────────

    private record WeightedBlock(int blockId, int weight) {}

    public enum ImportMode {
        /** Solid terrain column, height derived from pixel brightness. */
        HEIGHTMAP,
        /** Single surface block per column. */
        SURFACE,
        /** Flat image → block-colour match (like ImageImportPage). */
        COLORMAP,
        /** Normal-map image drives surface normals; colour used for block match. */
        NORMALMAP
    }

    public enum Channel {
        LUMINANCE, RED, GREEN, BLUE, ALPHA
    }

    public enum Origin {
        BOTTOM_FRONT_LEFT, BOTTOM_CENTER, CENTER, TOP_CENTER
    }

    // ── PageData / Codec ───────────────────────────────────────────────────────

    public static class PageData {
        // Key constants
        static final String KEY_HEIGHTMAP_PATH  = "@HeightmapPath";
        static final String KEY_COLORMAP_PATH   = "@ColormapPath";
        static final String KEY_HEIGHT_SCALE    = "@HeightScale";
        static final String KEY_MAX_SIZE        = "@MaxSize";
        static final String KEY_BLOCK_PATTERN   = "@BlockPattern";
        static final String KEY_IMPORT_MODE     = "@ImportMode";
        static final String KEY_CHANNEL         = "@Channel";
        static final String KEY_ORIGIN          = "@Origin";
        static final String KEY_INVERT          = "@Invert";
        static final String KEY_SMOOTH          = "@Smooth";
        static final String KEY_IMPORT          = "Import";
        static final String KEY_BROWSE          = "Browse";
        static final String KEY_BROWSE_COLORMAP = "BrowseColormap";
        static final String KEY_BROWSER_SELECT  = "BrowserSelect";
        static final String KEY_BROWSER_CANCEL  = "BrowserCancel";
        static final String KEY_CM_BROWSER_SELECT = "ColormapBrowserSelect";
        static final String KEY_CM_BROWSER_CANCEL = "ColormapBrowserCancel";

        public static final BuilderCodec<PageData> CODEC;

        @Nullable String  heightmapPath;
        @Nullable String  colormapPath;
        @Nullable Integer heightScale;
        @Nullable Integer maxSize;
        @Nullable String  blockPattern;
        @Nullable String  importMode;
        @Nullable String  channel;
        @Nullable String  origin;
        @Nullable Boolean invert;
        @Nullable Boolean smooth;
        @Nullable Boolean doImport;
        @Nullable Boolean browse;
        @Nullable Boolean browseColormap;
        @Nullable Boolean browserSelect;
        @Nullable Boolean browserCancel;
        @Nullable Boolean colormapBrowserSelect;
        @Nullable Boolean colormapBrowserCancel;
        @Nullable String  file;
        @Nullable String  searchQuery;
        @Nullable String  searchResult;

        static {
            CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                    .addField(new KeyedCodec(KEY_HEIGHTMAP_PATH,    Codec.STRING),  (e, s) -> ((PageData) e).heightmapPath         = (String)  s,                                      e -> ((PageData) e).heightmapPath)
                    .addField(new KeyedCodec(KEY_COLORMAP_PATH,     Codec.STRING),  (e, s) -> ((PageData) e).colormapPath          = (String)  s,                                      e -> ((PageData) e).colormapPath)
                    .addField(new KeyedCodec(KEY_HEIGHT_SCALE,      Codec.INTEGER), (e, i) -> ((PageData) e).heightScale           = (Integer) i,                                      e -> ((PageData) e).heightScale)
                    .addField(new KeyedCodec(KEY_MAX_SIZE,          Codec.INTEGER), (e, i) -> ((PageData) e).maxSize               = (Integer) i,                                      e -> ((PageData) e).maxSize)
                    .addField(new KeyedCodec(KEY_BLOCK_PATTERN,     Codec.STRING),  (e, s) -> ((PageData) e).blockPattern          = (String)  s,                                      e -> ((PageData) e).blockPattern)
                    .addField(new KeyedCodec(KEY_IMPORT_MODE,       Codec.STRING),  (e, s) -> ((PageData) e).importMode            = (String)  s,                                      e -> ((PageData) e).importMode)
                    .addField(new KeyedCodec(KEY_CHANNEL,           Codec.STRING),  (e, s) -> ((PageData) e).channel               = (String)  s,                                      e -> ((PageData) e).channel)
                    .addField(new KeyedCodec(KEY_ORIGIN,            Codec.STRING),  (e, s) -> ((PageData) e).origin                = (String)  s,                                      e -> ((PageData) e).origin)
                    .addField(new KeyedCodec(KEY_INVERT,            Codec.BOOLEAN), (e, b) -> ((PageData) e).invert                = (Boolean) b,                                      e -> ((PageData) e).invert)
                    .addField(new KeyedCodec(KEY_SMOOTH,            Codec.BOOLEAN), (e, b) -> ((PageData) e).smooth                = (Boolean) b,                                      e -> ((PageData) e).smooth)
                    .addField(new KeyedCodec(KEY_IMPORT,            Codec.STRING),  (e, s) -> ((PageData) e).doImport              = "true".equalsIgnoreCase((String) s),               e -> Boolean.TRUE.equals(((PageData) e).doImport)              ? "true" : null)
                    .addField(new KeyedCodec(KEY_BROWSE,            Codec.STRING),  (e, s) -> ((PageData) e).browse                = "true".equalsIgnoreCase((String) s),               e -> Boolean.TRUE.equals(((PageData) e).browse)                ? "true" : null)
                    .addField(new KeyedCodec(KEY_BROWSE_COLORMAP,   Codec.STRING),  (e, s) -> ((PageData) e).browseColormap        = "true".equalsIgnoreCase((String) s),               e -> Boolean.TRUE.equals(((PageData) e).browseColormap)        ? "true" : null)
                    .addField(new KeyedCodec(KEY_BROWSER_SELECT,    Codec.STRING),  (e, s) -> ((PageData) e).browserSelect         = "true".equalsIgnoreCase((String) s),               e -> Boolean.TRUE.equals(((PageData) e).browserSelect)         ? "true" : null)
                    .addField(new KeyedCodec(KEY_BROWSER_CANCEL,    Codec.STRING),  (e, s) -> ((PageData) e).browserCancel         = "true".equalsIgnoreCase((String) s),               e -> Boolean.TRUE.equals(((PageData) e).browserCancel)         ? "true" : null)
                    .addField(new KeyedCodec(KEY_CM_BROWSER_SELECT, Codec.STRING),  (e, s) -> ((PageData) e).colormapBrowserSelect  = "true".equalsIgnoreCase((String) s),              e -> Boolean.TRUE.equals(((PageData) e).colormapBrowserSelect)  ? "true" : null)
                    .addField(new KeyedCodec(KEY_CM_BROWSER_CANCEL, Codec.STRING),  (e, s) -> ((PageData) e).colormapBrowserCancel  = "true".equalsIgnoreCase((String) s),              e -> Boolean.TRUE.equals(((PageData) e).colormapBrowserCancel)  ? "true" : null)
                    .addField(new KeyedCodec("File",                Codec.STRING),  (e, s) -> ((PageData) e).file                  = (String)  s,                                      e -> ((PageData) e).file)
                    .addField(new KeyedCodec("@SearchQuery",        Codec.STRING),  (e, s) -> ((PageData) e).searchQuery           = (String)  s,                                      e -> ((PageData) e).searchQuery)
                    .addField(new KeyedCodec("SearchResult",        Codec.STRING),  (e, s) -> ((PageData) e).searchResult          = (String)  s,                                      e -> ((PageData) e).searchResult)
                    .build();
        }
    }
}