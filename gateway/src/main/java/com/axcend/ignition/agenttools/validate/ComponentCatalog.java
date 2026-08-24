package com.axcend.ignition.agenttools.validate;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Curated catalog of well-known stock Perspective component type IDs, deprecated aliases, and
 * style-key knowledge used by the view validator. Advisory only: the catalog is not exhaustive
 * (custom and third-party module components exist), so unknown types are warnings, never errors.
 */
public final class ComponentCatalog {

    public static final Set<String> KNOWN_TYPES = Set.of(
            // containers
            "ia.container.breakpt", "ia.container.column", "ia.container.coord",
            "ia.container.drawing", "ia.container.flex", "ia.container.split", "ia.container.tab",
            // display
            "ia.display.accordion", "ia.display.alarmjournaltable", "ia.display.alarmstatustable",
            "ia.display.audio", "ia.display.barcode", "ia.display.carousel", "ia.display.cylindrical-tank",
            "ia.display.dashboard", "ia.display.equipmentschedule", "ia.display.flex-repeater",
            "ia.display.google-map", "ia.display.icon", "ia.display.iframe", "ia.display.image",
            "ia.display.label", "ia.display.led-display", "ia.display.linear-scale", "ia.display.map",
            "ia.display.markdown", "ia.display.moving-analog-indicator", "ia.display.pdf-viewer",
            "ia.display.progress", "ia.display.sparkline", "ia.display.table", "ia.display.tag-browse-tree",
            "ia.display.thermometer", "ia.display.tree", "ia.display.video-player", "ia.display.view",
            "ia.display.viewcanvas",
            // charts
            "ia.chart.chartrangeselector", "ia.chart.gauge", "ia.chart.pie", "ia.chart.powerchart",
            "ia.chart.simple-gauge", "ia.chart.timeseries", "ia.chart.xy",
            // input
            "ia.input.barcodescannerinput", "ia.input.button", "ia.input.checkbox",
            "ia.input.date-time-input", "ia.input.date-time-picker", "ia.input.dropdown",
            "ia.input.fileupload", "ia.input.form", "ia.input.multi-state-button",
            "ia.input.numeric-entry-field", "ia.input.oneshotbutton", "ia.input.password-field",
            "ia.input.radio-group", "ia.input.signature-pad", "ia.input.slider", "ia.input.text-area",
            "ia.input.text-field", "ia.input.toggle-switch",
            // navigation / shapes / symbols
            "ia.navigation.horizontalmenu", "ia.navigation.link", "ia.navigation.menutree",
            "ia.navigation.navlinks", "ia.shapes.circle", "ia.shapes.ellipse", "ia.shapes.group",
            "ia.shapes.line", "ia.shapes.path", "ia.shapes.polygon", "ia.shapes.polyline",
            "ia.shapes.rect", "ia.shapes.svg", "ia.shapes.text", "ia.symbol.motor",
            "ia.symbol.pump", "ia.symbol.sensor", "ia.symbol.valve", "ia.symbol.vessel"
    );

    /** Deprecated/aliased component type IDs mapped to their current canonical ID. */
    public static final Map<String, String> ALIASES = Map.of(
            "ia.text.label", "ia.display.label",
            "ia.display.gauge", "ia.chart.simple-gauge",
            "ia.gauge", "ia.chart.simple-gauge",
            "ia.input.toggleSwitch", "ia.input.toggle-switch"
    );

    /** CSS keys that belong on flex props (direction/justify/alignItems/wrap), not props.style. */
    public static final Set<String> FLEX_STYLE_KEYS = Set.of(
            "flexDirection", "justifyContent", "alignItems", "alignContent", "flexWrap"
    );

    /** Recognized Perspective binding type discriminators. */
    public static final Set<String> BINDING_TYPES = Set.of(
            "expr", "tag", "query", "property", "tag-history", "http", "message", "udtParameter"
    );

    private ComponentCatalog() {
    }

    public static boolean isKnown(String type) {
        return type != null && KNOWN_TYPES.contains(type);
    }

    public static Optional<String> canonicalFor(String type) {
        return Optional.ofNullable(type == null ? null : ALIASES.get(type));
    }
}
