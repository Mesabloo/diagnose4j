package com.github.mesabloo.diagnose4j;

import com.github.mesabloo.diagnose4j.prettyprint.Doc;
import com.github.mesabloo.diagnose4j.prettyprint.Document;
import com.github.tomaslanger.chalk.Chalk;

import java.io.PrintStream;
import java.util.*;

public class Diagnostic<Msg extends Pretty<Msg>> {
    private final List<Report<Msg>> reports;
    private final Map<String, List<String>> files;

    public Diagnostic() {
        this.reports = new ArrayList<>();
        this.files = new HashMap<>();
    }

    public Diagnostic<Msg> withReport(final Report<Msg> report) {
        this.reports.add(report);
        return this;
    }

    public Diagnostic<Msg> withFile(final String filepath, final String content) {
        this.files.put(filepath, Arrays.asList(content.split("\n")));
        return this;
    }

    public void clear() {
        this.files.clear();
        this.reports.clear();
    }

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
