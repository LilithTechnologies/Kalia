package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.util.ScissorUtil;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.*;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.options.control.AbstractOptionList;
import net.caffeinemc.mods.sodium.client.gui.options.control.ExternalButtonControl;
import net.caffeinemc.mods.sodium.client.util.Dim2i;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

public class OptionListWidget extends AbstractOptionList {
    private List<Option.OptionNameSource> filteredOptions = null;
    private final Reference2ReferenceMap<Page, SectionInfo> pageToSectionInfo = new Reference2ReferenceOpenHashMap<>();
    private final Consumer<Page> onPageFocused;
    private SectionInfo lastFocusedSection;
    private boolean ignoreNextScrollUpdate = false;
    private int entryHeight;

    private ModOptions currentModOptions;

    private record SectionInfo(ModOptions modOptions, Page page, int startY, int endY, int scrollJumpTarget) {
    }

    public OptionListWidget(Screen screen, Dim2i dim, Consumer<Page> onPageFocused) {
        super(dim.insetLeft(Layout.OPTION_GROUP_MARGIN));
        this.onPageFocused = onPageFocused;
        this.rebuild(screen);
    }

    public void setFilteredOptions(List<Option.OptionNameSource> filteredOptions) {
        this.filteredOptions = filteredOptions;
    }

    public void clearFilter() {
        this.filteredOptions = null;
    }

    /** Restricts the list to one mod's pages; call {@link #rebuild} afterwards to apply. */
    public void showMod(ModOptions modOptions) {
        this.currentModOptions = modOptions;
    }

    public void rebuild(Screen screen) {
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth() - Layout.OPTION_LIST_SCROLLBAR_OFFSET - Layout.SCROLLBAR_WIDTH;
        int height = this.getHeight();

        this.clearChildren();
        this.controls.clear();
        this.pageToSectionInfo.clear();
        this.scrollbar = this.addRenderableChild(new ScrollbarWidget(new Dim2i(x + width + Layout.OPTION_LIST_SCROLLBAR_OFFSET, y, Layout.SCROLLBAR_WIDTH, height), this::updateSectionFocus));

        this.entryHeight = this.font.fontHeight * 2;
        int listHeight;

        if (this.filteredOptions != null) {
            listHeight = this.renderFilteredOptions(screen, x, y, width);
        } else if (this.currentModOptions != null) {
            listHeight = this.renderModPages(screen, x, y, width);
        } else {
            listHeight = this.renderAllPages(screen, x, y, width);
        }

        this.updateSectionFocus(this.scrollbar.getScrollAmount());
        this.scrollbar.setScrollbarContext(listHeight);
    }

    private int renderFilteredOptions(Screen screen, int x, int y, int width) {
        int listHeight = 0;

        Option.OptionNameSource lastSource = null;
        for (var source : this.filteredOptions) {
            var option = source.getOption();
            var control = option.getControl();
            var theme = source.getModOptions().theme();

            // Keep a little spacing between different option groups
            if (lastSource != null && lastSource.getOptionGroup() != source.getOptionGroup()) {
                listHeight += Layout.OPTION_GROUP_MARGIN;
            }

            var element = control.createElement(screen, this, new Dim2i(x, y + listHeight, width, this.entryHeight).insetLeft(Layout.OPTION_LEFT_INSET), theme);
            this.addRenderableChild(element);
            this.controls.add(element);
            listHeight += this.entryHeight;

            lastSource = source;
        }

        return listHeight;
    }

    private int renderModPages(Screen screen, int x, int y, int width) {
        final int buttonHeight = 20;
        final int gap = 4;
        final int sectionSpacing = 14;
        final int headerHeight = this.font.fontHeight + 4;
        int columnWidth = (width - gap) / 2;

        var theme = this.currentModOptions.theme();
        int listHeight = 0;
        boolean firstSection = true;

        for (var page : this.currentModOptions.pages()) {
            if (!firstSection) {
                listHeight += sectionSpacing;
            }
            firstSection = false;

            var header = new SectionHeaderWidget(this, new Dim2i(x, y + listHeight, width, headerHeight), page.name().asFormattedString());
            this.addRenderableChild(header);
            listHeight += headerHeight;

            if (page instanceof OptionPage optionPage) {
                int column = 0;
                for (OptionGroup group : optionPage.groups()) {
                    for (Option option : group.options()) {
                        int optionX = x + column * (columnWidth + gap);
                        var element = option.getControl().createElement(screen, this, new Dim2i(optionX, y + listHeight, columnWidth, buttonHeight), theme);
                        this.addRenderableChild(element);
                        this.controls.add(element);

                        column++;
                        if (column == 2) {
                            column = 0;
                            listHeight += buttonHeight + gap;
                        }
                    }
                }
                if (column != 0) {
                    listHeight += buttonHeight + gap;
                }
            } else if (page instanceof ExternalPage externalPage) {
                var externalPageWidget = new ExternalPageWidget(screen, this, new Dim2i(x, y + listHeight, width, buttonHeight), externalPage, theme);
                this.addRenderableChild(externalPageWidget);
                listHeight += buttonHeight + gap;
            } else {
                throw new IllegalStateException("Unknown page type: " + page.getClass());
            }
        }

        return listHeight;
    }

