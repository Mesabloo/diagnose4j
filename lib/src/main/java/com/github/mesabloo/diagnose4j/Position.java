package com.github.mesabloo.diagnose4j;

import java.util.Objects;

public final class Position implements Comparable<Position> {
    final long beginning_line;
    final long beginning_column;
    final long ending_line;
    final long ending_column;
    final String file;

    /**
     * Creates a new position from various position information.
     *
     * @param start_line   The line where the token starts
     * @param start_column The column where the token starts
     * @param end_line     The line where the token ends
     * @param end_column   The column where the tokens ends
     * @param file         The file in which the token occurs
     */
    public Position(final long start_line, final long start_column, final long end_line, final long end_column, final String file) {
        this.beginning_line = start_line;
        this.beginning_column = start_column;
        this.ending_line = end_line;
        this.ending_column = end_column;
        this.file = file;
    }

    /**
     * Get a default position where:
     * <ul>
     *     <li>the default file name is <code>&lt;no-file&gt;</code></li>
     *     <li>the default position is <code>1</code> for all coordinates</li>
     * </ul>
     *
     * @return A default position
     */
    public static Position def() {
        return new Position(1, 1, 1, 1, "<no-file>");
    }

    @Override
    public String toString() {
        return this.file + "@" + this.beginning_line + ":" + this.beginning_column + "-" + this.ending_line + ":" + this.ending_column;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return beginning_line == position.beginning_line && beginning_column == position.beginning_column && ending_line == position.ending_line && ending_column == position.ending_column && Objects.equals(file, position.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beginning_line, beginning_column, ending_line, ending_column, file);
    }

    @Override
    public int compareTo(Position o) {
        if (this.beginning_line < o.beginning_line)
            return -1;
        if (this.beginning_line == o.beginning_line && this.beginning_column < o.beginning_column)
            return -1;
        if (this.ending_line < o.ending_line)
            return -1;
        if (this.ending_line == o.ending_line && this.ending_column < o.ending_column)
            return -1;

        if (this.beginning_line == o.beginning_line && this.beginning_column == o.beginning_column
                && this.ending_line == o.ending_line && this.ending_column == o.ending_column)
            return 0;

        return 1;
    }
}