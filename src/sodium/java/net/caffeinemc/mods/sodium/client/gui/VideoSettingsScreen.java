package net.caffeinemc.mods.sodium.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import net.caffeinemc.mods.sodium.legacy.compat.mojang.minecraft.gui.Renderable;
import net.caffeinemc.mods.sodium.legacy.compat.mojang.minecraft.gui.event.GuiEventListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.VideoOptionsScreen;
import net.minecraft.text.TranslatableText;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalPage;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.minecraft.text.LiteralText;
import net.caffeinemc.mods.sodium.client.data.fingerprint.HashedFingerprint;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.gui.screen.ConfigCorruptedScreen;
import net.caffeinemc.mods.sodium.client.gui.screen.RenderableScreen;
import net.caffeinemc.mods.sodium.client.gui.widgets.*;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class VideoSettingsScreen extends RenderableScreen implements ScrollableTooltip.TooltipParent {
    // LWJGL2 legacy key codes (used by Minerva's Keyboard compatibility layer)
    private static final int KEY_ESCAPE = 1;
    private static final int KEY_T = 20;
    private static final int KEY_P = 25;

    public final Screen prevScreen;
    private final @Nullable OptionPage initiallyFocusedPage;

    private Dim2i dim;
    private boolean insetX, insetY;

    private final Reference2ReferenceLinkedOpenHashMap<ModOptions, FlatButtonWidget> modTabs = new Reference2ReferenceLinkedOpenHashMap<>();
    private ModOptions selectedMod;
    private boolean needsRebuild;
    private SearchWidget searchWidget;
    private OptionListWidget optionList;

    private FlatButtonWidget applyButton, closeButton, undoButton;
    private DonationButtonWidget donateButton;

    private boolean hasPendingChanges;

    private final ScrollableTooltip tooltip = new ScrollableTooltip(this);

    private VideoSettingsScreen(Screen prevScreen) {
        this(prevScreen, null);
    }

    private VideoSettingsScreen(Screen prevScreen, @Nullable OptionPage initiallyFocusedPage) {
        super();

        this.prevScreen = prevScreen;
        this.initiallyFocusedPage = initiallyFocusedPage;

        this.checkPromptTimers();

        // the binding values may have been modified in the meantime, reload from binding to update
        ConfigManager.CONFIG.resetAllOptionsFromBindings();
    }

    private void checkPromptTimers() {
        // Never show the prompt in developer workspaces.
        if (PlatformRuntimeInformation.getInstance().isDevelopmentEnvironment()) {
            return;
        }

        var options = SodiumClientMod.options();

        // If the user has already seen the prompt, don't show it again.
        if (options.notifications.hasSeenDonationPrompt) {
            return;
        }

        HashedFingerprint fingerprint = null;

        try {
            fingerprint = HashedFingerprint.loadFromDisk();
        } catch (Throwable t) {
            SodiumClientMod.logger()
                    .error("Failed to read the fingerprint from disk", t);
        }

        // If the fingerprint doesn't exist, or failed to be loaded, abort.
        if (fingerprint == null) {
            return;
        }

        // The fingerprint records the installation time. If it's been a while since installation, show the user
        // a prompt asking for them to consider donating.
        var now = Instant.now();
        var threshold = Instant.ofEpochSecond(fingerprint.timestamp())
                .plus(3, ChronoUnit.DAYS);

        if (now.isAfter(threshold)) {
            this.openDonationPrompt(options);
        }
    }

    private void openDonationPrompt(SodiumOptions options) {
        options.notifications.hasSeenDonationPrompt = true;

        try {
            SodiumOptions.writeToDisk(options);
        } catch (IOException e) {
            SodiumClientMod.logger()
                    .error("Failed to update config file", e);
        }
    }

    public static Screen createScreen(Screen currentScreen) {
        return createScreen(currentScreen, null);
    }

    public static Screen createScreen(Screen currentScreen, @Nullable OptionPage initiallyFocusedPage) {
        if (SodiumClientMod.options().isReadOnly()) {
            return new ConfigCorruptedScreen(currentScreen, VideoSettingsScreen::new);
        } else {
            return new VideoSettingsScreen(currentScreen, initiallyFocusedPage);
        }
    }

    @Override
    public void init() {
        super.init();

        ConfigManager.CONFIG.invalidateGlobalRebuildDependents();
        this.rebuild();

        if (this.initiallyFocusedPage != null) {
            this.jumpToPage(this.initiallyFocusedPage);
            this.onSectionFocused(this.initiallyFocusedPage);
        }
    }

    private int ifInsetX(int value) {
        return this.insetX ? value : 0;
    }

    private int ifInsetY(int value) {
        return this.insetY ? value : 0;
    }

    private int ifNotInsetX(int value) {
        return this.insetX ? 0 : value;
    }

    private int ifNotInsetY(int value) {
        return this.insetY ? 0 : value;
    }

    private void rebuild() {
        this.clearWidgets();
        this.modTabs.clear();

        this.updateScreenDimensions();
        var x = this.dim.x();
        var y = this.dim.y();
        var w = this.dim.width();
        var h = this.dim.height();

        var font = MinecraftClient.getInstance().textRenderer;

        // ---- Resolve the selected mod (default: first mod with pages) ----
        var mods = new ArrayList<ModOptions>();
        for (var modOptions : ConfigManager.CONFIG.getModOptions()) {
            if (!modOptions.pages().isEmpty()) {
                mods.add(modOptions);
            }
        }
        if (this.selectedMod == null || !containsByReference(mods, this.selectedMod)) {
            this.selectedMod = mods.isEmpty() ? null : mods.get(0);
        }

        int gridWidth = 304; // two 150px option buttons + gap
        int contentWidth = gridWidth + Layout.OPTION_LIST_SCROLLBAR_OFFSET + Layout.SCROLLBAR_WIDTH;
        int contentX = x + (w - contentWidth) / 2;

        int searchWidth = 90;
        int tabWidth = 60;
        for (var mod : mods) {
            tabWidth = Math.max(tabWidth, font.getStringWidth(mod.name()) + 16);
        }
        tabWidth = Math.min(tabWidth, 110);

        int tabsX = contentX;
        int searchX = contentX + contentWidth - searchWidth;

        for (var mod : mods) {
            var tab = new FlatButtonWidget(new Dim2i(tabsX, y, tabWidth, Layout.BUTTON_SHORT), new LiteralText(mod.name()), () -> {
                if (this.selectedMod != mod) {
                    this.selectedMod = mod;
                    this.needsRebuild = true;
                }
            }, true, false);
            tab.setSelected(mod == this.selectedMod);
            this.modTabs.put(mod, tab);
            this.addRenderableWidget(tab);
            tabsX += tabWidth + Layout.INNER_MARGIN;
        }

        this.searchWidget = new SearchWidget(this::onSearchResults, new Dim2i(searchX, y, searchWidth, Layout.BUTTON_SHORT));

        int topBarClear = Layout.BUTTON_SHORT + Layout.INNER_MARGIN * 2;

        int doneWidth = 200;
        int doneX = x + (w - doneWidth) / 2;
        int bottomY = y + h - Layout.BUTTON_SHORT - ifNotInsetY(Layout.INNER_MARGIN);

        this.undoButton = new FlatButtonWidget(new Dim2i(doneX - Layout.BUTTON_LONG - Layout.INNER_MARGIN, bottomY, Layout.BUTTON_LONG, Layout.BUTTON_SHORT), new TranslatableText("sodium.options.buttons.undo"), this::undoChanges, true, false);
        this.applyButton = new FlatButtonWidget(new Dim2i(doneX + doneWidth + Layout.INNER_MARGIN, bottomY, Layout.BUTTON_LONG, Layout.BUTTON_SHORT), new TranslatableText("sodium.options.buttons.apply"), ConfigManager.CONFIG::applyAllOptions, true, false);
        this.closeButton = new FlatButtonWidget(new Dim2i(doneX, bottomY, doneWidth, Layout.BUTTON_SHORT), new TranslatableText("gui.done"), () -> {
            this.client.setScreen(this.prevScreen);
        }, true, false);
        this.addRenderableWidget(this.undoButton);
        this.addRenderableWidget(this.applyButton);
        this.addRenderableWidget(this.closeButton);

        this.donateButton = new DonationButtonWidget(this, this::openDonationPage, this::hideDonationButton);
        if (this.donateButton.getWidth() > x + w - (contentX + contentWidth)) {
            this.donateButton.updateDisplay(this, false);
        }

        this.addRenderableWidget(this.searchWidget);
        this.updateSearchWidgetWidth();

        int bottomClear = Layout.BUTTON_SHORT + Layout.INNER_MARGIN * 2;
        var optionListDim = new Dim2i(
                contentX,
                y + topBarClear,
                contentWidth,
                h - topBarClear - bottomClear - ifNotInsetY(Layout.INNER_MARGIN)
        );
        this.optionList = new OptionListWidget(this, optionListDim, this::onSectionFocused);
        if (this.selectedMod != null) {
            this.optionList.showMod(this.selectedMod);
            this.optionList.rebuild(this);
        }
        this.addRenderableWidget(this.optionList);

        var tooltipAreaY = y + topBarClear;
        var tooltipAreaX = this.optionList.getLimitX() + Layout.TOOLTIP_OUTER_MARGIN;
        this.tooltip.setTooltipArea(
                new Dim2i(
                        tooltipAreaX,
                        tooltipAreaY,
                        this.width - tooltipAreaX - ifNotInsetX(Layout.TOOLTIP_OUTER_MARGIN),
                        this.height - tooltipAreaY - ifNotInsetY(Layout.TOOLTIP_OUTER_MARGIN)
                )
        );
    }

    private static <T> boolean containsByReference(List<? extends T> list, T value) {
        for (var element : list) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    private void updateScreenDimensions() {
        // size screen to not be too wide
        var baseContentWidth = Layout.PAGE_LIST_WIDTH + Layout.INNER_MARGIN + Layout.OPTION_WIDTH + Layout.OPTION_LIST_SCROLLBAR_OFFSET + Layout.SCROLLBAR_WIDTH + Layout.TOOLTIP_OUTER_MARGIN;
        var minContentWidth = baseContentWidth + (Layout.MAX_TOOLTIP_WIDTH - Layout.MIN_TOOLTIP_WIDTH) / 2 + Layout.MIN_TOOLTIP_WIDTH;
        var maxContentWidth = baseContentWidth + Layout.MAX_TOOLTIP_WIDTH;
        var maxInterpolatingBorderWidth = 100;
        var widthInterpolationStart = minContentWidth + Layout.CONTENT_BORDER_MIN_WIDTH;
        var widthInterpolationEnd = maxContentWidth + maxInterpolatingBorderWidth;

        int contentWidth = this.width;
        this.insetX = false;
        if (this.width > minContentWidth + Layout.CONTENT_BORDER_MIN_WIDTH) {
            // interpolate between min and max content width based on current width
            if (this.width < widthInterpolationEnd) {
                float t = (float) (this.width - widthInterpolationStart) / (widthInterpolationEnd - widthInterpolationStart);
                contentWidth = minContentWidth + (int) (t * (maxContentWidth - minContentWidth));
            } else {
                contentWidth = maxContentWidth;
            }
            this.insetX = true;
        }

        // for height, it's the other way around. there's a maximum border height
        int contentHeight = this.height;
        this.insetY = false;
        if (this.height > Layout.CONTENT_MIN_HEIGHT + Layout.CONTENT_BORDER_HEIGHT && this.insetX) {
            contentHeight = this.height - Layout.CONTENT_BORDER_HEIGHT;
            this.insetY = true;
        }

        // center the content area
        this.dim = new Dim2i(
                (this.width - contentWidth) / 2,
                (this.height - contentHeight) / 2,
                contentWidth,
                contentHeight
        );
    }

    public Dim2i getDimensions() {
        return dim;
    }

    private void onSearchResults(List<Option.OptionNameSource> searchResults) {
        if (searchResults.isEmpty()) {
            this.optionList.clearFilter();
        } else {
            this.optionList.setFilteredOptions(searchResults);
        }
        this.optionList.rebuild(this);
    }

    private void onSectionFocused(Page page) {
    }

    public void jumpToPage(Page page) {
        for (var modOptions : ConfigManager.CONFIG.getModOptions()) {
            if (containsByReference(modOptions.pages(), page)) {
                this.selectedMod = modOptions;
                break;
            }
        }
        this.rebuild();
    }

    private void updateSearchWidgetWidth() {
        this.searchWidget.updateWidgetWidth(this.searchWidget.getWidth());
    }

    private void hideDonationButton() {
        SodiumOptions options = SodiumClientMod.options();
        options.notifications.hasClearedDonationButton = true;

        try {
            SodiumOptions.writeToDisk(options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save configuration", e);
        }

        this.donateButton.updateDisplay(this, false);
        this.updateSearchWidgetWidth();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        if (this.needsRebuild) {
            this.needsRebuild = false;
            this.rebuild();
        }

        this.updateControls(mouseX, mouseY);

        var font = MinecraftClient.getInstance().textRenderer;

        if (this.insetY) {
            String title = new TranslatableText("options.videoTitle").asFormattedString();
            font.drawWithShadow(title, (this.width - font.getStringWidth(title)) / 2.0f, this.dim.y() - 16, 0xFFFFFFFF);
        }

        var modLine = new StringBuilder();
        for (var modOptions : ConfigManager.CONFIG.getModOptions()) {
            if (modLine.length() > 0) {
                modLine.append(", ");
            }
            modLine.append(modOptions.name()).append(' ').append(modOptions.version());
        }
        font.drawWithShadow(modLine.toString(), 2, this.height - 10, 0xFF888888);

        super.render(mouseX, mouseY, delta);

        this.tooltip.render();
    }

    private void updateControls(int mouseX, int mouseY) {
        boolean hasChanges = ConfigManager.CONFIG.anyOptionChanged();

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        AbstractWidget reservedAreaBlocker;
        if (hasChanges) {
            reservedAreaBlocker = this.undoButton;
        } else {
            reservedAreaBlocker = this.applyButton;
        }
        this.tooltip.setReservedAreaTopLeftCorner(reservedAreaBlocker.getX(), reservedAreaBlocker.getY());

        this.hasPendingChanges = hasChanges;

        // determine the tooltip hover target
        // this is the first item that's hovered over, or if nothing is hovered, the focused item
        ControlElement hovered = null;
        ControlElement focused = null;
        if (mouseX >= this.optionList.getX() && mouseX <= this.optionList.getLimitX() &&
                mouseY >= this.optionList.getY() && mouseY <= this.optionList.getLimitY()) {
            for (ControlElement element : this.optionList.getControls()) {
                if (element.isMouseOver(mouseX, mouseY)) {
                    hovered = element;
                    break;
                }
                if (element.isFocused()) {
                    focused = element;
                }
            }
        }
        var hoverTarget = hovered != null ? hovered : focused;

        this.tooltip.onControlHover(hoverTarget, mouseX, mouseY);
    }

    private void undoChanges() {
        ConfigManager.CONFIG.resetAllOptionsFromBindings();
    }

    private void openDonationPage() {
        try {
            Desktop.getDesktop().browse(URI.create("https://caffeinemc.net/donate"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double f, double amount) {
        // change the gui scale with scrolling if the control key is held
        if (hasControlDown()) {
            var location = new Identifier("sodium:general.gui_scale");
            var option = ConfigManager.CONFIG.getOption(location);
            if (option instanceof IntegerOption guiScaleOption) {
                if (guiScaleOption.getValidatedValue() instanceof Integer intValue) {
                    var range = guiScaleOption.getSteppedValidator();
                    var top = range.max() + 1;
                    var auto = range.min();

                    // re-maps the auto value (presumably 0) to be at the top of the scroll range
                    if (intValue == auto) {
                        intValue = top;
                    }
                    var newValue = Math.clamp(intValue + (int) Math.signum(amount), auto + 1, top);
                    if (newValue != intValue) {
                        if (newValue == top) {
                            newValue = auto;
                        }
                        if (range.isValueValid(newValue)) {
                            guiScaleOption.modifyValue(newValue);
                            ConfigManager.CONFIG.applyOption(location);
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        if (this.tooltip.mouseScrolled(x, y, amount)) {
            return true;
        }

        return super.mouseScrolled(x, y, f, amount);
    }

    @Override
    public <T extends GuiEventListener & Renderable> T addRenderableWidget(T guiEventListener) {
        return super.addRenderableWidget(guiEventListener);
    }

    @Override
    public void removeWidget(GuiEventListener guiEventListener) {
        super.removeWidget(guiEventListener);
    }

    public <T extends GuiEventListener & Renderable> void setWidgetPresence(T guiEventListener, boolean present) {
        this.removeWidget(guiEventListener);
        if (present) {
            this.addRenderableWidget(guiEventListener);
        }
    }

    @Override
    protected void keyPressed(char id, int code) {
        if (code == KEY_ESCAPE && this.hasPendingChanges) {
            // prevent closing the screen with pending changes
            return;
        }

        if (!this.searchWidget.isSearching()) {
            if (code == KEY_P && Screen.hasShiftDown()) {
                this.client.setScreen(new VideoOptionsScreen(this.prevScreen, this.client.options));
                return;
            }

            if (code == KEY_T) {
                this.searchWidget.setFocused(true);
            }
        }

        if (code == KEY_ESCAPE) {
            this.client.setScreen(this.prevScreen);
            if (this.client.currentScreen == null) {
                this.client.closeScreen();
            }
        }

        super.keyPressed(id, code);
    }

    public static int renderIconWithSpacing(Identifier icon, int color, boolean iconMonochrome, int x, int y, int height, int margin) {
        int iconSize = height - margin * 2;

        MinecraftClient.getInstance().getTextureManager().bindTexture(icon);

        x = x + margin;
        y = y + height / 2 - iconSize / 2;
        if (iconMonochrome) {
            GlStateManager.color((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
            drawTexture(x, y, 0, 0, iconSize, iconSize, iconSize, iconSize, iconSize, iconSize);
        } else {
            drawTexture(x, y, 0, 0, iconSize, iconSize, iconSize, iconSize, iconSize, iconSize);
        }

        return margin * 2 + iconSize;
    }

    //TODO: add back donation message
}
