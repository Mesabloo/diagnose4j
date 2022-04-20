package com.github.mesabloo.diagnose4j;

import com.github.mesabloo.diagnose4j.prettyprint.Doc;
import com.github.mesabloo.diagnose4j.prettyprint.Document;
import com.github.mesabloo.diagnose4j.report.Marker;
import com.github.tomaslanger.chalk.Ansi;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class Report<Msg extends Pretty<Msg>> {
    /**
     * Is the current report an error or warning report?
     */
    private final boolean isError;

    /**
     * The error message.
     */
    private final Msg msg;

    /**
     * A map associating position with specific markers.
     */
    private final Map<Position, Marker<Msg>> markers;

    /**
     * Additional hints to output at the end of the report.
     */
    private final List<Msg> hints;


    /**
     * Creates a new error report.
     *
     * @param isError Is the report for an error?
     * @param message The message which should be output at the beginning
     * @param markers A map of markers to highlight specific regions of the code
     * @param hints   Additional hints to put at the end of the report
     */
    public Report(final boolean isError, final Msg message, final Map<Position, Marker<Msg>> markers, final List<Msg> hints) {
        this.isError = isError;
        this.msg = message;
        this.markers = new HashMap<>();
        this.hints = new ArrayList<>(hints);

        this.markers.putAll(markers);
    }

    /**
     * Constructs a new report with no hints.
     *
     * @see Report#Report(boolean, Pretty, Map, List)
     */
    public Report(final boolean isError, final Msg message, final Map<Position, Marker<Msg>> markers) {
        this(isError, message, markers, new ArrayList<>());
    }

    public Document pretty(final Map<String, List<String>> files, final boolean withUnicode) {
        List<Map.Entry<Position, Marker<Msg>>> sortedMarkers = this.markers
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        // sort markers to have the first lines of the report at the beginning

        final Map<Long, List<Map.Entry<Position, Marker<Msg>>>> inlineMarkers = new HashMap<>();
        final List<Map.Entry<Position, Marker<Msg>>> multilineMarkers = new ArrayList<>();
        this.splitInlineMarkers(sortedMarkers, inlineMarkers, multilineMarkers);
        // split markers to separate inline and multiline markers
        final List<Map.Entry<Long, List<Map.Entry<Position, Marker<Msg>>>>> sortedMarkerPerLine = inlineMarkers.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        final Position reportFile = sortedMarkers.stream()
                .filter(e -> e.getValue() instanceof Marker.This)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseGet(Position::def);
        // retrieve the report file from the first `This` marker (ideally, there is only one in each report)

        final long maxLineNumberLength = Optional.ofNullable(sortedMarkers.isEmpty() ? null : sortedMarkers.get(sortedMarkers.size() - 1))
                .map(pos -> Long.toString(pos.getKey().ending_line))
                .map(s -> Math.max(3, s.length()))
                .orElse(3);
        // line numbers take at least 3 characters in width

        final LongStream allLineNumbersInReport = LongStream.concat(
                sortedMarkerPerLine.stream()
                        .mapToLong(Map.Entry::getKey),
                multilineMarkers.stream()
                        .flatMapToLong(entry -> LongStream.rangeClosed(entry.getKey().beginning_line, entry.getKey().ending_line)));
        // we need to record all line numbers in the report to show them all

        final Doc header = new Doc(isError ? "[error]" : "[warning]")
                .colors(isError ? Ansi.Color.RED : Ansi.Color.YELLOW, null, Ansi.Modifier.BOLD);

        /*
        A report is of the following form:
        (1)    [error|warning]: <message>
        (2)           +-> <file>
        (3)           :
        (4)    <line> | <code>
                      : <marker lines>
                      : <marker messages>
        (5)           :
                      : <hints>
        (6)    -------+
        */

        Document doc = new Document()
                // (1)
                .append(header)
                .append(Doc.colon())
                .append(Doc.space())
                .appendDoc(this.msg.pretty().aligned())
                .append(Doc.line())
                // (2)
                .append(Doc.space())
                .appendDoc(this.pad(maxLineNumberLength, ' ', Doc.empty(), Function.identity()))
                .append(Doc.space())
                .append(new Doc(withUnicode ? "╭─▶" : "+->").colors(Ansi.Color.BLACK, null, Ansi.Modifier.BOLD))
                .append(Doc.space())
                .append(new Doc(reportFile).colors(Ansi.Color.GREEN, null, Ansi.Modifier.BOLD))
                .append(Doc.line())
                // (3)
                .append(Doc.space())
                .appendDoc(this.pipePrefix(maxLineNumberLength, withUnicode))
                // (4)
                .appendDoc(this.prettyAllLines(files, withUnicode, isError, maxLineNumberLength, sortedMarkerPerLine, multilineMarkers, allLineNumbersInReport))
                .append(Doc.line());
                // (5)
        if (!hints.isEmpty() && !markers.isEmpty()) {
            doc
                    .append(Doc.space())
                    .appendDoc(this.dotPrefix(maxLineNumberLength, withUnicode))
                    .appendDoc(this.prettyAllHints(hints, maxLineNumberLength, withUnicode))
                    .append(Doc.line());
        }
        doc
                // (6)
                .appendDoc(this.pad(maxLineNumberLength + 2, withUnicode ? '─' : '-', Doc.empty(), d -> d.colors(Ansi.Color.BLACK, null, Ansi.Modifier.BOLD)))
                .append(new Doc(withUnicode ? "╯" : "+").colors(Ansi.Color.BLACK, null, Ansi.Modifier.BOLD))
                ;

        return doc;
    }

    /////////////////////////////////////
    //////////// INTERNAL ///////////////
    /////////////////////////////////////

    private Document prettyAllLines(
            final Map<String, List<String>> files,
            final boolean withUnicode,
            final boolean isError,
            final long maxLineNumberLength,
            final List<Map.Entry<Long, List<Map.Entry<Position, Marker<Msg>>>>> inlineMarkers,
            final List<Map.Entry<Position, Marker<Msg>>> multilineMarkers,
            final LongStream allLineNumbersInReport
    ) {
        Document doc = allLineNumbersInReport.mapToObj(line -> {
                    /*
                    A line of code is composed of:
                    (1)    <line> | <source code>
                    (2)           : <markers>
                    (3)           : <marker messages>

                    Multiline markers may also take an additional 2 characters-wide space after the bar
                    */

                    final List<Map.Entry<Position, Marker<Msg>>> allInlineMarkersInLine = inlineMarkers.stream()
                            .filter(e -> e.getKey() == line)
                            .flatMap(e -> e.getValue().stream())
                            .collect(Collectors.toList());
                    final List<Map.Entry<Position, Marker<Msg>>> allMultilineMarkersInLine = multilineMarkers.stream()
                            .filter(e -> e.getKey().beginning_line == line || e.getKey().ending_line == line)
                            .collect(Collectors.toList());
                    final List<Map.Entry<Position, Marker<Msg>>> allMultilineMarkersSpanningLine = multilineMarkers.stream()
                            .filter(e -> e.getKey().beginning_line < line && e.getKey().ending_line > line)
                            .collect(Collectors.toList());

                    final boolean inSpanOfMultiline = multilineMarkers.stream()
                            .anyMatch(e -> e.getKey().beginning_line <= line && e.getKey().ending_line >= line);

                    final Ansi.Color colorOfFirstMultilineMarker = Stream.concat(allMultilineMarkersInLine.stream(), allMultilineMarkersSpanningLine.stream())
                            .findFirst()
                            .map(e -> e.getValue().markerColor(isError))
                            .orElse(null);
                    // take the color of the first multiline marker to color the entire line

                    final Document additionalPrefix;
                    if (allMultilineMarkersInLine.isEmpty()) {
                        if (!multilineMarkers.isEmpty()) {
                            if (!allMultilineMarkersSpanningLine.isEmpty()) {
                                additionalPrefix = new Document()
                                        .append(new Doc(withUnicode ? "│  " : "|  ").colors(colorOfFirstMultilineMarker, null));
                            } else {
                                additionalPrefix = new Document()
                                        .append(new Doc("   "));
                            }
                        } else {
                            additionalPrefix = new Document();
                        }
                    } else {
                        final Map.Entry<Position, Marker<Msg>> entry = allMultilineMarkersInLine.get(0);
                        final Position pos = entry.getKey();
                        final Marker<Msg> mark = entry.getValue();

                        final boolean hasPredecessor = pos.ending_line == line || multilineMarkers.stream()
                                .findFirst()
                                .map(e -> !e.getKey().equals(pos))
                                .orElse(false);

                        final String marker;
                        if (hasPredecessor && withUnicode) {
                            marker = "├";
                        } else if (hasPredecessor) {
                            marker = "|";
                        } else if (withUnicode) {
                            marker = "╭";
                        } else {
                            marker = "+";
                        }

                        additionalPrefix = new Document()
                                .append(new Doc(marker).colors(colorOfFirstMultilineMarker, null))
                                .append(new Doc(withUnicode ? "┤" : ">").colors(mark.markerColor(isError), null))
                                .append(Doc.space());
                    }

                    final List<Map.Entry<Position, Marker<Msg>>> allMarkersInLine = Stream.concat(
                            Stream.concat(
                                allInlineMarkersInLine.stream(),
                                allMultilineMarkersInLine.stream()
                            ),
                            allMultilineMarkersSpanningLine.stream()
                    ).collect(Collectors.toList());

                    return new Document()
                            .append(Doc.line())
                            // (1)
                            .appendDoc(this.linePrefix(maxLineNumberLength, line, withUnicode))
                            .append(Doc.space())
                            .appendDoc(additionalPrefix)
                            .appendDoc(this.getLine(files, allMarkersInLine, line, isError))
                            // (2)
                            .appendDoc(this.showAllMarkersInLine(!multilineMarkers.isEmpty(), inSpanOfMultiline, colorOfFirstMultilineMarker, withUnicode, isError, maxLineNumberLength, allInlineMarkersInLine));
                })
                .reduce(new Document(), Document::appendDoc);

        if (!multilineMarkers.isEmpty()) {

            final Ansi.Color colorOfLastMultilineMarker = multilineMarkers.get(multilineMarkers.size() - 1).getValue().markerColor(isError);

            final Document prefix = new Document()
                    .append(Doc.line())
                    .append(Doc.space())
                    .appendDoc(this.dotPrefix(maxLineNumberLength, withUnicode))
                    .append(Doc.space());
            final Function<Ansi.Color, Document> prefixWithBar = color -> new Document()
                    .appendDoc(prefix)
                    .append(new Doc(withUnicode ? "│ " : "| ").colors(color, null));

            final BiFunction<Map.Entry<Position, Marker<Msg>>, Boolean, Document> showMultilineMarkerMessage = (entry, isLast) -> new Document()
                    .append(new Doc(isLast ? (withUnicode ? "╰╸ " : "`- ") : (withUnicode ? "├╸ " : "|- ")).colors(entry.getValue().markerColor(isError), null))
                    .appendDoc(entry.getValue().getMessage().pretty().aligned());

            doc = doc
                    .appendDoc(prefixWithBar.apply(colorOfLastMultilineMarker))
                    .appendDoc(prefix);

            for (int i = multilineMarkers.size() - 1; i >= 1; --i) {
                doc = doc
                        .appendDoc(showMultilineMarkerMessage.apply(multilineMarkers.get(i), false))
                        .appendDoc(prefix);
            }
            if (!multilineMarkers.isEmpty()) {
                // this will only happen if the loop is executed at least once
                doc = doc.appendDoc(showMultilineMarkerMessage.apply(multilineMarkers.get(0), true));
            }
        }

        return doc;
    }

    private Document getLine(
            final Map<String, List<String>> files,
            final List<Map.Entry<Position, Marker<Msg>>> allMarkersInLine,
            final long line,
            final boolean isError
    ) {
        final Optional<String> lineOfCode = Optional.ofNullable(allMarkersInLine.isEmpty() ? null : allMarkersInLine.get(0))
                .flatMap(entry -> Optional.ofNullable(files.get(entry.getKey().file)))
                .flatMap(lines -> Optional.ofNullable(lines.size() >= line - 1 ? lines.get((int) line - 1) : null));

        if (lineOfCode.isPresent()) {
            final String code = lineOfCode.get();

            Document doc = new Document();
            for (int i = 1; i <= code.length(); ++i) {
                final int n = i;

                final Optional<Ansi.Color> color = allMarkersInLine.stream()
                        .filter(entry -> {
                            final Position pos = entry.getKey();
                            if (pos.beginning_line == pos.ending_line) {
                                return n >= pos.beginning_column && n < pos.ending_column;
                            } else {
                                return (pos.beginning_line == line && n >= pos.beginning_column)
                                        || (pos.ending_line == line && n < pos.ending_column);
                            }
                        })
                        .findFirst()
                        .map(Map.Entry::getValue)
                        .map(m -> m.markerColor(isError));

                doc = doc.append(new Doc(code.charAt(n - 1)).colors(color.orElse(null), null, color.isPresent() ? Ansi.Modifier.BOLD : null));
            }

            return doc;
        } else {
            return new Document()
                    .append(new Doc("<no line>").colors(Ansi.Color.MAGENTA, null));
        }
    }

    private Document showAllMarkersInLine(
            final boolean hasMultilines,
            final boolean inSpanOfMultiline,
            final Ansi.Color colorOfFirstMultilineMarker,
            final boolean withUnicode,
            final boolean isError,
            final long maxLineNumberLength,
            final List<Map.Entry<Position, Marker<Msg>>> allInlineMarkersInLine
    ) {
        Document doc = new Document();

        if (!allInlineMarkersInLine.isEmpty()) {
            final List<Map.Entry<Position, Marker<Msg>>> markers = new ArrayList<>(allInlineMarkersInLine);
            markers.sort(Comparator.comparingLong(e -> e.getKey().ending_column));

            final long maxMarkerColumn = markers.get(markers.size() - 1).getKey().ending_column;
            // get the maximum end column, so that we know when to stop looking for other markers on the same line

            final Document specialPrefix =
                    inSpanOfMultiline
                            ? new Document().append(new Doc(withUnicode ? "│ " : "| ").colors(colorOfFirstMultilineMarker, null))
                                .append(Doc.space())
                            : hasMultilines
                            ? new Document().append(new Doc("  ").colors(colorOfFirstMultilineMarker, null))
                                .append(Doc.space())
                            : new Document();

            doc = doc.append(Doc.line())
                    .append(Doc.space())
                    .appendDoc(this.dotPrefix(maxLineNumberLength, withUnicode))
                    .append(Doc.space())
                    .appendDoc(specialPrefix);

            for (long n = 1; n <= maxMarkerColumn; ++n) {
                final long n_ = n;

                final List<Map.Entry<Position, Marker<Msg>>> allMarkers = allInlineMarkersInLine.stream()
                        .filter(entry -> n_ >= entry.getKey().beginning_column && n_ < entry.getKey().ending_column)
                        .collect(Collectors.toList());
                // only consider markers which span onto the current column

                if (allMarkers.isEmpty()) {
                    doc = doc.append(Doc.space());
                } else {
                    final Map.Entry<Position, Marker<Msg>> entry = allMarkers.get(0);
                    final Position pos = entry.getKey();
                    final Marker<Msg> marker = entry.getValue();

                    if (pos.beginning_column == n) {
                        doc = doc.append(new Doc(withUnicode ? "┬" : "^").colors(marker.markerColor(isError), null));
                    } else {
                        doc = doc.append(new Doc(withUnicode ? "─" : "-").colors(marker.markerColor(isError), null));
                    }
                    // if the marker just started on this column, output a caret, else output a dash
                }
            }

            final LinkedList<Map.Entry<Position, Marker<Msg>>> ms = new LinkedList<>(allInlineMarkersInLine);
            while (!ms.isEmpty()) {
                final Map.Entry<Position, Marker<Msg>> entry = ms.removeFirst();

                final Position pos = entry.getKey();
                final Marker<Msg> marker = entry.getValue();

                final List<Map.Entry<Position, Marker<Msg>>> filteredPipes = ms.stream()
                        .filter(e -> e.getKey().beginning_line != pos.beginning_line || e.getKey().beginning_column != pos.beginning_column)
                        .collect(Collectors.toList());
                        // only keep markers starting on different positions
                final List<Map.Entry<Position, Marker<Msg>>> filteredAndNubbedPipes = filteredPipes.stream()
                        .filter(new Predicate<Map.Entry<Position, Marker<Msg>>>() {
                            Map.Entry<Position, Marker<Msg>> previous;

                            @Override
                            public boolean test(Map.Entry<Position, Marker<Msg>> entry1) {
                                if (previous != null && previous.getKey().beginning_line == entry1.getKey().beginning_line
                                        && previous.getKey().beginning_column == entry1.getKey().beginning_column)
                                    return false;

                                previous = entry1;
                                return true;
                                // this is dirty, but I have no other choice
                                // (other than creating a wrapping class just to change the comparator)
                                //
                                // see https://stackoverflow.com/a/23733628/6718698 for the original idea
                            }
                        })
                        // and remove all duplicates
                        .collect(Collectors.toList());

                final boolean hasSuccessor = filteredPipes.size() != ms.size();

                final BiFunction<Long, List<Map.Entry<Position, Doc>>, Map.Entry<Long, List<Doc>>> allColumns = (n, marks) -> {
                    final List<Map.Entry<Position, Doc>> marks_ = new ArrayList<>(marks);
                    final List<Doc> docs = new ArrayList<>();
                    long ret = 1;

                    for (int i = 0; i < marks_.size(); ) {
                        final Map.Entry<Position, Doc> entry1 = marks_.get(i);

                        final Position pos1 = entry1.getKey();
                        final Doc doc1 = entry1.getValue();

                        if (ret == pos1.beginning_column) {
                            docs.add(doc1);
                            i++;
                        } else if (ret < pos1.beginning_column) {
                            docs.add(Doc.space());
                        } else {
                            docs.add(Doc.space());
                            i++;
                        }
                        ret++;
                    }

                    return new AbstractMap.SimpleEntry<>(ret, docs);
                };

                final Function<List<Map.Entry<Position, Doc>>, Document> lineStart = pipes2 -> {
                    pipes2.sort(Comparator.comparingLong(e -> e.getKey().beginning_column));
                    final Map.Entry<Long, List<Doc>> res = allColumns.apply(1L, pipes2);

                    Document doc2 = new Document()
                            .appendDoc(this.dotPrefix(maxLineNumberLength, withUnicode))
                            .append(Doc.space())
                            .appendDoc(specialPrefix);

                    for (Doc d : res.getValue()) {
                        doc2 = doc2.append(d);
                    }
                    doc2 = doc2.appendDoc(this.pad(pos.beginning_column - res.getKey(), ' ', Doc.empty(), Function.identity()));

                    return doc2;
                };

                final List<Map.Entry<Position, Marker<Msg>>> pipesBefore = new ArrayList<>();
                final List<Map.Entry<Position, Marker<Msg>>> pipesAfter = new ArrayList<>();
                // split the list so that pipes before this one have a `|` but pipes after don't
                for (Map.Entry<Position, Marker<Msg>> pipe : filteredAndNubbedPipes) {
                    final Position p = pipe.getKey();

                    if (p.beginning_column < pos.beginning_column)
                        pipesBefore.add(pipe);
                    else
                        pipesAfter.add(pipe);
                }

                final List<Map.Entry<Position, Doc>> pipesBeforePreRender = new ArrayList<>(pipesBefore.stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new Doc(withUnicode ? "│" : "|").colors(e.getValue().markerColor(isError), null)))
                        .entrySet());
                // pre-render pipes which are before because they will be shown

                final long lastBeginPosition = pipesAfter.isEmpty() ? 0 : pipesAfter.get(0).getKey().beginning_column - pos.beginning_column;

                final String currentPipe = withUnicode && hasSuccessor ? "├" : withUnicode ? "╰" : hasSuccessor ? "|" : "`";
                final char lineChar = withUnicode ? '─' : '-';
                final char pointChar = withUnicode ? '╸' : '-';

                StringBuilder arrowLine = new StringBuilder();
                for (int i = 0; i < lastBeginPosition; ++i) {
                    arrowLine.append(lineChar);
                }

                final Document prefix = new Document()
                        .appendDoc(lineStart.apply(pipesBeforePreRender))
                        .append(new Doc(currentPipe).colors(marker.markerColor(isError), null))
                        .append(new Doc(arrowLine).colors(marker.markerColor(isError), null))
                        .append(new Doc(pointChar).colors(marker.markerColor(isError), null))
                        .append(Doc.space())
                        .appendDoc(marker.getMessage().pretty().colors(marker.markerColor(isError), null).aligned());

                doc = doc.append(Doc.line())
                        .append(Doc.space())
                        .appendDoc(prefix);
            }
        }

        return doc;
    }

    private Document prettyAllHints(List<Msg> hints, long maxLineNumberLength, boolean withUnicode) {
        Document doc = new Document();

        if (!hints.isEmpty()) {
            final Document prefix = new Document()
                    .append(Doc.line())
                    .append(Doc.space())
                    .appendDoc(this.pipePrefix(maxLineNumberLength, withUnicode));

            for (Msg hint : hints) {
                doc = doc.appendDoc(prefix)
                        .append(Doc.space())
                        .append(new Doc("Hint:").colors(Ansi.Color.CYAN, null, Ansi.Modifier.BOLD))
                        .append(Doc.space())
                        .appendDoc(hint.pretty().colors(Ansi.Color.CYAN, null).aligned());
            }
        }

        return doc;
    }

    /**
     * Split a list of markers into two lists where:
     * <ul>
     *     <li>The first list contains all markers which occur on only one line</li>
     *     <li>The second list contains markers spanning across multiple lines</li>
     * </ul>
     *
     * @param markers          The list of markers to split
     * @param inlineMarkers    The list where inline markers are put
     * @param multilineMarkers The list where multiline markers are put
     */
    private void splitInlineMarkers(
            final List<Map.Entry<Position, Marker<Msg>>> markers,
            Map<Long, List<Map.Entry<Position, Marker<Msg>>>> inlineMarkers,
            List<Map.Entry<Position, Marker<Msg>>> multilineMarkers
    ) {
        for (final Map.Entry<Position, Marker<Msg>> entry : markers) {
            final Position pos = entry.getKey();

            if (pos.beginning_line != pos.ending_line)
                multilineMarkers.add(entry);
            else {
                List<Map.Entry<Position, Marker<Msg>>> list;
                if (inlineMarkers.containsKey(pos.beginning_line)) {
                    list = inlineMarkers.get(pos.beginning_line);
                } else {
                    list = new ArrayList<>();
                }
                list.add(0, entry);
                inlineMarkers.put(pos.beginning_line, list);
            }
        }
    }

    /**
     * Insert some characters after a {@link Doc} until it reaches the given width.
     *
     * @param max     The width to reach
     * @param padding The padding character to use
     * @param doc     The document to pad
     * @return        A new {@link Document} containing the given {@link Doc} plus some padding
     */
    private Document pad(final long max, final char padding, final Doc doc, final Function<Doc, Doc> paddingColors) {
        final long width = doc.width();
        final StringBuilder paddingString = new StringBuilder();
        for (int i = 0; i < max - width; ++i) {
            paddingString.append(padding);
        }

        return new Document()
                .append(doc)
                .append(paddingColors.apply(new Doc(paddingString.toString())));
    }

    /**
     * Creates a "pipe"-prefix for a report line where there is no code.
     *
     * This follows this format:
     * <ul>
     *     <li>With Unicode: <code>"␣␣␣␣␣│␣"</code></li>
     *     <li>With ASCII: <code>"␣␣␣␣␣|␣"</code></li>
     * </ul>
     *
     * @param max         The number of spaces to insert before the pipe
     * @param withUnicode Should we output with Unicode characters?
     * @return A {@link Document} containing the whole prefix with colors
     */
    private Document pipePrefix(final long max, final boolean withUnicode) {
        return new Document()
                .appendDoc(this.pad(max, ' ', Doc.empty(), Function.identity()))
                .append(Doc.space())
                .append(new Doc(withUnicode ? "│" : "|").colors(Ansi.Color.BLACK, null, Ansi.Modifier.BOLD));
    }

    /**
     * Creates a "dot"-prefix for a report line where there is no code.
     *
     * This follows this format:
     * <ul>
     *     <li>With Unicode: <code>"␣␣␣␣␣•␣"</code></li>
     *     <li>With ASCII: <code>"␣␣␣␣␣:␣"</code></li>
     * </ul>
     *
     * @param max         The number of spaces to insert before the pipe
     * @param withUnicode Should we output with Unicode characters?
     * @return A {@link Document} containing the whole prefix with colors
     */
    private Document dotPrefix(final long max, final boolean withUnicode) {
        return new Document()
                .appendDoc(this.pad(max, ' ', Doc.empty(), Function.identity()))
                .append(Doc.space())
                .append(new Doc(withUnicode ? "•" : ":").colors(Ansi.Color.BLACK, null, Ansi.Modifier.BOLD));
    }

    /**
     * Creates a "line"-prefix for a report line containing source code.
     *
     * This follows this format:
     * <ul>
     *     <li>With Unicode: <code>"␣␣␣3␣│␣"</code></li>
     *     <li>With ASCII: <code>"␣␣␣3␣|␣"</code></li>
     * </ul>
     *
     * Visuals may be different depending on the line number length.
     *
     * @param maxLineNumberLength The number of spaces to insert before the line number
     * @param line                The line number
     * @param withUnicode         Should the output be with Unicode characters?
     * @return A {@link Document} containing the whole prefix with colors
     */
    private Document linePrefix(final long maxLineNumberLength, final long line, final boolean withUnicode) {
        final int lineNoLength = Long.toString(line).length();
        return new Document()
                .appendDoc(this.pad(maxLineNumberLength - lineNoLength, ' ', Doc.empty(), d -> d.colors(Ansi.Color.BLACK, null)))
                .append(Doc.space().colors(Ansi.Color.BLACK, null))
                .append(new Doc(line).colors(Ansi.Color.BLACK, null))
                .append(Doc.space().colors(Ansi.Color.BLACK, null))
                .append(new Doc(withUnicode ? "│" : "|").colors(Ansi.Color.BLACK, null));
    }
}
