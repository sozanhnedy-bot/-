package com.threadsdocumentarynews.app;

import java.util.ArrayList;

/** Creates a mutable backing list for ArrayAdapter instances that are refreshed at runtime. */
public final class MutableSpinnerItems {
    private MutableSpinnerItems() { }

    public static ArrayList<String> copyOf(String[] values) {
        ArrayList<String> copy = new ArrayList<>(values == null ? 0 : values.length);
        if (values != null) {
            for (String value : values) copy.add(value);
        }
        return copy;
    }
}