    private int renderAllPages(Screen screen, int x, int y, int width) {
        int listHeight = -Layout.OPTION_MOD_MARGIN;

        for (var modOptions : ConfigManager.CONFIG.getModOptions()) {
            if (modOptions.pages().isEmpty()) {
                continue;
            }

            var theme = modOptions.theme();

            // Add mod header
            listHeight += Layout.OPTION_MOD_MARGIN;
            var modHeaderStart = listHeight;
            var modHeader = new ModHeaderWidget(this, new Dim2i(x, y + listHeight, width, this.entryHeight), modOptions.name(), theme, modOptions.icon(), modOptions.iconMonochrome());
            this.addRenderableChild(modHeader);
            listHeight += this.entryHeight;

            for (var page : modOptions.pages()) {
                int pageStartY = listHeight;

                // if options page, add page header and options groups
                if (page instanceof OptionPage) {
                    // Add page header
                    listHeight += Layout.OPTION_PAGE_MARGIN;
                    var pageHeader = new PageHeaderWidget(this, new Dim2i(x, y + listHeight, width, this.entryHeight), page.name().asFormattedString(), theme);
                    this.addRenderableChild(pageHeader);
                    listHeight += this.entryHeight;

                    for (OptionGroup group : page.groups()) {
                        // Add padding beneath each option group
                        listHeight += Layout.OPTION_GROUP_MARGIN;

                        // Add group header if it has a name
                        if (group.name() != null) {
                            var groupHeader = new GroupHeaderWidget(this, new Dim2i(x, y + listHeight, width, this.entryHeight).insetLeft(Layout.OPTION_LEFT_INSET), group.name().asFormattedString());
                            this.addRenderableChild(groupHeader);
                            listHeight += this.entryHeight;
                        }

                        // Add each option's control element
                        for (Option option : group.options()) {
                            var control = option.getControl();
                            var element = control.createElement(screen, this, new Dim2i(x, y + listHeight, width, this.entryHeight).insetLeft(Layout.OPTION_LEFT_INSET), theme);

                            this.addRenderableChild(element);
                            this.controls.add(element);
                            listHeight += this.entryHeight;
                        }
                    }
                } else if (page instanceof ExternalPage externalPage) {
                    // Add external page entry
                    listHeight += Layout.OPTION_PAGE_MARGIN;
                    var externalPageWidget = new ExternalPageWidget(screen, this, new Dim2i(x, y + listHeight, width, this.entryHeight), externalPage, theme);
                    this.addRenderableChild(externalPageWidget);
                    listHeight += this.entryHeight;
                } else {
                    throw new IllegalStateException("Unknown page type: " + page.getClass());
                }

                // scroll up to the start of the mod header if this is the first page of a mod
                var scrollJumpTarget = pageStartY;
                if (modHeaderStart != -1) {
                    scrollJumpTarget = modHeaderStart;
                    modHeaderStart = -1;
                }

                var sectionInfo = new SectionInfo(modOptions, page, pageStartY, listHeight, scrollJumpTarget);
                this.pageToSectionInfo.put(page, sectionInfo);
            }
        }

        return listHeight;
    }

