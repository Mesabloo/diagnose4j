package com.github.mesabloo.diagnose4j.instances;

import com.github.mesabloo.diagnose4j.Pretty;
import com.github.mesabloo.diagnose4j.prettyprint.Doc;
import com.github.mesabloo.diagnose4j.prettyprint.Document;

public class StringPretty implements Pretty<StringPretty> {
    private final String internal;

    public StringPretty(final String wrapped) {
        this.internal = wrapped;
    }

    @Override
    public Document pretty() {
        return new Document().append(new Doc(this.internal));
    }
}
