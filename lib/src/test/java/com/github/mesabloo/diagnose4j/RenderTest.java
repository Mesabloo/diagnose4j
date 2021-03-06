package com.github.mesabloo.diagnose4j;

import com.github.mesabloo.diagnose4j.instances.StringPretty;
import com.github.mesabloo.diagnose4j.report.Marker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class RenderTest {
    private static final Map<String, String> files = new HashMap<>();
    static {
        files.put("test.zc", "let id<a>(x : a) : a := x + 1\nrec fix(f) := f(fix(f))\nlet const<a, b>(x : a, y : b) : a := x");
        files.put("somefile.zc", "let id<a>(x : a) : a := x + 1\nrec fix(f) := f(fix(f))\nlet const<a, b>(x : a, y : b) : a := x");
        files.put("err.nst", "\n\n\n\n    = jmp g\n\n    g: forall(s: Ts, e: Tc).{ %r0: *s64 | s -> e }");
        files.put("unsized.nst", "main: forall(a: Ta, s: Ts, e: Tc).{ %r5: forall().{| s -> e } | s -> %r5 }\n    = salloc a\n    ; sfree\\n");
    }

    private Diagnostic<StringPretty> diag;

    @Before
    public void setUp() {
        diag = new Diagnostic<>();
        for (final Map.Entry<String, String> file : files.entrySet()) {
            diag = diag.withFile(file.getKey(), file.getValue());
        }

        System.out.println("-------------------------------------------------------");
    }

    @After
    public void tearDown() {
        System.out.println("--- With Unicode ---");
        diag.print(System.out, true, true);
        System.out.println("--- With ASCII ---");
        diag.print(System.out, false, true);
        System.out.println("--- Without colors ---");
        diag.print(System.out, true, false);

        diag.clear();
    }

    @Test
    public void noHintsAndNoMarkers() {
        diag = diag
                .withReport(new Report<>(true, new StringPretty("Error with no marker and no hints"), new LinkedHashMap<>()));
    }

    @Test
    public void singleMarkerNoHints() {
        diag = diag
                .withReport(new Report<>(true, new StringPretty("Error with one marker and no hints"), new LinkedHashMap<Position, Marker<StringPretty>>() {{
                    this.put(new Position(1, 25, 1, 30, "test.zc"), new Marker.This<>(new StringPretty("Required here")));
                }}));
    }

    @Test
    public void simpleDiagnostic() {
        diag = diag
                .withReport(new Report<>(true, new StringPretty("Could not deduce constraint 'Num(a)' from the current context"),
                        new LinkedHashMap<Position, Marker<StringPretty>>() {{
                            this.put(
                                    new Position(1, 25, 1, 30, "test.zc"),
                                    new Marker.This<>(new StringPretty("While applying function '+'"))
                            );
                            this.put(
                                    new Position(1, 11, 1, 16, "test.zc"),
                                    new Marker.Where<>(new StringPretty("'x' is supposed to have type 'a'"))
                            );
                            this.put(
                                    new Position(1, 8, 1, 9, "test.zc"),
                                    new Marker.Where<>(new StringPretty("type 'a' is bound here without constraints"))
                            );
                }}, new ArrayList<StringPretty>() {{
                    this.add(new StringPretty("Adding 'Num(a)' to the list of constraints may solve this problem."));
                }}));
    }

    @Test
    public void multilineMessages() {
        diag = diag
                .withReport(new Report<>(true, new StringPretty("Could not deduce constraint 'Num(a)'\nfrom the current context"),
                        new LinkedHashMap<Position, Marker<StringPretty>>() {{
                            this.put(
                                    new Position(1, 25, 1, 30, "test.zc"),
                                    new Marker.This<>(new StringPretty("While applying function '+'"))
                            );
                            this.put(
                                    new Position(1, 11, 1, 16, "test.zc"),
                                    new Marker.Where<>(new StringPretty("'x' is supposed to have type 'a'"))
                            );
                            this.put(
                                    new Position(1, 8, 1, 9, "test.zc"),
                                    new Marker.Where<>(new StringPretty("type 'a' is bound here without constraints"))
                            );
                        }}, new ArrayList<StringPretty>() {{
                            this.add(new StringPretty("Adding 'Num(a)' to the list of\nconstraints may solve this problem."));
                        }}
                ));
    }

    @Test
    public void multipleFiles() {
        diag = diag
                .withReport(new Report<>(true, new StringPretty("Error on multiple files"),
                        new LinkedHashMap<Position, Marker<StringPretty>>() {{
                            this.put(
                                    new Position(1, 5, 1, 7, "test.zc"),
                                    new Marker.Where<>(new StringPretty("Function already defined here"))
                            );
                            this.put(
                                    new Position(1, 5, 1, 7, "somefile.zc"),
                                    new Marker.This<>(new StringPretty("Function `id` already declared in another module"))
                            );
                        }}));
    }

    @Test
    public void noMarkerButSomeHints() {
        diag = diag
                .withReport(new Report<>(false, new StringPretty("Error with no markers but some hints"), new LinkedHashMap<>(),
                        new ArrayList<StringPretty>() {{
                            this.add(new StringPretty("My first hint on resolving this issue"));
                            this.add(new StringPretty("And a second one because I'm feeling nice today :)"));
                        }}));
    }

    @Test
    public void testCrossing() {
        diag = diag
                .withReport(new Report<>(false, new StringPretty("Ordered labels with crossing"),
                        new LinkedHashMap<Position, Marker<StringPretty>>() {{
                            this.put(
                                    new Position(1, 1, 1, 7, "somefile.zc"),
                                    new Marker.This<>(new StringPretty("leftmost label"))
                            );
                            this.put(
                                    new Position(1, 9, 1, 16, "somefile.zc"),
                                    new Marker.Where<>(new StringPretty("rightmost label"))
                            );
                        }}));
    }
}
