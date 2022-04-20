package com.github.mesabloo.diagnose4j;

import com.github.mesabloo.diagnose4j.prettyprint.Doc;
import com.github.mesabloo.diagnose4j.prettyprint.Document;
import com.github.tomaslanger.chalk.Chalk;

import java.io.PrintStream;
import java.util.*;

public class Diagnostic<Msg extends Pretty<Msg>> {
    private final List<Report<Msg>> reports;
    private final Map<String, List<String>> files;

    /**
     * Creates an empty diagnostic with no reports and no files attached to it.
     */
    public Diagnostic() {
        this.reports = new ArrayList<>();
        this.files = new HashMap<>();
    }

    /**
     * Insert a new error/warning report into the diagnostic.
     *
     * @param report The report to insert.
     * @return The current diagnostic, modified to include the newly added report.
     */
    public Diagnostic<Msg> withReport(final Report<Msg> report) {
        this.reports.add(report);
        return this;
    }

    /**
     * Specifies that a new file can be used inside reports.
     *
     * @param filepath The path of the file to add.
     *                 This is a kind of identifier, which will be used to refer to it in the reports.
     * @param content  The content of the file as a single string, where lines are separated by <code>"\n"</code>.
     * @return The current diagnostic, modified to include the new file.
     */
    public Diagnostic<Msg> withFile(final String filepath, final String content) {
        this.files.put(filepath, Arrays.asList(content.split("\n")));
        return this;
    }

    public void clear() {
        this.files.clear();
        this.reports.clear();
    }

    /**
     * Print the diagnostic onto the given stream.
     *
     * If output is meant to be collected as a String, you may use a {@link java.io.ByteArrayOutputStream} like this:
     * <pre><code>
     * final ByteArrayOutputStream os = new ByteArrayOutputStream();
     * final String utf8 = StandardCharsets.UTF_8.name();
     * try (final PrintStream ps = new PrintStream(os, true, utf8)) {
     *     diagnostic.print(os, withUnicode, false);
     * }
     * final String output = os.toString(utf8);
     * </code></pre>
     * In such case, color output must be <i>disabled</i> in order not to include junk bytes!
     *
     * @param handle      The handle of a {@link PrintStream} onto which to print the diagnostic.
     * @param withUnicode Specifies whether Unicode characters are wanted.
     * @param withColors  Must the output be colored?
     *                    (Disable if printing to a stream other than {@link System#out} and {@link System#err})
     */
    public void print(PrintStream handle, final boolean withUnicode, final boolean withColors) {
        final Document doc = new Document();
        for (final Report<Msg> report : reports) {
            doc.appendDoc(report.pretty(this.files, withUnicode))
                    .append(Doc.line());
        }

        if (withColors) {
            Chalk.setColorEnabled(true);
        } else {
            doc.removeColors();
        }

        doc.print(handle);
    }
}