    public void jumpToPage(Page page) {
        var sectionInfo = this.pageToSectionInfo.get(page);
        if (sectionInfo != null) {
            this.ignoreNextScrollUpdate = true;
            this.scrollbar.scrollTo(sectionInfo.scrollJumpTarget);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        ScissorUtil.withScissor(this.getX(), this.getY(), this.getWidth(), this.getHeight(), () -> {
            super.render(mouseX, mouseY, delta);
        });
    }

    private void updateSectionFocus(int scrollAmount) {
        if (this.ignoreNextScrollUpdate) {
            this.ignoreNextScrollUpdate = false;
            return;
        }

        // calculate which y position is considered the "viewed" option,
        // + y is needed to compensate for the initial offset that the .startY values have
        int highlightTarget = scrollAmount + this.getY() + Math.min(this.entryHeight * 3, this.getHeight() / 2);

        // Find which section is currently in the middle of the viewport
        SectionInfo currentSection = null;
        for (SectionInfo section : this.pageToSectionInfo.values()) {
            if (highlightTarget >= section.startY && highlightTarget <= section.endY) {
                currentSection = section;
                break;
            }
        }

        // Only notify if the section has changed
        if (currentSection != null && currentSection != this.lastFocusedSection) {
            this.lastFocusedSection = currentSection;
            this.onPageFocused.accept(currentSection.page());
        }
    }

    private abstract static class HeaderWidget extends AbstractWidget {
        final AbstractOptionList list;
        final String title;
        final int textColor;
        final int backgroundColor;

        public HeaderWidget(AbstractOptionList list, Dim2i dim, String title, int textColor, int backgroundColor) {
            super(dim);
            this.list = list;
            this.title = title;
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            this.hovered = this.isMouseOver(mouseX, mouseY);

            this.drawRect(this.getX(), this.getY(), this.getLimitX(), this.getLimitY(), this.backgroundColor);
            this.drawString(this.truncateLabelToFit(this.title), this.getX() + Layout.OPTION_PAGE_MARGIN, this.getCenterY() + Layout.REGULAR_TEXT_BASELINE_OFFSET, this.textColor);
        }

        protected String truncateLabelToFit(String name) {
            return truncateTextToFit(name, this.getWidth() - 12);
        }

        @Override
        public int getY() {
            return super.getY() - this.list.getScrollAmount();
        }
    }

    private static class ModHeaderWidget extends HeaderWidget {
        final Identifier icon;
        final boolean iconMonochrome;

        public ModHeaderWidget(AbstractOptionList list, Dim2i dim, String title, ColorTheme theme, Identifier icon, boolean iconMonochrome) {
            super(list, dim, Formatting.BOLD + title, theme.themeLighter, Colors.BACKGROUND_DARKER);
            this.icon = icon;
            this.iconMonochrome = iconMonochrome;
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            this.hovered = this.isMouseOver(mouseX, mouseY);

            this.drawRect(this.getX(), this.getY(), this.getLimitX(), this.getLimitY(), this.backgroundColor);

            int textOffset = Layout.OPTION_TEXT_SIDE_PADDING;
            int textY = this.getCenterY() + Layout.REGULAR_TEXT_BASELINE_OFFSET;
            if (this.icon != null) {
                textOffset = VideoSettingsScreen.renderIconWithSpacing(this.icon,  this.textColor, this.iconMonochrome, this.getX(), this.getY(), this.getHeight(), Layout.ICON_MARGIN);
                textY = this.getCenterY() + Layout.ICON_TEXT_BASELINE_OFFSET;
            }
            this.drawString(truncateTextToFit(this.title, this.getWidth() - textOffset), this.getX() + textOffset, textY, this.textColor);
        }
    }

    private static class PageHeaderWidget extends HeaderWidget {
        public PageHeaderWidget(AbstractOptionList list, Dim2i dim, String title, ColorTheme theme) {
            this(list, dim, "◆ ", title, theme);
        }

        PageHeaderWidget(AbstractOptionList list, Dim2i dim, String prefix, String title, ColorTheme theme) {
            super(list, dim, prefix + title, theme.theme, Colors.BACKGROUND_DEFAULT);
        }
    }

    private static class GroupHeaderWidget extends HeaderWidget {
        public GroupHeaderWidget(AbstractOptionList list, Dim2i dim, String title) {
            super(list, dim, Formatting.BOLD + title, Colors.FOREGROUND, Colors.BACKGROUND_MEDIUM);
        }
    }

    private static class SectionHeaderWidget extends AbstractWidget {
        private final AbstractOptionList list;
        private final String title;

        SectionHeaderWidget(AbstractOptionList list, Dim2i dim, String title) {
            super(dim);
            this.list = list;
            this.title = title;
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            this.font.drawWithShadow(this.title, this.getX(), this.getY(), 0xFFFFFFFF);
        }

        @Override
        public int getY() {
            return super.getY() - this.list.getScrollAmount();
        }
    }

    // external page widget which is like ExternalButtonControl but for pages, clickable
    private static class ExternalPageWidget extends PageHeaderWidget {
        private final Screen screen;
        private final ExternalPage page;
        private final ColorTheme theme;

        public ExternalPageWidget(Screen screen, AbstractOptionList list, Dim2i dim, ExternalPage page, ColorTheme theme) {
            super(list, dim, "▶ ", page.name().asFormattedString(), theme);
            this.screen = screen;
            this.theme = theme;
            this.page = page;
        }

        @Override
        public void render(int mouseX, int mouseY, float delta) {
            super.render(mouseX, mouseY, delta);

            Text buttonText = ExternalButtonControl.formatExternalButtonText(true, this.theme);

            this.drawString(buttonText,
                    this.getLimitX() - Layout.OPTION_TEXT_SIDE_PADDING - this.font.getStringWidth(buttonText.asFormattedString()),
                    this.getCenterY() + Layout.REGULAR_TEXT_BASELINE_OFFSET,
                    Colors.FOREGROUND);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
                this.page.currentScreenConsumer().accept(this.screen);
                this.playClickSound();
                return true;
            }
            return false;
        }
    }
}
