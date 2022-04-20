package com.github.mesabloo.diagnose4j.report;

import com.github.mesabloo.diagnose4j.Pretty;
import com.github.tomaslanger.chalk.Ansi;

import java.util.Objects;

public abstract class Marker<Msg extends Pretty<Msg>> {
    final Msg msg;

    private Marker(final Msg message) {
        this.msg = message;
    }

    public abstract Ansi.Color markerColor(final boolean isError);

    public Msg getMessage() {
        return this.msg;
    }

    public static final class This<Msg extends Pretty<Msg>> extends Marker<Msg> {
        public This(final Msg message) {
            super(message);
        }

        @Override
        public Ansi.Color markerColor(final boolean isError) {
            return isError ? Ansi.Color.RED : Ansi.Color.YELLOW;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof This && super.equals(obj);
        }
    }

    public static final class Where<Msg extends Pretty<Msg>> extends Marker<Msg> {
        public Where(Msg message) {
            super(message);
        }

        @Override
        public Ansi.Color markerColor(final boolean isError) {
            return Ansi.Color.BLUE;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Where && super.equals(obj);
        }
    }

    public static final class Maybe<Msg extends Pretty<Msg>> extends Marker<Msg> {
        public Maybe(Msg message) {
            super(message);
        }

        @Override
        public Ansi.Color markerColor(final boolean isError) {
            return Ansi.Color.MAGENTA;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Maybe && super.equals(obj);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Marker<?> marker = (Marker<?>) o;
        return Objects.equals(msg, marker.msg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg);
    }
}
