package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses a {@code turn:lanes} value into ordered left→right cells in the direction of travel. */
public final class OsmTurnLaneParser {

    public List<OsmTurnLaneCell> parse(String value) {
        List<OsmTurnLaneCell> cells = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return cells;
        }
        for (String cellText : value.toLowerCase(Locale.ROOT).split("\\|", -1)) {
            cells.add(parseCell(cellText.trim()));
        }
        return cells;
    }

    private OsmTurnLaneCell parseCell(String cellText) {
        List<LaneTurnClass> indications = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        LaneMerge merge = LaneMerge.NONE;
        boolean none = false;
        if (cellText.isEmpty()) {
            return new OsmTurnLaneCell("", indications, merge, true, false, unsupported);
        }
        for (String token : cellText.split(";")) {
            String t = token.trim();
            switch (t) {
                case "left" -> indications.add(LaneTurnClass.LEFT);
                case "slight_left" -> indications.add(LaneTurnClass.SLIGHT_LEFT);
                case "sharp_left" -> indications.add(LaneTurnClass.SHARP_LEFT);
                case "through" -> indications.add(LaneTurnClass.THROUGH);
                case "right" -> indications.add(LaneTurnClass.RIGHT);
                case "slight_right" -> indications.add(LaneTurnClass.SLIGHT_RIGHT);
                case "sharp_right" -> indications.add(LaneTurnClass.SHARP_RIGHT);
                case "reverse" -> indications.add(LaneTurnClass.REVERSE);
                case "merge_to_left" -> merge = LaneMerge.LEFT;
                case "merge_to_right" -> merge = LaneMerge.RIGHT;
                case "none" -> none = true;
                default -> unsupported.add(t);
            }
        }
        return new OsmTurnLaneCell(cellText, indications, merge, false, none, unsupported);
    }
}
