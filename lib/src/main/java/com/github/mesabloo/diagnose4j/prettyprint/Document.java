package com.github.mesabloo.diagnose4j.prettyprint;

import com.github.tomaslanger.chalk.Ansi;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class Document {
    private final List<Doc> parts;

    public Document() {
        this.parts = new ArrayList<>();
    }

    public Document aligned() {
        for (final Doc part : this.parts) {
            part.aligned();
        }
        return this;
    }

    public Document colors(final Ansi.Color fgColor, final Ansi.BgColor bgColor, final Ansi.Modifier... mods) {
        for (final Doc part : this.parts) {
            part.colors(fgColor, bgColor, mods);
        }
        return this;
    }

    public Document append(final Doc part) {
        this.parts.add(part);
        return this;
    }

    public Document appendDoc(final Document doc) {
        this.parts.addAll(doc.parts);
        return this;
    }

    public void removeColors() {
        for (final Doc doc : this.parts) {
            doc.colors(null, null);
        }
    }

    public void print(PrintStream handle) {
        long currentColumn = 1;

        for (final Doc doc : this.parts) {
            doc.print(handle, currentColumn);

            final String[] lines = doc.content.split("\n");
            if (lines.length == 1)
                currentColumn += lines[0].length();
            else if (lines.length == 0)
                currentColumn = 1;
            else
                currentColumn = lines[lines.length - 1].length() + 1;
        }
    }
}
