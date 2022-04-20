package com.github.mesabloo.diagnose4j.prettyprint;

import com.github.tomaslanger.chalk.Ansi;
import com.github.tomaslanger.chalk.Chalk;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Doc {
    final String content;
    private Ansi.Color fgColor;
    private Ansi.BgColor bgColor;
    private List<Ansi.Modifier> attributes;
    private boolean alignedToCurrentIndent;

    /**
     * Generates a colorless document from a <code>toString</code> method.
     *
     * @param content A value of any type which has <code>toString</code> overriden
     * @param <T>
     */
    public <T> Doc(final T content) {
        this.content = content.toString();
        this.fgColor = null;
        this.bgColor = null;
        this.attributes = new ArrayList<>();
        this.alignedToCurrentIndent = false;
    }

    public Doc colors(final Ansi.Color fg, final Ansi.BgColor bg, final Ansi.Modifier... attributes) {
        this.fgColor = fg;
        this.bgColor = bg;
        this.attributes = Arrays.stream(attributes)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return this;
    }

    public long width() {
        return Arrays.stream(content.split("\n"))
                .map(String::length)
                .max(Integer::compare)
                .orElse(0);
    }

    public Doc aligned() {
        this.alignedToCurrentIndent = true;

        return this;
    }

    ////////

    public static Doc colon() {
        return new Doc(":");
    }

    public static Doc space() {
        return new Doc(" ");
    }

    public static Doc line() {
        return new Doc("\n");
    }

    public static Doc empty() {
        return new Doc("");
    }

    ///////////////////////

    void print(PrintStream handle, final long currentColumn) {
        String content = this.content;
        if (this.alignedToCurrentIndent) {
            StringBuilder padding = new StringBuilder();
            for (int i = 0; i < currentColumn - 1; ++i) {
                padding.append(" ");
            }

            content = content.replaceAll("\n", "\n" + padding);
        }

        long nbNewlines = content.chars().filter(n -> n == '\n').count();
        boolean shouldAddNewlines = !content.equals("\n");
        for (String line : shouldAddNewlines ? content.split("\n") : new String[]{"\n"}) {
            // naive version: just output content with colors
            Chalk c = Chalk.on(shouldAddNewlines && nbNewlines-- > 0 ? (line + "\n") : line);
            if (this.fgColor != null)
                c = c.apply(this.fgColor);
            if (this.bgColor != null)
                c = c.apply(this.bgColor);
            for (Ansi.Modifier mod : this.attributes) {
                c = c.apply(mod);
            }

            handle.print(c);
        }
    }
}
