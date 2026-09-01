package net.fayber.moderngui.dev;

import net.fayber.moderngui.render.Ui;
import net.fayber.moderngui.screen.ModernGuiScreen;
import net.fayber.moderngui.screen.Modal;
import net.fayber.moderngui.screen.Toast;
import net.fayber.moderngui.widget.Badge;
import net.fayber.moderngui.widget.Checkbox;
import net.fayber.moderngui.widget.CollapsibleSection;
import net.fayber.moderngui.widget.CycleButton;
import net.fayber.moderngui.widget.Divider;
import net.fayber.moderngui.widget.Dropdown;
import net.fayber.moderngui.widget.FlatButton;
import net.fayber.moderngui.widget.HorizontalScrollPanel;
import net.fayber.moderngui.widget.HFlow;
import net.fayber.moderngui.widget.IconButton;
import net.fayber.moderngui.widget.Icons;
import net.fayber.moderngui.widget.IntSlider;
import net.fayber.moderngui.widget.KeybindField;
import net.fayber.moderngui.widget.Label;
import net.fayber.moderngui.widget.Layouts;
import net.fayber.moderngui.widget.NumberField;
import net.fayber.moderngui.widget.PillToggle;
import net.fayber.moderngui.widget.ProgressBar;
import net.fayber.moderngui.widget.RadioGroup;
import net.fayber.moderngui.widget.SearchField;
import net.fayber.moderngui.widget.SegmentedControl;
import net.fayber.moderngui.widget.Spinner;
import net.fayber.moderngui.widget.Stepper;
import net.fayber.moderngui.widget.Tabs;
import net.fayber.moderngui.widget.TextArea;
import net.fayber.moderngui.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The widget catalog test screen: one of every widget, so regressions stay visible during
 * development. Shipped but inert in production: it is only reachable through the
 * {@code -Dmoderngui.preview=true} workbench hook, and all of its backing state lives here as
 * statics (so switching tabs, which re-runs {@link #initScreen}, keeps every value).
 *
 * <p>The catalog does not fit on one 720p screen, so the tabs at the top switch between three
 * pages: buttons and selectors, text input, and containers plus status indicators. On small
 * windows the tail of a page clips rather than scrolls; that is fine for a workbench screen.
 */
public final class WidgetTestScreen extends ModernGuiScreen {
    private static final int TAB_BUTTONS = 0;
    private static final int TAB_INPUTS = 1;
    private static final int TAB_LAYOUT = 2;

    // Shared demo state; statics survive tab switches (the screen rebuilds).
    private static int tab = TAB_BUTTONS;
    private static boolean pillState = true;
    private static boolean checkboxState = false;
    private static int radioIndex = 1;
    private static int segmentedIndex = 0;
    private static int stepperValue = 8;
    private static int dropdownValue = 1;
    private static DemoMode cycleValue = DemoMode.NORMAL;
    private static int keybindCode = GLFW.GLFW_KEY_R;
    private static int sliderValue = 40;
    private static double numberValue = 2.5;
    private static String searchValue = "";

    /** Demo values for the cycle button. */
    private enum DemoMode {
        SLOW,
        NORMAL,
        FAST
    }

    private static final DemoMode[] DEMO_MODES = DemoMode.values();

    public WidgetTestScreen() {
        super(Component.literal("Modern GUI Widget Test"));
    }

    @Override
    protected void initScreen() {
        int x = this.contentX();

        this.addRenderableWidget(new Tabs(x, 30, List.of(Component.literal("Buttons"),
                        Component.literal("Inputs"), Component.literal("Layout")),
                () -> tab, index -> {
                    tab = index;
                    this.rebuildWidgets();
                }));

        switch (tab) {
            case TAB_BUTTONS -> this.buildButtonsPage(x);
            case TAB_INPUTS -> this.buildInputsPage(x);
            default -> this.buildLayoutPage(x);
        }
    }

    private void buildButtonsPage(int x) {
        int y = 68;

        FlatButton primary = new FlatButton(x, y, 100, 28,
                Component.literal("Primary"), () -> {
                }, FlatButton.Style.PRIMARY);
        FlatButton ghost = new FlatButton(x + 108, y, 100, 28,
                Component.literal("Ghost"), () -> {
                });
        FlatButton disabled = new FlatButton(x + 216, y, 100, 28,
                Component.literal("Disabled"), () -> {
                });
        disabled.active = false;
        this.tooltip(ghost, "Rich tooltip", "Title and body, 500 ms hover delay. Flips at the screen edges.");
        this.place(List.of(primary, ghost, disabled), x, y);

        y += 38;
        List<AbstractWidget> iconRow = new ArrayList<>();
        iconRow.add(new FlatButton(x, y, 80, 26, Component.literal("Toast"), () ->
                this.showToast(Toast.success("Saved", "The widget catalog works."))));
        iconRow.add(new FlatButton(x + 88, y, 80, 26, Component.literal("Modal"), this::showModalExample));
        iconRow.add(new IconButton(x + 180, y, 26, Icons.PLUS, () -> {
        }));
        iconRow.add(new IconButton(x + 214, y, 26, Icons.GEAR, () -> {
        }, IconButton.Style.PRIMARY));
        iconRow.add(new IconButton(x + 248, y, 26, Icons.SEARCH, () -> {
        }));
        IconButton iconDisabled = new IconButton(x + 282, y, 26, Icons.X, () -> {
        });
        iconDisabled.active = false;
        iconRow.add(iconDisabled);
        this.place(iconRow, x, y);

        y += 38;
        this.place(List.of(
                new PillToggle(x, y + 2, PillToggle.Size.SMALL, () -> pillState, v -> pillState = v),
                new PillToggle(x + 36, y + 1, PillToggle.Size.NORMAL, () -> pillState, v -> pillState = v),
                new PillToggle(x + 80, y, PillToggle.Size.LARGE, () -> pillState, v -> pillState = v),
                new Checkbox(x + 136, y + 4, "Checkbox", checkboxState, v -> checkboxState = v),
                new Checkbox(x + 250, y + 4, "Live", () -> pillState, v -> pillState = v)), x, y);

        y += 38;
        this.place(List.of(
                new KeybindField(x, y, 180, () -> keybindCode, v -> keybindCode = v),
                new Stepper(x + 190, y + 1, () -> stepperValue, v -> stepperValue = v, 0, 20, 1)), x, y);

        y += 38;
        this.place(List.of(
                new CycleButton<>(x, y, 24, () -> cycleValue, v -> cycleValue = v,
                        DEMO_MODES, m -> switch (m) {
                            case SLOW -> Component.literal("Slow");
                            case NORMAL -> Component.literal("Normal");
                            case FAST -> Component.literal("Fast");
                        }),
                new SegmentedControl(x + 130, y, List.of("Alpha", "Beta"),
                        () -> segmentedIndex, v -> segmentedIndex = v)), x, y);

        y += 38;
        this.place(List.of(
                new RadioGroup(x, y, List.of("One", "Two", "Three"), () -> radioIndex, v -> radioIndex = v)
                        .horizontal(),
                new Dropdown(x + 200, y, 180, 26, List.of(Component.literal("Option One"),
                                Component.literal("Option Two"), Component.literal("Option Three")),
                        () -> dropdownValue, v -> dropdownValue = v).host(this.popupHost())), x, y);
    }

    private void buildInputsPage(int x) {
        int y = 68;

        this.addRenderableWidget(new TextField(x, y, 240, 22).hint("Text field")
                .onChanged(v -> searchValue = v));
        this.tooltip(new Label(x + 250, y + 5, "TextField"), "TextField",
                "Wraps a vanilla EditBox: caret, clipboard and selection come free.");

        y += 38;
        this.addRenderableWidget(new SearchField(x, y, 240, 22)
                .onSearch(v -> searchValue = v));

        y += 38;
        this.addRenderableWidget(new NumberField(x, y, 140, 22, 0.0, 10.0, 0.5)
                .onChange(v -> numberValue = v));
        Label committed = new Label(x + 150, y + 5, "committed: " + numberValue);
        committed.style(Label.Style.MUTED);
        this.addRenderableWidget(committed);

        y += 38;
        TextArea area = new TextArea(x, y, 300, 88).lineNumbers(true)
                .value("Line one\nLine two\n\nEdit me.")
                .onChanged(v -> searchValue = v);
        this.addRenderableWidget(area);

        y += 100;
        this.addRenderableWidget(new IntSlider(x, y, 300,
                Component.literal("Slider"), 0, 100, 5, () -> sliderValue, v -> sliderValue = v));
    }

    private void buildLayoutPage(int x) {
        int y = 68;

        ProgressBar labeled = new ProgressBar(x, y, 230, 16).showLabel(true)
                .value(() -> 0.5 + 0.5 * Math.sin(Util.getMillis() / 900.0));
        this.addRenderableWidget(labeled);
        this.addRenderableWidget(new Label(x + 240, y + 4, "determinate"));

        y += 26;
        this.addRenderableWidget(new ProgressBar(x, y, 230, 8).indeterminate(true));
        this.addRenderableWidget(new Label(x + 240, y + 2, "indeterminate"));

        y += 22;
        this.addRenderableWidget(new Spinner(x, y, 16));
        HFlow badges = new HFlow(x + 26, y, 350);
        badges.add(Badge.success(0, 0, "success"));
        badges.add(Badge.warning(0, 0, "warning"));
        badges.add(Badge.danger(0, 0, "danger"));
        badges.add(new Badge(0, 0, "neutral"));
        this.addRenderableWidget(badges);

        y += 28;
        Label regular = new Label(x, y, "Regular");
        Label bold = new Label(x + 70, y, "Bold");
        bold.style(Label.Style.BOLD);
        Label muted = new Label(x + 120, y, "Muted");
        muted.style(Label.Style.MUTED);
        Label heading = new Label(x + 180, y, "Heading");
        heading.style(Label.Style.HEADING);
        this.place(List.of(regular, bold, muted, heading), x, y);

        y += 24;
        this.addRenderableWidget(Divider.labeled(x, y, 380, "containers"));

        y += 18;
        CollapsibleSection section = new CollapsibleSection(x, y, 380, "Collapsible section", true);
        section.add(new FlatButton(0, 0, 110, 22, Component.literal("Inside"), () ->
                this.showToast(Toast.info("Section", "The section body works."))));
        section.pack();
        this.addRenderableWidget(section);

        y += 62;
        HorizontalScrollPanel panel = new HorizontalScrollPanel(x, y, 380, 40);
        for (int i = 0; i < 6; i++) {
            panel.add(new FlatButton(0, 7, 80, 26, Component.literal("Item " + (i + 1)), () -> {
            }));
        }
        panel.pack(8);
        this.addRenderableWidget(panel);
    }

    /** Lays a row of widgets out left to right, then adds them all to the screen. */
    private void place(List<AbstractWidget> widgets, int x, int y) {
        Layouts.row(widgets, x, y, 10);
        widgets.forEach(this::addRenderableWidget);
    }

    private void showModalExample() {
        this.showModal(Modal.confirm(
                Component.literal("Confirm action"),
                Component.literal("This dialog blocks every widget beneath it until you answer."),
                () -> this.showToast(Toast.info("Confirmed", "OK was pressed.")),
                null));
    }

    @Override
    protected void drawTitle(GuiGraphicsExtractor gfx) {
        Ui.text(gfx, Ui.uiBold(this.title), this.contentX(), 18, this.theme.text);
    }
}
